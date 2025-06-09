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
 * Base interface for all RESTHeart plugins.
 *
 * <p>Plugin is the root interface in the RESTHeart plugin hierarchy. All plugin types
 * (services, interceptors, initializers, providers, etc.) extend this interface.
 * It serves as a marker interface that identifies a class as a RESTHeart plugin.</p>
 *
 * <h2>Plugin Types</h2>
 * <p>RESTHeart supports several plugin types, each extending this interface:</p>
 * <ul>
 *   <li>{@link Service} - Handle HTTP requests and generate responses</li>
 *   <li>{@link Interceptor} - Process requests/responses in the pipeline</li>
 *   <li>{@link Initializer} - Perform startup initialization tasks</li>
 *   <li>{@link Provider} - Supply dependencies for injection</li>
 *   <li>{@link org.restheart.plugins.security.AuthMechanism} - Implement authentication mechanisms</li>
 *   <li>{@link org.restheart.plugins.security.Authenticator} - Verify user credentials</li>
 *   <li>{@link org.restheart.plugins.security.Authorizer} - Control access to resources</li>
 *   <li>{@link org.restheart.plugins.security.TokenManager} - Manage authentication tokens</li>
 * </ul>
 *
 * <h2>Plugin Development</h2>
 * <p>To create a plugin:</p>
 * <ol>
 *   <li>Implement one of the specific plugin interfaces</li>
 *   <li>Annotate the class with {@link RegisterPlugin}</li>
 *   <li>Use {@link Inject} for dependency injection</li>
 *   <li>Use {@link OnInit} for initialization logic</li>
 *   <li>Package as a JAR and place in the plugins directory</li>
 * </ol>
 *
 * <h2>Example Plugin</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "hello",
 *     description = "A simple greeting service"
 * )
 * public class HelloService implements JsonService {
 *     @Inject("config")
 *     private Map<String, Object> config;
 *
 *     @OnInit
 *     public void init() {
 *         logger.info("HelloService initialized");
 *     }
 *
 *     @Override
 *     public void handle(JsonRequest request, JsonResponse response) {
 *         response.setContent(new JsonObject()
 *             .put("message", "Hello, World!")
 *             .build());
 *     }
 * }
 * }</pre>
 *
 * <h2>Plugin Lifecycle</h2>
 * <p>Plugins follow this lifecycle:</p>
 * <ol>
 *   <li><strong>Discovery:</strong> Plugin classes are found via classpath scanning</li>
 *   <li><strong>Registration:</strong> Plugins with {@code @RegisterPlugin} are registered</li>
 *   <li><strong>Instantiation:</strong> Plugin instances are created</li>
 *   <li><strong>Injection:</strong> Dependencies marked with {@code @Inject} are injected</li>
 *   <li><strong>Initialization:</strong> Methods marked with {@code @OnInit} are called</li>
 *   <li><strong>Activation:</strong> Plugin becomes available for handling requests</li>
 * </ol>
 *
 * <h2>Plugin Discovery</h2>
 * <p>Plugins are discovered through:</p>
 * <ul>
 *   <li>Classpath scanning for {@code @RegisterPlugin} annotations</li>
 *   <li>JAR files in the plugins directory</li>
 *   <li>Classes in the RESTHeart core</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Plugin instances are singletons and must be thread-safe. Multiple threads may
 * call plugin methods concurrently. Use appropriate synchronization or thread-safe
 * data structures when maintaining state.</p>
 *
 * @see RegisterPlugin
 * @see Service
 * @see Interceptor
 * @see Initializer
 * @see Provider
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Plugin {
}
