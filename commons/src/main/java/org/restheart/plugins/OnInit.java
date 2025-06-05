/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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
 * Marks a method to be called during plugin initialization.
 * 
 * <p>The {@code @OnInit} annotation identifies methods that should be invoked after a plugin
 * is instantiated and all dependencies have been injected via {@link Inject}. This provides
 * a hook for plugins to perform initialization logic that requires access to injected
 * dependencies.</p>
 * 
 * <h2>Execution Order</h2>
 * <p>The plugin initialization lifecycle follows this sequence:</p>
 * <ol>
 *   <li>Plugin class is instantiated via default constructor</li>
 *   <li>Fields marked with {@link Inject} are populated</li>
 *   <li>Methods marked with {@code @OnInit} are called</li>
 *   <li>Plugin is registered and ready for use</li>
 * </ol>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @RegisterPlugin(name = "data-service")
 * public class DataService implements JsonService {
 *     @Inject("config")
 *     private Map<String, Object> config;
 *     
 *     @Inject("mclient")
 *     private MongoClient mongoClient;
 *     
 *     private MongoDatabase database;
 *     private boolean cacheEnabled;
 *     
 *     @OnInit
 *     public void initialize() {
 *         // Access injected configuration
 *         String dbName = config.get("database").toString();
 *         cacheEnabled = (Boolean) config.getOrDefault("cache", true);
 *         
 *         // Initialize using injected dependencies
 *         database = mongoClient.getDatabase(dbName);
 *         
 *         // Perform other initialization tasks
 *         validateConfiguration();
 *         setupCache();
 *         logger.info("DataService initialized with database: {}", dbName);
 *     }
 *     
 *     @OnInit
 *     public void validateConfiguration() {
 *         // Multiple @OnInit methods are supported
 *         if (!config.containsKey("database")) {
 *             throw new IllegalStateException("database configuration required");
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <h2>Method Requirements</h2>
 * <p>Methods annotated with {@code @OnInit} must:</p>
 * <ul>
 *   <li>Be public</li>
 *   <li>Have no parameters</li>
 *   <li>Return void</li>
 *   <li>Not be static</li>
 * </ul>
 * 
 * <h2>Multiple Init Methods</h2>
 * <p>A plugin can have multiple methods annotated with {@code @OnInit}. They will be
 * called in the order they are declared in the class. This allows for modular
 * initialization logic.</p>
 * 
 * <h2>Error Handling</h2>
 * <p>If an {@code @OnInit} method throws an exception:</p>
 * <ul>
 *   <li>The exception is logged with details</li>
 *   <li>Plugin initialization is aborted</li>
 *   <li>The plugin is not registered</li>
 *   <li>RESTHeart continues to start (unless critical)</li>
 * </ul>
 * 
 * <h2>Best Practices</h2>
 * <ul>
 *   <li>Keep initialization logic focused and fast</li>
 *   <li>Validate configuration and fail fast if invalid</li>
 *   <li>Log initialization progress for debugging</li>
 *   <li>Handle exceptions gracefully</li>
 *   <li>Avoid blocking operations when possible</li>
 *   <li>Clean up resources if initialization fails</li>
 * </ul>
 * 
 * @see Inject
 * @see RegisterPlugin
 * @see Plugin
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnInit {
}
