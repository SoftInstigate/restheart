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
package org.restheart.plugins;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that should be called during plugin initialization.
 * <p>
 * The {@code @OnInit} annotation is used to mark methods in plugin classes that should
 * be automatically invoked by the RESTHeart framework after the plugin has been
 * instantiated and all dependency injection has been completed. This provides a
 * clean way to perform initialization logic without implementing specific interfaces.
 * </p>
 * <p>
 * Methods annotated with {@code @OnInit} are called during the plugin initialization
 * phase, which occurs after:
 * <ul>
 *   <li>Plugin instantiation</li>
 *   <li>Dependency injection (fields marked with {@link Inject})</li>
 *   <li>Configuration injection for {@link ConfigurablePlugin} implementations</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Method Requirements:</strong>
 * <ul>
 *   <li>Must be public or package-private</li>
 *   <li>Must not take any parameters</li>
 *   <li>Must not be static</li>
 *   <li>Return type is ignored (can be void or any type)</li>
 * </ul>
 * </p>
 * <p>
 * Common use cases for {@code @OnInit} methods:
 * <ul>
 *   <li><strong>Resource Initialization</strong> - Setting up connections, caches, or resources</li>
 *   <li><strong>Configuration Validation</strong> - Validating injected configuration parameters</li>
 *   <li><strong>Derived State Setup</strong> - Computing derived values from injected dependencies</li>
 *   <li><strong>Registration Tasks</strong> - Registering with external services or frameworks</li>
 *   <li><strong>Logging and Diagnostics</strong> - Recording successful plugin initialization</li>
 * </ul>
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * &#64;RegisterPlugin(name = "myService", description = "Example service")
 * public class MyService implements Service&lt;JsonRequest, JsonResponse&gt; {
 *     &#64;Inject("config")
 *     private Map&lt;String, Object&gt; config;
 *     
 *     &#64;Inject("mongo-client")
 *     private MongoClient mongoClient;
 *     
 *     private MongoDatabase database;
 *     private String apiKey;
 *     
 *     &#64;OnInit
 *     public void initialize() {
 *         // Validate configuration
 *         this.apiKey = (String) config.get("apiKey");
 *         if (apiKey == null || apiKey.isEmpty()) {
 *             throw new IllegalArgumentException("API key is required");
 *         }
 *         
 *         // Setup database connection
 *         String dbName = (String) config.get("database");
 *         this.database = mongoClient.getDatabase(dbName);
 *         
 *         LOGGER.info("MyService initialized with database: {}", dbName);
 *     }
 *     
 *     // Service implementation methods...
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Multiple {@code @OnInit} Methods:</strong><br>
 * A plugin class can have multiple methods annotated with {@code @OnInit}. They will
 * be called in an unspecified order, so initialization methods should not depend on
 * each other. If ordering is important, use a single {@code @OnInit} method that
 * calls other initialization methods in the required sequence.
 * </p>
 * <p>
 * <strong>Error Handling:</strong><br>
 * If an {@code @OnInit} method throws an exception, the plugin initialization will
 * fail and the plugin will not be registered. This allows plugins to fail fast if
 * their initialization requirements are not met.
 * </p>
 * <p>
 * <strong>Inheritance:</strong><br>
 * {@code @OnInit} methods in parent classes are also called during initialization.
 * If both a parent and child class have {@code @OnInit} methods, both will be invoked.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Inject
 * @see ConfigurablePlugin
 * @see RegisterPlugin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnInit {
}
