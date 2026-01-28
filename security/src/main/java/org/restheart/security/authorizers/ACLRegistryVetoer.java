/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.Authorizer.TYPE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(
        name = "aclRegistryVetoer",
        description = "vetoes requests according to veto predicates defined in the ACLRegistry",
        enabledByDefault = true,
        authorizerType = TYPE.VETOER)
public class ACLRegistryVetoer implements Authorizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ACLRegistryVetoer.class);

    private final ACLRegistryImpl registry = ACLRegistryImpl.getInstance();

    @Override
    public boolean isAllowed(Request<?> request) {
        var vetoed = registry.vetoPredicates()
            .stream()
            .anyMatch(predicate -> predicate.test(request));

        if (LOGGER.isDebugEnabled() && vetoed) {
            LOGGER.debug("Request vetoed by ACLRegistryVetoer due to a veto predicate");
        }

        return !vetoed;
    }

    @Override
    public boolean isAuthenticationRequired(Request<?> request) {
        return false;
    }
}
