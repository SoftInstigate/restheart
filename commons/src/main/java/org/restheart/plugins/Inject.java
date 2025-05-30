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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for dependency injection in RESTHeart plugins.
 * <p>
 * The {@code @Inject} annotation is used to mark fields in plugin classes that should
 * be automatically populated by the RESTHeart dependency injection system. This annotation
 * enables plugins to access various framework services, configuration objects, and other
 * dependencies without manually instantiating or looking them up.
 * </p>
 * <p>
 * Supported injection targets include:
 * <ul>
 *   <li><strong>"config"</strong> - Plugin configuration arguments from restheart.yml</li>
 *   <li><strong>"rh-config"</strong> - Complete RESTHeart configuration object</li>
 *   <li><strong>"mongo-client"</strong> - MongoDB client instance</li>
 *   <li><strong>"mclient"</strong> - Alternative MongoDB client reference</li>
 *   <li><strong>Custom providers</strong> - User-defined dependency providers</li>
 * </ul>
 * </p>
 * <p>
 * Common usage patterns:
 * <pre>
 * &#64;RegisterPlugin(name = "myPlugin", description = "Example plugin")
 * public class MyPlugin implements Service&lt;JsonRequest, JsonResponse&gt; {
 *     
 *     &#64;Inject("config")
 *     private Map&lt;String, Object&gt; config;
 *     
 *     &#64;Inject("mongo-client")
 *     private MongoClient mongoClient;
 *     
 *     &#64;Inject("rh-config")
 *     private Configuration rhConfig;
 *     
 *     // Plugin implementation...
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Configuration Injection:</strong><br>
 * When injecting "config", the field receives a Map containing the plugin's
 * configuration arguments as specified in the RESTHeart configuration file:
 * <pre>
 * plugins-args:
 *   myPlugin:
 *     apiKey: "secret"
 *     timeout: 30
 *     enabled: true
 * </pre>
 * </p>
 * <p>
 * <strong>Injection Lifecycle:</strong>
 * <ol>
 *   <li>Plugin class is instantiated</li>
 *   <li>Fields marked with {@code @Inject} are identified</li>
 *   <li>Appropriate dependencies are resolved from providers</li>
 *   <li>Dependencies are injected into the annotated fields</li>
 *   <li>Plugin initialization methods are called</li>
 * </ol>
 * </p>
 * <p>
 * <strong>Custom Providers:</strong><br>
 * Plugins can define custom dependency providers by implementing the
 * {@link Provider} interface and registering them with RESTHeart. The injection
 * value should match the provider's registered name.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Provider
 * @see ConfigurablePlugin
 * @see RegisterPlugin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Inject {
    /**
     * Specifies the name of the dependency to inject.
     * <p>
     * This value identifies which dependency provider should be used to populate
     * the annotated field. The value must match either a built-in injection target
     * (like "config", "mongo-client") or a custom provider name registered with
     * the RESTHeart dependency injection system.
     * </p>
     * <p>
     * Built-in injection targets:
     * <ul>
     *   <li><strong>"config"</strong> - Plugin configuration Map from restheart.yml</li>
     *   <li><strong>"rh-config"</strong> - Complete RESTHeart Configuration object</li>
     *   <li><strong>"mongo-client"</strong> - MongoDB client instance</li>
     *   <li><strong>"mclient"</strong> - Alternative MongoDB client reference</li>
     * </ul>
     * </p>
     * <p>
     * For custom providers, this value should match the name used when registering
     * the provider with the dependency injection system.
     * </p>
     *
     * @return the name of the dependency to inject into the annotated field
     */
    String value();
}
