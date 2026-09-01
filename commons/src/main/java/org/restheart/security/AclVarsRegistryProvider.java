/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
package org.restheart.security;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

/**
 * Provides the {@link AclVarsRegistry} for plugins to register a custom {@link VarResolver}.
 *
 * @see AclVarsRegistry
 */
@RegisterPlugin(name = "acl-vars-registry", description = "provides the AclVarsRegistry to register custom ACL variables (VarResolver)")
public class AclVarsRegistryProvider implements Provider<AclVarsRegistry> {

    @Override
    public AclVarsRegistry get(PluginRecord<?> caller) {
        return AclVarsRegistryImpl.getInstance();
    }
}
