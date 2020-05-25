/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package org.restheart.security.plugins.authorizers;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import static com.google.common.collect.Sets.newHashSet;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCursor;
import static com.mongodb.client.model.Filters.eq;
import static io.undertow.predicate.Predicate.PREDICATE_CONTEXT;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
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
import org.restheart.ConfigurationException;
import org.restheart.cache.Cache;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.exchange.Request;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectMongoClient;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "mongoAclAuthorizer",
        description = "authorizes requests against acl stored in mongodb")
public class MongoAclAuthorizer implements Authorizer {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(MongoAclAuthorizer.class);

    public static final String X_FORWARDED_ACCOUNT_ID = "rhAuthenticator";
    public static final String X_FORWARDED_ROLE = "RESTHeart";

    public static final AttachmentKey<FilterPredicate> MATCHING_ACL_PREDICATE = AttachmentKey.create(FilterPredicate.class);

    public static final String ACL_COLLECTION_NAME = "acl";

    public static String MREST_SUPERUSER_ROLE = "__MREST_SUPERUSER";

    public static final String $UNAUTHENTICATED = "$unauthenticated";

    String aclDb;
    String aclCollection;
    private String rootRole = null;
    private boolean cacheEnabled = false;
    private Integer cacheSize = 1_000; // 1000 entries
    private Integer cacheTTL = 60 * 1_000; // 1 minute
    private Cache.EXPIRE_POLICY cacheExpirePolicy = Cache.EXPIRE_POLICY.AFTER_WRITE;

    private LoadingCache<String, LinkedHashSet<FilterPredicate>> acl = null;

    private MongoClient mclient;

    @InjectConfiguration
    public void setConf(Map<String, Object> args) {
        this.aclDb = argValue(args, "acl-db");
        this.aclCollection = argValue(args, "acl-collection");
        this.rootRole = argValue(args, "root-role");

        if (args != null && args.containsKey("cache-enabled")) {
            this.cacheEnabled = argValue(args, "cache-enabled");

            if (this.cacheEnabled) {
                this.cacheSize = argValue(args, "cache-size");
                this.cacheTTL = argValue(args, "cache-ttl");

                String _cacheExpirePolicy = argValue(args, "cache-expire-policy");

                if (_cacheExpirePolicy != null) {
                    try {
                        this.cacheExpirePolicy = Cache.EXPIRE_POLICY
                                .valueOf((String) _cacheExpirePolicy);
                    } catch (IllegalArgumentException iae) {
                        throw new ConfigurationException(
                                "wrong configuration file format. "
                                + "cache-expire-policy valid values are "
                                + Arrays.toString(Cache.EXPIRE_POLICY.values()));
                    }
                }

                this.acl = CacheFactory.createLocalLoadingCache(
                        this.cacheSize,
                        this.cacheExpirePolicy,
                        this.cacheTTL, (String role) -> {
                            return this.findRolePredicates(role);
                        });
            }
        }
    }

    @InjectMongoClient
    public void setMongoClient(MongoClient mclient) {
        this.mclient = mclient;
        
        if (!checkAclCollection()) {
            LOGGER.error("ACL collection does not exist and could not be created");
        }
    }

    /**
     * @param request
     * @return
     */
    @Override
    public boolean isAllowed(Request request) {
        // always allow OPTIONS requests
        if (request.isOptions()) {
            return true;
        }

        var exchange = request.getExchange();

        if (this.rootRole != null
                && exchange.getSecurityContext() != null
                && exchange.getSecurityContext()
                        .getAuthenticatedAccount() != null
                && exchange.getSecurityContext()
                        .getAuthenticatedAccount()
                        .getRoles().contains(this.rootRole)) {
            LOGGER.debug("allow request for root user {}", exchange
                    .getSecurityContext()
                    .getAuthenticatedAccount().getPrincipal().getName());
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

        final ArrayList<FilterPredicate> predicates = new ArrayList<>();

        // debug roles and predicates evaluation order
        if (LOGGER.isDebugEnabled()) {
            roles(exchange).forEachOrdered(role
                    -> {
                ArrayList<FilterPredicate> matched = Lists.newArrayListWithCapacity(1);

                predicatesForRole(role)
                        .stream().anyMatch(predicate -> {
                            var resolved = predicate.resolve(exchange);

                            String marker;

                            // to highlight the effective predicate
                            if (resolved && matched.isEmpty()) {
                                matched.add(predicate);
                                marker = "<--";
                            } else {
                                marker = "";
                            }

                            LOGGER.debug("role {}, predicate {}, resolve {} {}",
                                    role,
                                    predicate.getId(),
                                    resolved,
                                    marker);

                            return false;
                        });
            });
        }

        // the applicable predicate is the ones that
        // resolves the exchange
        roles(exchange)
                .forEachOrdered(role
                        -> predicatesForRole(role)
                        .stream()
                        .anyMatch(r -> {
                            if (r.resolve(exchange)) {
                                predicates.add(r);
                                return true;
                            } else {
                                return false;
                            }
                        }));

        if (predicates.isEmpty()) {
            return false;
        } else {
            exchange.putAttachment(MATCHING_ACL_PREDICATE, predicates.get(0));
            return true;
        }
    }

    @Override
    public boolean isAuthenticationRequired(Request request) {
        // don't require authentication for OPTIONS requests
        if (request.isOptions()) {
            return false;
        }

        var exchange = request.getExchange();

        var ps = getRoleFilterPredicates($UNAUTHENTICATED);

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

            return !ps.stream().anyMatch(r -> r.resolve(exchange));
        } else {
            return true;
        }
    }

