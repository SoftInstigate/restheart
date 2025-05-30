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
 * Enumeration defining when initializers should be executed during RESTHeart startup.
 * <p>
 * InitPoint determines the timing of initializer execution in the RESTHeart lifecycle,
 * allowing plugins to perform initialization tasks at the most appropriate stage of
 * the server startup process. This ensures proper ordering of initialization activities
 * and allows initializers to depend on specific RESTHeart components being available.
 * </p>
 * <p>
 * The RESTHeart startup sequence follows this order:
 * <ol>
 *   <li>Configuration loading and validation</li>
 *   <li>Core framework initialization</li>
 *   <li>Plugin discovery and loading</li>
 *   <li>BEFORE_STARTUP initializers execution</li>
 *   <li>Server binding and HTTP listener startup</li>
 *   <li>AFTER_STARTUP initializers execution</li>
 *   <li>Ready state - server accepting requests</li>
 * </ol>
 * </p>
 * <p>
 * Common use cases for initializers include:
 * <ul>
 *   <li><strong>Database Setup</strong> - Creating indexes, schemas, or initial data</li>
 *   <li><strong>External Service Registration</strong> - Registering with service discovery</li>
 *   <li><strong>Cache Warming</strong> - Pre-loading frequently accessed data</li>
 *   <li><strong>Health Check Setup</strong> - Initializing monitoring and health endpoints</li>
 *   <li><strong>Resource Validation</strong> - Verifying external resource availability</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Initializer
 * @see RegisterPlugin#initPoint()
 */
public enum InitPoint {
    /**
     * Execute the initializer before starting the HTTP server.
     * <p>
     * Initializers with this init point are executed after the RESTHeart framework
     * has been configured and all plugins have been loaded, but before the HTTP
     * server starts listening for requests. This is the appropriate time for setup
     * tasks that must complete before the server becomes available to clients.
     * </p>
     * <p>
     * <strong>Available Resources:</strong>
     * <ul>
     *   <li>RESTHeart configuration is fully loaded</li>
     *   <li>All plugins are instantiated and configured</li>
     *   <li>Database connections are established</li>
     *   <li>Dependency injection is complete</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Use Cases:</strong>
     * <ul>
     *   <li><strong>Database Schema Setup</strong> - Create collections, indexes, or initial data</li>
     *   <li><strong>Configuration Validation</strong> - Verify external resource connectivity</li>
     *   <li><strong>Security Setup</strong> - Initialize security providers or key stores</li>
     *   <li><strong>Cache Initialization</strong> - Pre-populate caches with critical data</li>
     *   <li><strong>Plugin Coordination</strong> - Set up inter-plugin dependencies</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Important Notes:</strong>
     * <ul>
     *   <li>The server is not yet accepting HTTP requests</li>
     *   <li>Initialization failures can prevent server startup</li>
     *   <li>Long-running tasks will delay server startup</li>
     *   <li>Use for critical setup that must complete before serving requests</li>
     * </ul>
     * </p>
     */
    BEFORE_STARTUP,

    /**
     * Execute the initializer after the HTTP server has started.
     * <p>
     * Initializers with this init point are executed after the HTTP server has
     * successfully started and is listening for requests. The server is fully
     * operational at this point, but these initializers can perform additional
     * setup tasks that don't need to block server availability.
     * </p>
     * <p>
     * <strong>Available Resources:</strong>
     * <ul>
     *   <li>HTTP server is listening and accepting requests</li>
     *   <li>All RESTHeart services are operational</li>
     *   <li>All plugins are fully initialized and active</li>
     *   <li>Server is in ready state for client connections</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Use Cases:</strong>
     * <ul>
     *   <li><strong>Service Registration</strong> - Register with external service discovery</li>
     *   <li><strong>Health Check Setup</strong> - Initialize health monitoring endpoints</li>
     *   <li><strong>Background Tasks</strong> - Start background processing or cleanup tasks</li>
     *   <li><strong>Notification Sending</strong> - Announce server availability</li>
     *   <li><strong>Performance Optimization</strong> - Warm up caches or optimize resources</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Important Notes:</strong>
     * <ul>
     *   <li>The server is already accepting HTTP requests</li>
     *   <li>Initialization failures won't prevent the server from running</li>
     *   <li>These tasks run concurrently with request processing</li>
     *   <li>Use for non-critical setup that can happen after server startup</li>
     *   <li>Consider performance impact on early requests</li>
     * </ul>
     * </p>
     */
    AFTER_STARTUP
}
