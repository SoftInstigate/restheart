/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.PermissionEnumerator;
import org.restheart.security.BaseAclPermission;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "fullAuthorizer",
        description = "authorizes all requests",
        enabledByDefault = false)
public class FullAuthorizer implements Authorizer, PermissionEnumerator {

    private boolean authenticationRequired;

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() {
        this.authenticationRequired = arg(this.config, "authentication-required");
    }

    /**
     * this Authorizer allows any operation to any user
     *
     * @param authenticationRequired
     */
    public FullAuthorizer(boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    /**
     * this Authorizer allows any operation to any user
     *
     * @throws org.restheart.configuration.ConfigurationException
     */
    public FullAuthorizer() {
        this(false);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isAllowed(final Request request) {
        return true;
    }
    @Override
    @SuppressWarnings("rawtypes")
    public boolean isAuthenticationRequired(final Request request) {
        return !request.isOptions() && authenticationRequired;
    }

    /**
     * Its rule, written down: {@code true}.
     *
     * <p>Enumerable like any other, because "allow everything" is something the predicate language
     * can say. Without this, a service configured with no authorizer but this one — which is what
     * {@code secured = false} gets — would have an empty MCP catalog: not one resource could be
     * shown to be allowed, on a server where everything is.
     */
    @Override
    public Set<BaseAclPermission> permissions(Request<?> request) {
        return Set.of(ALLOW_EVERYTHING);
    }

    private static final BaseAclPermission ALLOW_EVERYTHING =
            new BaseAclPermission(request -> true, Set.of(), 0, null) {
                @Override
                public Optional<String> predicateSource() {
                    return Optional.of("true");
                }
            };
}
