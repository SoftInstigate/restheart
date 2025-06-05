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

/**
 * Defines the execution time point for {@link Initializer} plugins.
 * 
 * <p>InitPoint determines when an initializer plugin will be executed during the
 * RESTHeart startup process. This allows plugins to perform initialization tasks
 * at specific points in the application lifecycle.</p>
 * 
 * <h2>Purpose</h2>
 * <p>Different initialization tasks require execution at different stages:</p>
 * <ul>
 *   <li>Some tasks must complete before the server starts accepting requests</li>
 *   <li>Others need the server to be running (e.g., to register with external services)</li>
 *   <li>This enum provides precise control over initialization timing</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "database-migrator",
 *     description = "Runs database migrations before server startup"
 * )
 * public class DatabaseMigrator implements Initializer {
 *     @Override
 *     public void init() {
 *         // Perform database migrations
 *         runMigrations();
 *     }
 *     
 *     @Override
 *     public InitPoint initPoint() {
 *         return InitPoint.BEFORE_STARTUP;
 *     }
 * }
 * }</pre>
 * 
 * @see Initializer
 * @see RegisterPlugin
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public enum InitPoint {
    /**
     * Execute the initializer before starting the HTTP server.
     * 
     * <p>Use this init point for tasks that must complete before the server
     * begins accepting HTTP requests. This is ideal for:</p>
     * <ul>
     *   <li>Database schema initialization or migrations</li>
     *   <li>Loading required configuration or resources</li>
     *   <li>Validating system prerequisites</li>
     *   <li>Setting up core dependencies</li>
     *   <li>Registering critical services</li>
     * </ul>
     * 
     * <p>If an initializer with BEFORE_STARTUP fails, RESTHeart will abort
     * the startup process to prevent running in an invalid state.</p>
     */
    BEFORE_STARTUP,

    /**
     * Execute the initializer immediately after the HTTP server has started.
     * 
     * <p>Use this init point for tasks that require the server to be running
     * and accepting requests. This is ideal for:</p>
     * <ul>
     *   <li>Registering with service discovery systems</li>
     *   <li>Starting background tasks or schedulers</li>
     *   <li>Warming up caches with HTTP calls</li>
     *   <li>Sending startup notifications</li>
     *   <li>Initializing monitoring or metrics collection</li>
     * </ul>
     * 
     * <p>At this point, all plugins are loaded and the server is fully operational.
     * Failures in AFTER_STARTUP initializers are logged but don't stop the server.</p>
     */
    AFTER_STARTUP
}
