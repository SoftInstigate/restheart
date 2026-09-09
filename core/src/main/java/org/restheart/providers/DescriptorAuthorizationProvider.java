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
package org.restheart.providers;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.security.DescriptorAuthorization;
import org.restheart.security.authorizers.DescriptorAuthorizationImpl;

/**
 * Provides {@link DescriptorAuthorization}, so a plugin can ask whether a caller could perform an
 * operation and get the same answer the framework gives when performing it.
 *
 * <p>Resolved lazily, on first injection, because authorizers are themselves plugins: at
 * provider-registration time the registry may not hold them all yet.
 */
@RegisterPlugin(name = "descriptor-authorization",
        description = "provides the framework's decision on whether a caller may perform an operation")
public class DescriptorAuthorizationProvider implements Provider<DescriptorAuthorization> {

    private volatile DescriptorAuthorization authorization;

    @Override
    public DescriptorAuthorization get(PluginRecord<?> caller) {
        var resolved = authorization;

        if (resolved == null) {
            synchronized (this) {
                resolved = authorization;

                if (resolved == null) {
                    resolved = new DescriptorAuthorizationImpl(PluginsRegistryImpl.getInstance().getAuthorizers());
                    authorization = resolved;
                }
            }
        }

        return resolved;
    }
}
