/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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
package org.restheart.plugins;

/**
 * The root interface for all RESTHeart plugins.
 * <p>
 * This interface serves as the base marker interface for all plugin types in the RESTHeart
 * framework. It provides a common type hierarchy for the plugin system, enabling uniform
 * handling of different plugin implementations during registration, lifecycle management,
 * and runtime operations.
 * </p>
 * <p>
 * RESTHeart supports various types of plugins, all of which implement this base interface:
 * <ul>
 *   <li><strong>Services</strong> - Handle HTTP requests and generate responses</li>
 *   <li><strong>Interceptors</strong> - Intercept and modify requests/responses</li>
 *   <li><strong>Authenticators</strong> - Verify user credentials</li>
 *   <li><strong>Authorizers</strong> - Control access to resources</li>
 *   <li><strong>Token Managers</strong> - Handle authentication tokens</li>
 *   <li><strong>Initializers</strong> - Perform startup initialization tasks</li>
 *   <li><strong>Providers</strong> - Supply dependency injection services</li>
 * </ul>
 * </p>
 * <p>
 * Plugin implementations are typically annotated with {@link RegisterPlugin} to provide
 * metadata about their configuration, dependencies, and registration parameters. The
 * RESTHeart plugin system uses this interface for type safety and to ensure consistent
 * plugin handling across the framework.
 * </p>
 * <p>
 * <strong>Plugin Lifecycle:</strong>
 * <ol>
 *   <li>Discovery - Plugins are discovered through classpath scanning or explicit registration</li>
 *   <li>Instantiation - Plugin instances are created by the framework</li>
 *   <li>Configuration - Plugin configuration is injected if the plugin implements {@link ConfigurablePlugin}</li>
 *   <li>Initialization - Plugins are initialized through dependency injection and initialization hooks</li>
 *   <li>Runtime - Plugins are invoked during request processing based on their type and configuration</li>
 * </ol>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see RegisterPlugin
 * @see ConfigurablePlugin
 * @see Service
 * @see Interceptor
 * @see org.restheart.plugins.security.Authenticator
 * @see org.restheart.plugins.security.Authorizer
 * @see org.restheart.plugins.security.TokenManager
 * @see Initializer
 * @see Provider
 */
public interface Plugin {
}
