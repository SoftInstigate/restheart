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

/**
 * Provides configuration management capabilities for RESTHeart.
 * 
 * <p>This package contains classes responsible for loading, parsing, and managing
 * RESTHeart's configuration from YAML or JSON files. The configuration system
 * supports environment variable overrides, default values, and validation of
 * configuration parameters.</p>
 * 
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.restheart.configuration.Configuration} - The main configuration
 *       holder that provides access to all configuration settings</li>
 *   <li>{@link org.restheart.configuration.CoreModule} - Core RESTHeart settings
 *       including plugin management, threading, and buffer configuration</li>
 *   <li>{@link org.restheart.configuration.Listener} - HTTP and AJP listener
 *       configuration</li>
 *   <li>{@link org.restheart.configuration.TLSListener} - HTTPS listener
 *       configuration with TLS/SSL support</li>
 *   <li>{@link org.restheart.configuration.ProxiedResource} - Configuration for
 *       proxied resources and reverse proxy settings</li>
 *   <li>{@link org.restheart.configuration.StaticResource} - Static file serving
 *       configuration</li>
 *   <li>{@link org.restheart.configuration.Logging} - Logging configuration
 *       including log levels and output formats</li>
 * </ul>
 * 
 * <h2>Configuration Loading</h2>
 * <p>Configuration can be loaded from:</p>
 * <ul>
 *   <li>YAML files (preferred format)</li>
 *   <li>JSON files</li>
 *   <li>Environment variables (for overriding specific values)</li>
 *   <li>System properties</li>
 * </ul>
 * 
 * <h2>Environment Variable Overrides</h2>
 * <p>Configuration values can be overridden using environment variables following
 * the pattern: {@code RH_<SECTION>_<KEY>}. For example:</p>
 * <ul>
 *   <li>{@code RH_CORE_NAME} - overrides the instance name</li>
 *   <li>{@code RH_HTTP_LISTENER_PORT} - overrides the HTTP port</li>
 * </ul>
 * 
 * <h2>Example Configuration</h2>
 * <pre>{@code
 * core:
 *   name: "my-restheart"
 *   plugins-directory: "./plugins"
 *   io-threads: 4
 * 
 * http-listener:
 *   enabled: true
 *   host: "0.0.0.0"
 *   port: 8080
 * 
 * https-listener:
 *   enabled: true
 *   host: "0.0.0.0"
 *   port: 4443
 *   keystore-path: "./keystore.jks"
 *   keystore-password: "password"
 * }</pre>
 * 
 * @since 1.0
 * @see org.restheart.configuration.Configuration
 */
package org.restheart.configuration;