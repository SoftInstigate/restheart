/*-
 * ========================LICENSE_START=================================
 * restheart-commons
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
package org.restheart.mongodb.utils;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

/**
 * Provides the {@link CustomOperatorRegistry} for plugins to register a custom
 * {@link CustomOperator}.
 *
 * @see CustomOperatorRegistry
 */
@RegisterPlugin(name = "custom-operator-registry", description = "provides the CustomOperatorRegistry to register custom aggregation pipeline operators (CustomOperator)")
public class CustomOperatorRegistryProvider implements Provider<CustomOperatorRegistry> {

    @Override
    public CustomOperatorRegistry get(PluginRecord<?> caller) {
        return CustomOperatorRegistryImpl.getInstance();
    }
}
