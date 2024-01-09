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

import static com.google.common.collect.Sets.newHashSet;
import static io.undertow.predicate.Predicate.PREDICATE_CONTEXT;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import java.io.FileNotFoundException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.plugins.FileConfigurablePlugin;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;
import static org.restheart.security.BaseAclPermission.MATCHING_ACL_PERMISSION;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "fileAclAuthorizer",
        description = "authorizes requests according to acl defined in a configuration file",
        enabledByDefault = false)
public class FileAclAuthorizer extends FileConfigurablePlugin implements Authorizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileAclAuthorizer.class);

    public static final String $UNAUTHENTICATED = "$unauthenticated";

    private final Set<FileAclPermission> permissions = new LinkedHashSet<>();

    @Inject("registry")
    private PluginsRegistry registry;

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() throws FileNotFoundException, ConfigurationException {
        if (config.containsKey("conf-file") && config.get("conf-file") != null) {
            // init from conf-file
            init(config, "permissions");
        } else if (config.containsKey("permissions") && config.get("permissions") != null) {
            // init from permissions list property
            List<Map<String, Object>> _permissions = argOrDefault(config, "permissions", new ArrayList<>());
            _permissions.stream().forEach(consumeConfiguration());
        } else {
            throw new IllegalArgumentException("The configuration requires either 'conf-file' or 'permissions' paramenter");
        }

        // reverse oreder, the first permission in the acl.yml must be on top
        var list = new ArrayList<FileAclPermission>(this.permissions);
        Collections.reverse(list);
        this.permissions.clear();
        list.stream().forEach(permissions::add);
    }

    @Override
    public Consumer<? super Map<String, Object>> consumeConfiguration() {
        return p -> {
            try {
                this.permissions.add(FileAclPermission.build(p));
            } catch (ConfigurationException pce) {
                LOGGER.error("Wrong permission", pce);
            }
        };
    }

    private boolean permissionsTransformed = false;
    private void transformPermissions() {
        this.permissions.forEach(p -> this.registry.getPermissionTransformers().stream().forEach(pt -> pt.transform(p)));
    }

    /**
     * @param request
     * @return
     */
    @Override
    public boolean isAllowed(Request<?> request) {
        // on first request, apply the permission transformers
        if (!permissionsTransformed) {
            permissionsTransformed = true;
            transformPermissions();
        }

        // always allow OPTIONS requests
        if (request.isOptions()) {
            return true;
        }

        var exchange = request.getExchange();

        // this fixes undertow bug 377
        // https://issues.jboss.org/browse/UNDERTOW-377
        if (exchange.getAttachment(PREDICATE_CONTEXT) == null) {
            exchange.putAttachment(PREDICATE_CONTEXT, new TreeMap<>());
        }

        // Predicate.resolve() uses getRelativePath() that is the path relative to
        // the last PathHandler we want to check against the full request path
        // see https://issues.jboss.org/browse/UNDERTOW-1317
        exchange.setRelativePath(exchange.getRequestPath());

        final ArrayList<FileAclPermission> machedPermissions = new ArrayList<>();

        // debug roles and permissions evaluation order
        if (LOGGER.isDebugEnabled()) {
            roles(exchange).forEachOrdered(role
                    -> {
                ArrayList<FileAclPermission> matched = Lists.newArrayListWithCapacity(1);

                rolePermissions(role)
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

                            LOGGER.debug("role {}, permission (roles={},predicate={}), resolve {} {}",
                                    role,
                                    permission.getRoles(),
                                    permission.getRequestPredicate(),
                                    resolved,
                                    marker);

                            return false;
                        });
            });
        }

        // the applicable permission is the ones that
        // resolves the exchange
        roles(exchange)
                .forEachOrdered(role -> rolePermissions(role)
                        .stream()
                        .anyMatch(p -> {
                            if (p.allow(request)) {
                                machedPermissions.add(p);
                                return true;
                            } else {
                                return false;
                            }
                        }));

        if (machedPermissions.isEmpty()) {
            return false;
        } else {
            exchange.putAttachment(MATCHING_ACL_PERMISSION, machedPermissions.get(0));
            return true;
        }
    }

    @Override
    public boolean isAuthenticationRequired(Request<?> request) {
        // don't require authentication for OPTIONS requests
        if (request.isOptions()) {
            return false;
        }

        var exchange = request.getExchange();

        var ps = rolePermissions($UNAUTHENTICATED);

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

    private Stream<String> roles(HttpServerExchange exchange) {
        return account(exchange).getRoles().stream();
    }

    private LinkedHashSet<FileAclPermission> rolePermissions(final String role) {
        LinkedHashSet<FileAclPermission> ret = Sets.newLinkedHashSet();

        StreamSupport.stream(this.permissions.spliterator(), true)
                .filter(p -> p.getRoles() != null && p.getRoles().contains(role))
                .sorted(Comparator.comparingInt(FileAclPermission::getPriority))
                .forEachOrdered(p -> ret.add(p));

        return ret;
    }

    private Account account(HttpServerExchange exchange) {
        final Account account = exchange.getSecurityContext()
                .getAuthenticatedAccount();
        return isAuthenticated(account) ? account : new NotAuthenticatedAccount();
    }

    private boolean isAuthenticated(Account authenticatedAccount) {
        return authenticatedAccount != null;
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
