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

import java.util.Collection;
import java.util.List;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.DescriptorAuthorization;
import org.restheart.plugins.security.DescriptorAwareAuthorizer;
import org.restheart.plugins.security.DescriptorAwareAuthorizer.Decision;
import org.restheart.plugins.security.RequestDescriptor;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The framework's implementation of {@link DescriptorAuthorization}: every vetoer must allow an
 * operation and at least one allower must allow it.
 *
 * <p>Bound to a set of authorizers when built, so the split into vetoers and allowers is paid once
 * rather than per decision — which matters when a caller decides a whole catalog at a time.
 *
 * @see DescriptorAuthorizationProvider
 */
public class DescriptorAuthorizationImpl implements DescriptorAuthorization {
    private static final Logger LOGGER = LoggerFactory.getLogger(DescriptorAuthorizationImpl.class);

    private final List<DescriptorAwareAuthorizer> vetoers;
    private final List<DescriptorAwareAuthorizer> allowers;

    public DescriptorAuthorizationImpl(Collection<PluginRecord<Authorizer>> authorizers) {
        var descriptorAware = authorizers.stream()
                .filter(PluginRecord::isEnabled)
                .map(PluginRecord::getInstance)
                .filter(DescriptorAwareAuthorizer.class::isInstance)
                .map(DescriptorAwareAuthorizer.class::cast)
                .toList();

        this.vetoers = descriptorAware.stream()
                .filter(a -> PluginUtils.authorizerType(a) == Authorizer.TYPE.VETOER).toList();

        this.allowers = descriptorAware.stream()
                .filter(a -> PluginUtils.authorizerType(a) == Authorizer.TYPE.ALLOWER).toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>An authorizer that throws counts against the operation: a vetoer that cannot answer
     * denies, and an allower that cannot answer simply does not grant. With no descriptor-aware
     * authorizer configured at all the answer is {@link Decision#DENIED} — an operation nobody is
     * in a position to authorize is not thereby authorized.
     */
    @Override
    public Decision decide(RequestDescriptor descriptor) {
        if (vetoers.isEmpty() && allowers.isEmpty()) {
            LOGGER.debug("No DescriptorAwareAuthorizer configured — denying {}", descriptor);
            return Decision.DENIED;
        }

        for (var vetoer : vetoers) {
            try {
                if (!vetoer.decide(descriptor).allowed()) {
                    return Decision.DENIED;
                }
            } catch (Exception ex) {
                LOGGER.error("Error in VETOER {} evaluating a RequestDescriptor", PluginUtils.name(vetoer), ex);
                return Decision.DENIED;
            }
        }

        for (var allower : allowers) {
            try {
                var decision = allower.decide(descriptor);

                if (decision.allowed()) {
                    return decision;
                }
            } catch (Exception ex) {
                LOGGER.error("Error in ALLOWER {} evaluating a RequestDescriptor", PluginUtils.name(allower), ex);
            }
        }

        return Decision.DENIED;
    }
}
