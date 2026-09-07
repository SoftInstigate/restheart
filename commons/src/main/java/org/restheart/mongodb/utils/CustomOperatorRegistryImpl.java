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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.restheart.configuration.ConfigurationException;

/**
 * Default {@link CustomOperatorRegistry} implementation: a process-wide singleton,
 * reachable both via dependency injection (see {@code CustomOperatorRegistryProvider},
 * for plugins registering a {@link CustomOperator}) and via {@link #getInstance()} (for
 * {@link VarsInterpolator}'s static methods, which cannot receive an injected instance).
 *
 * <p>There are no built-in operators to register at construction time — unlike
 * {@code AclVarsRegistryImpl}, which seeds {@code @user}/{@code @now}/etc. — since
 * {@code $var}/{@code $arg} are handled directly by {@link VarsInterpolator}, not
 * through this registry.
 */
public class CustomOperatorRegistryImpl implements CustomOperatorRegistry {
    private static final CustomOperatorRegistryImpl HOLDER = new CustomOperatorRegistryImpl();

    private final Map<String, CustomOperator> operators = new LinkedHashMap<>();

    private CustomOperatorRegistryImpl() {
    }

    static CustomOperatorRegistryImpl getInstance() {
        return HOLDER;
    }

    /**
     * {@code $var}/{@code $arg} are handled directly by {@link VarsInterpolator} before
     * this registry is ever consulted (see its dispatch order) — a custom operator
     * registered under either name would silently never be reached, so registration is
     * refused outright rather than left as a silent footgun.
     */
    private static final Set<String> RESERVED_NAMES = Set.of(
            VarsInterpolator.VAR_OPERATOR.$var.name().substring(1),
            VarsInterpolator.VAR_OPERATOR.$arg.name().substring(1));

    @Override
    public void register(CustomOperator operator) throws ConfigurationException {
        var name = operator.name();

        if (RESERVED_NAMES.contains(name)) {
            throw new ConfigurationException(
                    "Cannot register custom operator '$" + name + "': this name is reserved by VarsInterpolator");
        }

        if (operators.containsKey(name)) {
            throw new ConfigurationException(
                    "Cannot register custom operator '$" + name + "': an operator with this name is already registered");
        }

        operators.put(name, operator);
    }

    /**
     * Looks up a registered operator by name, without the leading {@code $}.
     */
    Optional<CustomOperator> operator(String name) {
        return Optional.ofNullable(operators.get(name));
    }

    /**
     * All registered operator names, without the leading {@code $}, in registration order.
     */
    Set<String> names() {
        return operators.keySet();
    }
}
