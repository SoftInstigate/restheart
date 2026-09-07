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

import org.restheart.configuration.ConfigurationException;

/**
 * Registry for programmatically contributing {@link CustomOperator}s to
 * {@link VarsInterpolator}.
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * @Inject("custom-operator-registry")
 * private CustomOperatorRegistry operators;
 *
 * @OnInit
 * public void init() {
 *     operators.register(new MyOperator());
 * }
 * }</pre>
 *
 * @see CustomOperator
 */
public interface CustomOperatorRegistry {
    /**
     * Registers a {@link CustomOperator}, making its {@code $name} usable inline in
     * aggregation pipeline {@code stages} and GraphQL mappings.
     *
     * <p>Must be called from {@code @OnInit}, before any request is processed.
     *
     * @param operator the operator to register; must not be {@code null}
     * @throws ConfigurationException if {@link CustomOperator#name()} is already registered
     */
    void register(CustomOperator operator) throws ConfigurationException;
}
