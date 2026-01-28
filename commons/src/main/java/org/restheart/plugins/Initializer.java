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

/**
 * Interface for implementing RESTHeart initializers that execute setup logic during server startup.
 * <p>
 * Initializers are plugins that perform one-time setup operations during the RESTHeart
 * startup sequence. They are essential for preparing the runtime environment, setting up
 * resources, and performing any required configuration before the server begins handling
 * requests. Unlike services or interceptors, initializers are not invoked during request
 * processing but only during the server lifecycle.
 * </p>
 * <p>
 * Common initializer use cases include:
 * <ul>
 *   <li><strong>Database Setup</strong> - Creating collections, indexes, or initial data</li>
 *   <li><strong>Schema Validation</strong> - Ensuring database schemas match application requirements</li>
 *   <li><strong>External Service Integration</strong> - Establishing connections to external APIs</li>
 *   <li><strong>Configuration Validation</strong> - Verifying that external resources are accessible</li>
 *   <li><strong>Cache Initialization</strong> - Pre-loading frequently accessed data</li>
 *   <li><strong>Security Setup</strong> - Initializing certificates, keys, or security providers</li>
 *   <li><strong>Plugin Coordination</strong> - Setting up dependencies between multiple plugins</li>
 * </ul>
 * </p>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "databaseInitializer",
 *     description = "Sets up database collections and indexes",
 *     initPoint = InitPoint.BEFORE_STARTUP
 * )
 * public class DatabaseInitializer implements Initializer {
 *     &#64;Inject("mongo-client")
 *     private MongoClient mongoClient;
 *     
 *     &#64;Inject("config")
 *     private Map&lt;String, Object&gt; config;
 *     
 *     &#64;Override
 *     public void init() {
 *         MongoDatabase db = mongoClient.getDatabase("myapp");
 *         
 *         // Create collections if they don't exist
 *         db.createCollection("users");
 *         db.createCollection("sessions");
 *         
 *         // Create indexes
 *         db.getCollection("users").createIndex(Indexes.ascending("email"));
 *         db.getCollection("sessions").createIndex(Indexes.ascending("userId"));
 *         
 *         LOGGER.info("Database initialization completed");
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Execution Timing:</strong><br>
 * The timing of initializer execution is controlled by the {@code initPoint} parameter
 * in the {@link RegisterPlugin} annotation:
 * <ul>
 *   <li><strong>BEFORE_STARTUP</strong> - Execute before the HTTP server starts (default)</li>
 *   <li><strong>AFTER_STARTUP</strong> - Execute after the HTTP server has started</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Error Handling:</strong><br>
 * If an initializer throws an exception during execution:
 * <ul>
 *   <li>BEFORE_STARTUP initializers will prevent server startup</li>
 *   <li>AFTER_STARTUP initializers will log the error but won't stop the server</li>
 * </ul>
 * Therefore, critical setup logic should use BEFORE_STARTUP, while optional
 * setup can use AFTER_STARTUP.
 * </p>
 * <p>
 * <strong>Configuration Access:</strong><br>
 * Initializers can access their configuration through dependency injection:
 * <pre>
 * &#64;Inject("config")
 * private Map&lt;String, Object&gt; config;
 * </pre>
 * This provides access to plugin-specific configuration from the RESTHeart
 * configuration file.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see InitPoint
 * @see RegisterPlugin
 * @see ConfigurablePlugin
 * @see Inject
 * @see https://restheart.org/docs/plugins/core-plugins/#initializers
 */
public interface Initializer extends ConfigurablePlugin {
    /**
     * Performs the initialization logic for this initializer.
     * <p>
     * This method is called exactly once during the RESTHeart startup sequence
     * at the time specified by the {@code initPoint} parameter in the plugin's
     * {@link RegisterPlugin} annotation. The method should contain all the
     * setup logic required for this initializer's functionality.
     * </p>
     * <p>
     * <strong>Implementation Guidelines:</strong>
     * <ul>
     *   <li>Keep initialization logic focused and atomic</li>
     *   <li>Use appropriate logging to track initialization progress</li>
     *   <li>Handle errors gracefully and provide meaningful error messages</li>
     *   <li>Avoid blocking operations if using BEFORE_STARTUP timing</li>
     *   <li>Check for resource availability before attempting setup</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Available Resources:</strong><br>
     * At the time this method is called, the following are available:
     * <ul>
     *   <li>RESTHeart configuration is fully loaded</li>
     *   <li>All plugins have been instantiated and configured</li>
     *   <li>Dependency injection is complete (injected fields are populated)</li>
     *   <li>Database connections and other core services are established</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Exception Handling:</strong><br>
     * If this method throws an exception:
     * <ul>
     *   <li>For BEFORE_STARTUP: RESTHeart startup will be aborted</li>
     *   <li>For AFTER_STARTUP: The error will be logged but startup continues</li>
     * </ul>
     * Therefore, only throw exceptions for truly critical failures that should
     * prevent the server from starting.
     * </p>
     * <p>
     * Example implementation:
     * <pre>
     * &#64;Override
     * public void init() {
     *     try {
     *         // Perform initialization tasks
     *         setupDatabase();
     *         validateConfiguration();
     *         initializeCache();
     *         
     *         LOGGER.info("Initializer {} completed successfully", getClass().getSimpleName());
     *     } catch (Exception e) {
     *         LOGGER.error("Initialization failed", e);
     *         throw new RuntimeException("Critical initialization failure", e);
     *     }
     * }
     * </pre>
     * </p>
     *
     * @throws RuntimeException if a critical error occurs that should prevent server startup
     */
    public void init();
}
