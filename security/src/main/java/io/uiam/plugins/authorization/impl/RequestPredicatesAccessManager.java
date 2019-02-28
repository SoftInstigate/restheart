/*
 * uIAM - the IAM for microservices
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uiam.plugins.authorization.impl;

import static com.google.common.collect.Sets.newHashSet;
import io.uiam.handlers.exchange.ByteArrayRequest;
import static io.uiam.plugins.ConfigurablePlugin.argValue;
import static io.undertow.predicate.Predicate.PREDICATE_CONTEXT;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.uiam.plugins.FileConfigurablePlugin;
import io.uiam.plugins.PluginConfigurationException;
import io.uiam.plugins.authorization.PluggableAccessManager;
import io.uiam.utils.LambdaUtils;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestPredicatesAccessManager
        extends FileConfigurablePlugin
        implements PluggableAccessManager {

    private final HashMap<String, Set<Predicate>> acl = new HashMap<>();

    /**
     * @param configuration
     * @throws java.io.FileNotFoundException
     * @throws java.io.PluginConfigurationException
     */
    public RequestPredicatesAccessManager(String name, 
            Map<String, Object> configuration)
            throws FileNotFoundException, PluginConfigurationException {
        init(configuration, "permissions");
    }

    @Override
    public Consumer<? super Map<String, Object>> consumeConfiguration() {
        return u -> {
            try {
                String role = argValue(u, ("role"));
                String _predicate = argValue(u, "predicate");

                Predicate predicate = null;

                try {
                    predicate = PredicateParser.parse(
                            _predicate,
                            this.getClass().getClassLoader());
                } catch (Throwable t) {
                    throw new PluginConfigurationException("wrong configuration: "
                            + "Invalid predicate " + _predicate, t);
                }

                aclForRole(role).add(predicate);

            } catch (PluginConfigurationException pce) {
                LambdaUtils.throwsSneakyExcpetion(pce);
            }
        };
    }

    /**
     * @param exchange
     * @return
     */
    @Override
    public boolean isAllowed(HttpServerExchange exchange) {
        if (noAclDefined()) {
            return false;
        }

        // this fixes undertow bug 377
        // https://issues.jboss.org/browse/UNDERTOW-377
        if (exchange.getAttachment(PREDICATE_CONTEXT) == null) {
            exchange.putAttachment(PREDICATE_CONTEXT, new TreeMap<>());
        }

        // Predicate.resolve() uses getRelativePath() that is the path relative to
        // the last PathHandler We want to check against the full request path
        // see https://issues.jboss.org/browse/UNDERTOW-1317
        exchange.setRelativePath(exchange.getRequestPath());

        return roles(exchange).anyMatch(role -> aclForRole(role)
                .stream()
                .anyMatch(p -> p.resolve(exchange)));
    }

    @Override
    public boolean isAuthenticationRequired(final HttpServerExchange exchange) {
        // don't require authentication for OPTIONS requests
        if (ByteArrayRequest.wrap(exchange).isOptions()) {
            return false;
        }
        
        if (getAcl() == null) {
            return true;
        }

        Set<Predicate> ps = getAcl().get("$unauthenticated");

        if (ps != null) {
            // this fixes undertow bug 377
            // https://issues.jboss.org/browse/UNDERTOW-377
            if (exchange.getAttachment(PREDICATE_CONTEXT) == null) {
                exchange.putAttachment(PREDICATE_CONTEXT, new TreeMap<>());
            }

            // Predicate.resolve() uses getRelativePath() that is the path 
            // relative to the last PathHandler We want to check against the full 
            // request path see https://issues.jboss.org/browse/UNDERTOW-1317
            exchange.setRelativePath(exchange.getRequestPath());
            return !ps.stream().anyMatch(p -> p.resolve(exchange));
        } else {
            return true;
        }
    }

    private Stream<String> roles(HttpServerExchange exchange) {
        return account(exchange).getRoles().stream();
    }

    private boolean noAclDefined() {
        return getAcl() == null;
    }

    private Set<Predicate> aclForRole(String role) {
        Set<Predicate> predicates = getAcl().get(role);
        if (predicates == null) {
            predicates = newHashSet();
            getAcl().put(role, predicates);
        }

        return predicates;
    }

    private Account account(HttpServerExchange exchange) {
        final Account account = exchange.getSecurityContext()
                .getAuthenticatedAccount();
        return isAuthenticated(account) ? account : new NotAuthenticatedAccount();
    }

    private boolean isAuthenticated(Account authenticatedAccount) {
        return authenticatedAccount != null;
    }

    /**
     * @return the acl
     */
    public HashMap<String, Set<Predicate>> getAcl() {
        return acl;
    }

    private static class NotAuthenticatedAccount implements Account {

        private static final long serialVersionUID = 3124L;

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public Set<String> getRoles() {
            return newHashSet("$unauthenticated");
        }
    }
}
