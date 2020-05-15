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
package com.restheart.authorizers;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import static com.google.common.collect.Sets.newHashSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.restheart.security.net.Client;
import com.restheart.security.net.Request;
import static com.restheart.rhAuthenticator.RHAuthenticator.X_FORWARDED_ACCOUNT_ID;
import static com.restheart.rhAuthenticator.RHAuthenticator.X_FORWARDED_ROLE;
import com.restheart.security.plugins.interceptors.FilterPredicateInjector;
import static io.undertow.predicate.Predicate.PREDICATE_CONTEXT;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.LinkedHashSet;
import java.util.stream.StreamSupport;
import org.restheart.security.Bootstrapper;
import org.restheart.security.ConfigurationException;
import org.restheart.security.cache.Cache;
import org.restheart.security.cache.CacheFactory;
import org.restheart.security.cache.LoadingCache;
import org.restheart.security.handlers.exchange.ByteArrayRequest;
import static org.restheart.security.handlers.injectors.XForwardedHeadersInjector.getXForwardedAccountIdHeaderName;
import static org.restheart.security.handlers.injectors.XForwardedHeadersInjector.getXForwardedRolesHeaderName;
import org.restheart.plugins.security.Authorizer;
import static org.restheart.security.plugins.ConfigurablePlugin.argValue;
import org.restheart.security.plugins.PluginsRegistry;
import org.restheart.security.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RHAuthorizer implements Authorizer {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(RHAuthorizer.class);

    public static final AttachmentKey<FilterPredicate> MATCHING_ACL_PREDICATE = AttachmentKey.create(FilterPredicate.class);

    public static final String ACL_COLLECTION_NAME = "acl";

    public static String MREST_SUPERUSER_ROLE = "__MREST_SUPERUSER";

    public static final String $UNAUTHENTICATED = "$unauthenticated";

    private URI restheartBaseUrl = null;
    private URI aclUrl = null;

    private String aclUri = null;
    private String rootRole = null;
    private Boolean CACHE_ENABLED = false;
    private Long CACHE_SIZE = 1_000l; // 1000 entries
    private Long CACHE_TTL = 10 * 1_000l; // 10 seconds
    private Cache.EXPIRE_POLICY CACHE_EXPIRE_POLICY
            = Cache.EXPIRE_POLICY.AFTER_WRITE;

    private LoadingCache<String, LinkedHashSet<FilterPredicate>> acl = null;

    public RHAuthorizer(final String name, final Map<String, Object> args)
            throws ConfigurationException {
        this.aclUri = argValue(args, "acl-uri");
        this.rootRole = argValue(args, "root-role");

        this.restheartBaseUrl = Bootstrapper.getConfiguration()
                .getRestheartBaseUrl();

        this.aclUrl = this.restheartBaseUrl.resolve(this.restheartBaseUrl.getPath()
                .concat(aclUri));

        if (args != null && args.containsKey("cache-enabled")) {
            this.CACHE_ENABLED = argValue(args, "cache-enabled");

            if (this.CACHE_ENABLED) {
                Number _size = argValue(args, "cache-size");
                Number _ttl = argValue(args, "cache-ttl");
                this.CACHE_SIZE = _size.longValue();
                this.CACHE_TTL = _ttl.longValue();

                String _cacheExpirePolicy = argValue(args, "cache-expire-policy");

                if (_cacheExpirePolicy != null) {
                    try {
                        this.CACHE_EXPIRE_POLICY = Cache.EXPIRE_POLICY
                                .valueOf((String) _cacheExpirePolicy);
                    } catch (IllegalArgumentException iae) {
                        throw new ConfigurationException(
                                "wrong configuration file format. "
                                + "cache-expire-policy valid values are "
                                + Arrays.toString(Cache.EXPIRE_POLICY.values()));
                    }
                }
            }
        }

        if (this.CACHE_ENABLED) {
            this.acl = CacheFactory.createLocalLoadingCache(
                    CACHE_SIZE,
                    CACHE_EXPIRE_POLICY,
                    CACHE_TTL, (String role) -> {
                        return this.findRolePredicates(role);
                    });
        }

        // add a request interceptor to inject the filter eventually specified 
        // in the appying FilterPredicate
        PluginsRegistry.getInstance()
                .getRequestInterceptors()
                .add(new FilterPredicateInjector());
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
        
        if (this.rootRole != null
                && exchange
                        .getSecurityContext() != null
                && exchange
                        .getSecurityContext().getAuthenticatedAccount() != null
                && exchange
                        .getSecurityContext()
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
                ArrayList matched = Lists.newArrayListWithCapacity(1);

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
                            if (r.resolve(exchange)
                                    ) {
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
    public boolean isAuthenticationRequired(final HttpServerExchange exchange) {
        // don't require authentication for OPTIONS requests
        if (ByteArrayRequest.wrap(exchange).isOptions()) {
            return false;
        }

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
            exchange.setRelativePath(exchange.getRequestPath());

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
        if (CACHE_ENABLED) {
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

    private static String PROJECTION = "{\"_id\":1,\"roles\":1,\"predicate\":1,\"writeFilter\":1,\"readFilter\":1,\"priority\":1}";
    private static String SORT = "{\"priority\":-1,\"_id\":-1}";

    private LinkedHashSet<FilterPredicate> findRolePredicates(final String role) {
        var ajp = "ajp".equalsIgnoreCase(restheartBaseUrl.getScheme());

        String _acl;

        try {
            if (ajp) {
                var resp = Client.getInstance().execute(new Request(
                        Request.METHOD.GET, aclUrl)
                        .header(getXForwardedAccountIdHeaderName().toString(),
                                X_FORWARDED_ACCOUNT_ID)
                        .header(getXForwardedRolesHeaderName().toString(),
                                X_FORWARDED_ROLE)
                        .parameter("filter", "{\""
                                .concat("roles\":\"")
                                .concat(role)
                                .concat("\"}"))
                        .parameter("sort", SORT)
                        .parameter("keys", PROJECTION)
                        .parameter("rep", "STANDARD"));

                if (resp.getStatusCode() != HttpStatus.SC_OK) {
                    LOGGER.warn("Wrong response finding acl for role: {}. "
                            + "Response status: {}",
                            role,
                            resp.getStatus());
                    return null;
                } else {
                    _acl = resp.getBody();
                }
            } else {
                var resp = Unirest.get(aclUrl.toString())
                        .header(getXForwardedAccountIdHeaderName().toString(),
                                X_FORWARDED_ACCOUNT_ID)
                        .header(getXForwardedRolesHeaderName().toString(),
                                X_FORWARDED_ROLE)
                        .queryString("filter", "{\""
                                .concat("roles\":\"")
                                .concat(role)
                                .concat("\"}"))
                        .queryString("sort", SORT)
                        .queryString("keys", PROJECTION)
                        .queryString("rep", "STANDARD")
                        .asString();

                if (resp.getStatus() != HttpStatus.SC_OK) {
                    LOGGER.warn("Wrong response finding the acl for role: {}. "
                            + "Response status: {}",
                            role,
                            resp.getStatus());
                    return null;
                } else {
                    _acl = resp.getBody();
                }
            }
        } catch (UnirestException | IOException ex) {
            LOGGER.warn("Error requesting {}: {}", aclUrl, ex.getMessage());
            return null;
        }

        var acl = JsonParser.parseString(_acl);

        if (!acl.isJsonArray()) {
            LOGGER.warn("Response body is not a array", role);
            return null;
        }

        LinkedHashSet<FilterPredicate> ret = Sets.newLinkedHashSet();

        StreamSupport.stream(acl.getAsJsonArray().spliterator(), true)
                .filter(predicateElem -> predicateElem.isJsonObject())
                .map(predicateElem -> predicateElem.getAsJsonObject())
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
