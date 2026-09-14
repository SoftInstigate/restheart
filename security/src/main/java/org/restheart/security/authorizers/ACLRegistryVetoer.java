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
import org.restheart.plugins.security.RequestDescriptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.DescriptorAwareAuthorizer;
import org.restheart.plugins.security.Authorizer.TYPE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(
        name = "aclRegistryVetoer",
        description = "vetoes requests according to veto predicates defined in the ACLRegistry",
        enabledByDefault = true,
        authorizerType = TYPE.VETOER)
public class ACLRegistryVetoer implements DescriptorAwareAuthorizer {
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

    /**
     * The same predicates, against an operation described as data.
     *
     * <p>This one matters more than the allower's. A vetoer that is not consulted fails
     * <em>open</em>: an operation a real request would be refused is listed as available, so the
     * MCP catalogue advertises resources whose read then fails — or worse, names resources a
     * deployment vetoes precisely so that this caller does not learn they exist.
     */
    @Override
    public Decision decide(RequestDescriptor descriptor) {
        return isAllowed(SyntheticRequestFactory.from(descriptor)) ? Decision.allowed(null, null) : Decision.DENIED;
    }

    @Override
    public boolean isAuthenticationRequired(Request<?> request) {
        return false;
    }
}
