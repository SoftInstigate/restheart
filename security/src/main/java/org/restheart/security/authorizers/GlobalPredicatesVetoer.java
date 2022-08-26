/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2022 SoftInstigate
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

import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.Authorizer.TYPE;

@RegisterPlugin(
        name = "globalPredicatesVetoer",
        description = "vetoes requests according to global predicates",
        enabledByDefault = true,
        authorizerType = TYPE.VETOER)
public class GlobalPredicatesVetoer implements Authorizer {
    @Inject("registry")
    private PluginsRegistry registry;

    @Override
    public boolean isAllowed(Request<?> request) {
        return registry.getGlobalSecurityPredicates()
                .stream()
                .allMatch(predicate -> predicate.resolve(request.getExchange()));
    }

    @Override
    public boolean isAuthenticationRequired(Request<?> request) {
        return false;
    }
}
