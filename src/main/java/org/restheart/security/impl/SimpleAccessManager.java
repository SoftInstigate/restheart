/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.security.impl;

import org.restheart.handlers.RequestContext;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.collect.Sets.newHashSet;
import static io.undertow.predicate.Predicate.PREDICATE_CONTEXT;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.restheart.security.AccessManager;

/**
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public final class SimpleAccessManager extends AbstractSimpleSecurityManager implements AccessManager {
    private final HashMap<String, Set<Predicate>> acl = new HashMap<>();

    /**
     * @param configuration
     * @throws java.io.FileNotFoundException
     */
    public SimpleAccessManager(Map<String, Object> configuration) throws FileNotFoundException {
        init(configuration, "permissions");
    }

    @Override
    Consumer<? super Map<String, Object>> consumeConfiguration() {
        return u -> {
            Object _role = u.get("role");
            Object _predicate = u.get("predicate");

            if (_role == null || !(_role instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. a permission entry is missing the role");
            }

            String role = (String) _role;

            if (_predicate == null || !(_predicate instanceof String)) {
                throw new IllegalArgumentException("wrong configuration file format. a permission entry is missing the predicate");
            }

            Predicate predicate = null;

            try {
                predicate = PredicateParser.parse((String) _predicate, this.getClass().getClassLoader());
            } catch (Throwable t) {
                throw new IllegalArgumentException("wrong configuration file format. wrong predicate " + (String) _predicate, t);
            }

            aclForRole(role).add(predicate);
        };
    }

    /**
     * @param exchange
     * @param context
     * @return
     */
    @Override
    public boolean isAllowed(HttpServerExchange exchange, RequestContext context) {
        if (noAclDefined()) {
            return false;
        }

        // this fixes undertow bug 377
        // https://issues.jboss.org/browse/UNDERTOW-377
        if (exchange.getAttachment(PREDICATE_CONTEXT) == null) {
            exchange.putAttachment(PREDICATE_CONTEXT, new TreeMap<String, Object>());
        }
        
        return roles(exchange).anyMatch(role -> aclForRole(role).stream().anyMatch(p -> p.resolve(exchange)));
    }
    
    @Override
    public boolean isAuthenticationRequired(final HttpServerExchange exchange) {
        if (getAcl() == null)
            return true;
        
        Set<Predicate> ps = getAcl().get("$unauthenticated");
        return ps == null ? true : !ps.stream().anyMatch(p -> p.resolve(exchange));
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
        final Account account = exchange.getSecurityContext().getAuthenticatedAccount();
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
