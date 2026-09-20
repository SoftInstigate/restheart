/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.providers;

import java.util.LinkedHashSet;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistryImpl;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AclPermissions;
import org.restheart.plugins.security.PermissionEnumerator;

/**
 * Provides {@link AclPermissions}: the rules that apply to a caller, from the authorizers that can
 * enumerate theirs.
 *
 * <p>Resolved lazily, on first injection, because authorizers are themselves plugins: at
 * provider-registration time the registry may not hold them all yet.
 */
@RegisterPlugin(name = "acl-permissions",
        description = "provides the ACL permissions applying to a caller, for plugins that analyse them")
public class AclPermissionsProvider implements Provider<AclPermissions> {

    @Override
    public AclPermissions get(PluginRecord<?> caller) {
        return request -> {
            var applicable = new LinkedHashSet<org.restheart.security.BaseAclPermission>();

            PluginsRegistryImpl.getInstance().getAuthorizers().stream()
                    .filter(PluginRecord::isEnabled)
                    .map(PluginRecord::getInstance)
                    .filter(PermissionEnumerator.class::isInstance)
                    .map(PermissionEnumerator.class::cast)
                    .forEach(enumerator -> applicable.addAll(enumerator.permissions(request)));

            return applicable;
        };
    }
}
