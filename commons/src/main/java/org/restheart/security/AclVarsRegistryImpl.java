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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.restheart.configuration.ConfigurationException;

/**
 * Default {@link AclVarsRegistry} implementation: a process-wide singleton, reachable both via
 * dependency injection (see {@link AclVarsRegistryProvider}, for plugins registering a
 * {@link VarResolver}) and via {@link #getInstance()} (for {@link AclVarsInterpolator}'s static
 * methods, which cannot receive an injected instance).
 *
 * <p>Built-in resolvers are registered in the constructor, i.e. at class-loading time — before
 * any plugin {@code @OnInit} can run — so they always win a name collision (see
 * {@link #register(VarResolver)}).
 */
public class AclVarsRegistryImpl implements AclVarsRegistry {
    private static final AclVarsRegistryImpl HOLDER = new AclVarsRegistryImpl();

    private final Map<String, VarResolver> resolvers = new LinkedHashMap<>();

    private AclVarsRegistryImpl() {
        BuiltInVarResolvers.all().forEach(resolver -> resolvers.put(resolver.name(), resolver));
    }

    static AclVarsRegistryImpl getInstance() {
        return HOLDER;
    }

    @Override
    public void register(VarResolver resolver) throws ConfigurationException {
        var name = resolver.name();

        if (resolvers.containsKey(name)) {
            throw new ConfigurationException(
                    "Cannot register VarResolver '" + name + "': a resolver with this name is already registered");
        }

        resolvers.put(name, resolver);
    }

    /**
     * Looks up a registered resolver by name, without the leading {@code @}.
     */
    Optional<VarResolver> resolver(String name) {
        return Optional.ofNullable(resolvers.get(name));
    }

    /**
     * All registered resolver names, without the leading {@code @}, built-in and custom —
     * in registration order (built-ins first).
     */
    Set<String> names() {
        return resolvers.keySet();
    }
}
