/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.security.authorizers;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.Request;
import static org.restheart.mongodb.ConnectionChecker.connected;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;
import static org.restheart.security.BaseAclPermission.MATCHING_ACL_PERMISSION;
import static org.restheart.security.MongoPermissions.ALLOW_ALL_MONGO_PERMISSIONS;
import org.restheart.security.utils.MongoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import static com.google.common.collect.Sets.newHashSet;
import com.mongodb.client.MongoClient;
import static com.mongodb.client.model.Filters.eq;

import static io.undertow.predicate.Predicate.PREDICATE_CONTEXT;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "mongoAclAuthorizer", description = "authorizes requests against acl stored in mongodb")
public class MongoAclAuthorizer implements Authorizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoAclAuthorizer.class);

    public static final String X_FORWARDED_ACCOUNT_ID = "rhAuthenticator";
    public static final String X_FORWARDED_ROLE = "RESTHeart";

    public static final String ACL_COLLECTION_NAME = "acl";

    public static final String UNAUTHENTICATED = "$unauthenticated";

    String aclDb;
    String aclCollection;
    String overrideAclDbHeader;
    private String rootRole = null;
    private boolean cacheEnabled = false;
    private Integer cacheSize = 1_000; // 1000 entries
    private Integer cacheTTL = 60 * 1_000; // 1 minute
    private Cache.EXPIRE_POLICY cacheExpirePolicy = Cache.EXPIRE_POLICY.AFTER_WRITE;
    private record CacheKey(String role, String db) {};
    private LoadingCache<CacheKey, LinkedHashSet<MongoAclPermission>> acl = null;

    @Inject("mclient")
    private MongoClient mclient;

    @Inject("registry")
    private PluginsRegistry registry;

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() {
        this.aclDb = argOrDefault(config, "acl-db", "restheart");
        this.aclCollection = argOrDefault(config, "acl-collection", "acl");
        this.overrideAclDbHeader = argOrDefault(config, "override-acl-db-header", null);
        this.rootRole = argOrDefault(config, "root-role", null);

        if (config != null && config.containsKey("cache-enabled")) {
            this.cacheEnabled = arg(config, "cache-enabled");

            if (this.cacheEnabled) {
                this.cacheSize = arg(config, "cache-size");
                this.cacheTTL = arg(config, "cache-ttl");

                String _cacheExpirePolicy = arg(config, "cache-expire-policy");

                if (_cacheExpirePolicy != null) {
                    try {
                        this.cacheExpirePolicy = Cache.EXPIRE_POLICY.valueOf((String) _cacheExpirePolicy);
                    } catch (IllegalArgumentException iae) {
                        throw new ConfigurationException("wrong configuration file format. cache-expire-policy valid values are " + Arrays.toString(Cache.EXPIRE_POLICY.values()));
                    }
                }

                this.acl = CacheFactory.createLocalLoadingCache(
                    this.cacheSize,
                    this.cacheExpirePolicy,
                    this.cacheTTL, role -> this.findRolePermissions(role));
            }
        }

        try {
            if (!checkAclCollection()) {
                LOGGER.error("ACL collection does not exist and could not be created");
            }
        } catch(IllegalStateException ise) {
            LOGGER.error(ise.getMessage());
        }
    }

    /**
     * @param request
     * @return
     */
    @Override
    public boolean isAllowed(Request<?> request) {
        // always allow OPTIONS requests
        if (request.isOptions()) {
            return true;
        }

        var exchange = request.getExchange();

        if (this.rootRole != null
                && exchange.getSecurityContext() != null
                && exchange.getSecurityContext().getAuthenticatedAccount() != null
                && exchange.getSecurityContext().getAuthenticatedAccount().getRoles().contains(this.rootRole)) {
            LOGGER.debug("allow request for root user {}", exchange.getSecurityContext().getAuthenticatedAccount().getPrincipal().getName());

            // for root role add a mongo permissions that allows everything
            Set<String> roles = Sets.newHashSet();
            roles.add(this.rootRole);
            exchange.putAttachment(MATCHING_ACL_PERMISSION, new MongoAclPermission(new BsonObjectId(), "path-prefix('/')", roles, Integer.MAX_VALUE, new BsonDocument("mongo", ALLOW_ALL_MONGO_PERMISSIONS.asBson())));
            return true;
        }

        // this fixes undertow bug 377
        // https://issues.jboss.org/browse/UNDERTOW-377
        if (exchange.getAttachment(PREDICATE_CONTEXT) == null) {
            exchange.putAttachment(PREDICATE_CONTEXT, new TreeMap<>());
        }

        // Predicate.resolve() uses getRelativePath() that is the path relative to
        // the last PathHandler we want to check against the full request path
        // see https://issues.jboss.org/browse/UNDERTOW-1317
        exchange.setRelativePath(exchange.getRequestPath());

        final ArrayList<MongoAclPermission> permissions = new ArrayList<>();

        // debug roles and predicates evaluation order
        if (LOGGER.isDebugEnabled()) {
            roles(exchange).forEachOrdered(role -> {
                ArrayList<MongoAclPermission> matched = Lists.newArrayListWithCapacity(1);
                final var key = new CacheKey(role, aclDb(request));

                rolePermissions(key)
                    .stream().anyMatch(permission -> {
                        var resolved = permission.allow(request);

                        String marker;

                        // to highlight the effective permission
                        if (resolved && matched.isEmpty()) {
                            matched.add(permission);
                            marker = "<--";
                        } else {
                            marker = "";
                        }

                        LOGGER.debug("role {}, permission id {}, resolve {} {}",
                            role,
                            permission.getId(),
                            resolved,
                            marker);

                        return false;
                    });
            });
        }

        // the applicable permission is the ones that
        // resolves the exchange
        roles(exchange)
            .map(role -> new CacheKey(role, aclDb(request)))
            .forEachOrdered(key -> rolePermissions(key)
            .stream()
            .anyMatch(r -> {
                if (r.allow(request)) {
                    permissions.add(r);
                    return true;
                } else {
                    return false;
                }
            }));

        if (permissions.isEmpty()) {
            return false;
        } else {
            exchange.putAttachment(MATCHING_ACL_PERMISSION, permissions.get(0));
            return true;
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isAuthenticationRequired(Request request) {
        // don't require authentication for OPTIONS requests
        if (request.isOptions()) {
            return false;
        }

        var exchange = request.getExchange();

        var ps = rolePermissions(new CacheKey(UNAUTHENTICATED, aclDb(request)));

        if (ps != null) {
            // this fixes undertow bug 377
            // https://issues.jboss.org/browse/UNDERTOW-377
            if (exchange.getAttachment(PREDICATE_CONTEXT) == null) {
                exchange.putAttachment(PREDICATE_CONTEXT, new TreeMap<>());
            }

            // Predicate.resolve() uses getRelativePath() that is the path relative to
            // the last PathHandler we want to check against the full request path
            // see https://issues.jboss.org/browse/UNDERTOW-1317
            exchange.setRelativePath(request.getPath());

            return !ps.stream().anyMatch(r -> r.allow(request));
        } else {
            return true;
        }
    }

    private String aclDb(Request<?> req) {
        if (this.overrideAclDbHeader != null && req.getHeaders().contains(this.overrideAclDbHeader)) {
            return req.getHeader(overrideAclDbHeader);
        } else {
            return this.aclDb;
        }
    }

    private Stream<String> roles(HttpServerExchange exchange) {
        return account(exchange).getRoles().stream();
    }

    private Account account(HttpServerExchange exchange) {
        final Account account = exchange.getSecurityContext().getAuthenticatedAccount();
        return isAuthenticated(account) ? account : new NotAuthenticatedAccount();
    }

    private boolean isAuthenticated(Account authenticatedAccount) {
        return authenticatedAccount != null;
    }

    /**
     * @param key the CacheKey(id,db)
     * @return the acl
     */
    public LinkedHashSet<MongoAclPermission> rolePermissions(CacheKey key) {
        if (this.cacheEnabled) {
            // TOFIX pinned thread
            var _rolePermissions = this.acl.getLoading(key);

            if (_rolePermissions != null && _rolePermissions.isPresent()) {
                return _rolePermissions.get();
            } else {
                return null;
            }
        } else {
            return findRolePermissions(key);
        }
    }

    private static class NotAuthenticatedAccount implements Account {

        /**
         *
         */
        private static final long serialVersionUID = -5208703681313492952L;

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public Set<String> getRoles() {
            return newHashSet("$unauthenticated");
        }
    }

    private static final BsonDocument PROJECTION = BsonDocument.parse("{\"_id\":1,\"roles\":1,\"predicate\":1,\"writeFilter\":1,\"readFilter\":1,\"priority\":1,\"mongo\":1}");
    private static final BsonDocument SORT = BsonDocument.parse("{\"priority\":-1,\"_id\":-1}");

    private LinkedHashSet<MongoAclPermission> findRolePermissions(final CacheKey key) {
        if (this.mclient == null) {
            LOGGER.error("Cannot find acl: mongo service is not enabled.");
            return null;
        } else {
            var permissions = new LinkedHashSet<BsonDocument>();
            this.mclient.getDatabase(key.db)
                .getCollection(this.aclCollection)
                .withDocumentClass(BsonDocument.class)
                .find(eq("roles", key.role))
                .projection(PROJECTION)
                .sort(SORT)
                .into(permissions);

            var ret = new LinkedHashSet<MongoAclPermission>();

            StreamSupport.stream(permissions.spliterator(), true)
                .filter(permissionElem -> permissionElem.isDocument())
                .map(permissionElem -> permissionElem.asDocument())
                .filter(permissionDocument -> {
                    // filter out illegal permissions
                    try {
                        MongoAclPermission.build(permissionDocument);
                        return true;
                    } catch (IllegalArgumentException iae) {
                        LOGGER.warn("invalid permission _id={}: {}", permissionDocument.get("_id"), iae);
                        return false;
                    }
                })
                .map(permissionDocument -> MongoAclPermission.build(permissionDocument))
                .forEachOrdered(p ->  {
                    this.registry.getPermissionTransformers().stream().forEach(pt -> pt.transform(p));
                    ret.add(p);
                });

            // apply the permission transformers
            ret.forEach(p -> this.registry.getPermissionTransformers().stream().forEach(pt -> pt.transform(p)));

            return ret;
        }
    }

    public boolean checkAclCollection() throws IllegalStateException {
        if (this.mclient == null) {
            throw new IllegalStateException("Cannot check acl collection: mongo service is not enabled.");
        }

        if (!connected(this.mclient)) {
            throw new IllegalStateException("Cannot check acl collection: MongoDB not connected.");
        }

        try {
            var mu = new MongoUtils(this.mclient);

            if (!mu.doesDbExist(this.aclDb)) {
                mu.createDb(this.aclDb);
            }

            if (!mu.doesCollectionExist(this.aclDb, this.aclCollection)) {
                mu.createCollection(this.aclDb, this.aclCollection);
            }
        } catch (Throwable t) {
            LOGGER.error("Error creating acl collection", t);
            return false;
        }

        return true;
    }

    public String rootRole() {
        return rootRole;
    }
}