    private Stream<String> roles(HttpServerExchange exchange) {
        return account(exchange).getRoles().stream();
    }

    private LinkedHashSet<FilterPredicate> predicatesForRole(String role) {
        LinkedHashSet<FilterPredicate> predicates = getRoleFilterPredicates(role);

        if (predicates == null) {
            return Sets.newLinkedHashSet();
        } else {
            return predicates;
        }
    }

    private Account account(HttpServerExchange exchange) {
        final Account account = exchange.getSecurityContext().getAuthenticatedAccount();
        return isAuthenticated(account) ? account : new NotAuthenticatedAccount();
    }

    private boolean isAuthenticated(Account authenticatedAccount) {
        return authenticatedAccount != null;
    }

    /**
     * @param role
     * @return the acl
     */
    public LinkedHashSet<FilterPredicate> getRoleFilterPredicates(String role) {
        if (this.cacheEnabled) {
            var _roleFilterPredicates = this.acl.getLoading(role);

            if (_roleFilterPredicates != null && _roleFilterPredicates.isPresent()) {
                return _roleFilterPredicates.get();
            } else {
                return null;
            }
        } else {
            return findRolePredicates(role);
        }
    }

    private static class NotAuthenticatedAccount implements Account {

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public Set<String> getRoles() {
            return newHashSet("$unauthenticated");
        }
    }

    private static final BsonDocument PROJECTION = BsonDocument.parse("{\"_id\":1,\"roles\":1,\"predicate\":1,\"writeFilter\":1,\"readFilter\":1,\"priority\":1}");
    private static final BsonDocument SORT = BsonDocument.parse("{\"priority\":-1,\"_id\":-1}");

    private LinkedHashSet<FilterPredicate> findRolePredicates(final String role) {
        if (this.mclient == null) {
            LOGGER.error("Cannot find acl: mongo service is not enabled.");
            return null;
        } else {
            var predicates = this.mclient.getDatabase(this.aclDb)
                    .getCollection(this.aclCollection, BsonDocument.class)
                    .find(eq("roles", role))
                    .projection(PROJECTION)
                    .sort(SORT);

            if (predicates == null) {
                return new LinkedHashSet<>();
            } else {
                LinkedHashSet<FilterPredicate> ret = Sets.newLinkedHashSet();

                StreamSupport.stream(predicates.spliterator(), true)
                        .filter(predicateElem -> predicateElem.isDocument())
                        .map(predicateElem -> predicateElem.asDocument())
                        .filter(predicateDocument -> {
                            // filter out illegal predicates
                            try {
                                new FilterPredicate(predicateDocument);
                                return true;
                            } catch (IllegalArgumentException iae) {
                                LOGGER.warn("invalid predicate _id={}", predicateDocument.get("_id"));
                                return false;
                            }
                        })
                        .map(predicateDocument -> new FilterPredicate(predicateDocument))
                        .forEachOrdered(p -> {
                            ret.add(p);
                        });

                return ret;
            }
        }
    }
    
    public boolean checkAclCollection() {
        if (this.mclient == null) {
            LOGGER.error("Cannot check acl collection: mongo service is not enabled.");
            return false;
        }
        
        var db = mclient.getDatabase(this.aclDb);

        MongoCursor<String> dbCollections = db
                .listCollectionNames()
                .iterator();

        while (dbCollections.hasNext()) {
            String dbCollection = dbCollections.next();

            if (this.aclCollection.equals(dbCollection)) {
                return true;
            }
        }

        try {
            db.createCollection(this.aclCollection);

            return true;
        } catch (Throwable t) {
            LOGGER.error("Error creating acl collection", t);
            return false;
        }
    }
}
