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
package org.restheart.security.plugins.authorizers;

import static com.google.common.collect.Sets.newHashSet;
import io.undertow.predicate.Predicate;
import static io.undertow.predicate.Predicate.PREDICATE_CONTEXT;
import io.undertow.predicate.PredicateParser;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.restheart.ConfigurationException;
import org.restheart.exchange.Request;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.FileConfigurablePlugin;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;
import org.restheart.utils.LambdaUtils;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "fileAclAuthorizer",
        description = "authorizes requests according to acl defined in a configuration file",
        enabledByDefault = false)
public class FileAclAuthorizer
        extends FileConfigurablePlugin
        implements Authorizer {

    private final HashMap<String, Set<Predicate>> acl = new HashMap<>();

    @InjectConfiguration
    public void init(Map<String, Object> confArgs)
            throws FileNotFoundException, ConfigurationException {
        init(confArgs, "permissions");
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
                    throw new ConfigurationException("wrong configuration: "
                            + "Invalid predicate " + _predicate, t);
                }

                aclForRole(role).add(predicate);

            } catch (ConfigurationException pce) {
                LambdaUtils.throwsSneakyExcpetion(pce);
            }
        };
    }

    /**
     * @param request
     * @return
     */
    @Override
    public boolean isAllowed(final Request request) {
        if (noAclDefined()) {
            return false;
        }
        
        var exchange = request.getExchange();

        // this fixes undertow bug 377
        // https://issues.jboss.org/browse/UNDERTOW-377
        if (exchange.getAttachment(PREDICATE_CONTEXT) == null) {
            exchange.putAttachment(PREDICATE_CONTEXT, new TreeMap<>());
        }

        // Predicate.resolve() uses getRelativePath() that is the path relative to
        // the last PathHandler We want to check against the full request path
        // see https://issues.jboss.org/browse/UNDERTOW-1317
        request.getExchange().setRelativePath(request.getExchange().getRequestPath());

        return roles(exchange).anyMatch(role -> aclForRole(role)
                .stream()
                .anyMatch(p -> p.resolve(exchange)));
    }

    @Override
    public boolean isAuthenticationRequired(final Request request) {
        // don't require authentication for OPTIONS requests
        if (request.isOptions()) {
            return false;
        }

        if (getAcl() == null) {
            return true;
        }

        Set<Predicate> ps = getAcl().get("$unauthenticated");

        if (ps != null) {
            var exchange = request.getExchange();
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
