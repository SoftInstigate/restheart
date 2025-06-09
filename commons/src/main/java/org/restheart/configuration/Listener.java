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
package org.restheart.configuration;

import static org.restheart.configuration.Utils.findOrDefault;
import java.util.Map;

/**
 * Configuration for HTTP and AJP protocol listeners.
 * 
 * <p>This record represents the configuration for network listeners that handle
 * incoming HTTP or AJP (Apache JServ Protocol) connections. Each listener can be
 * independently enabled or disabled and configured with specific host and port
 * bindings.</p>
 * 
 * <h2>Configuration Structure</h2>
 * <p>In the configuration file, listeners are configured as:</p>
 * <pre>{@code
 * http-listener:
 *   enabled: true
 *   host: "0.0.0.0"
 *   port: 8080
 * 
 * ajp-listener:
 *   enabled: false
 *   host: "localhost"
 *   port: 8009
 * }</pre>
 * 
 * <h2>Host Binding</h2>
 * <p>The host parameter controls which network interfaces the listener binds to:</p>
 * <ul>
 *   <li>{@code "localhost"} or {@code "127.0.0.1"} - only local connections</li>
 *   <li>{@code "0.0.0.0"} - all available interfaces (use with caution)</li>
 *   <li>Specific IP address - binds to that interface only</li>
 * </ul>
 * 
 * @param enabled whether this listener should be activated
 * @param host the hostname or IP address to bind to
 * @param port the port number to listen on
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 1.0
 */
public record Listener(boolean enabled, String host, int port) {
    /**
     * Configuration key for the HTTP listener section.
     */
    public static final String HTTP_LISTENER_KEY = "http-listener";
    
    /**
     * Configuration key for the AJP listener section.
     */
    public static final String AJP_LISTENER_KEY = "ajp-listener";
    
    /**
     * Configuration key for enabling/disabling the listener.
     */
    public static final String ENABLED_KEY = "enabled";
    
    /**
     * Configuration key for the host/IP binding.
     */
    public static final String HOST_KEY = "host";
    
    /**
     * Configuration key for the port number.
     */
    public static final String PORT_KEY = "port";

    /**
     * Creates a Listener from a configuration map.
     * 
     * <p>This constructor extracts listener configuration values from the provided map
     * under the specified listener key (e.g., "http-listener" or "ajp-listener").
     * If any values are missing, the corresponding values from the defaultValue
     * parameter are used.</p>
     * 
     * @param conf the main configuration map
     * @param listenerKey the key for this listener section (e.g., "http-listener")
     * @param defaultValue default values to use for missing configuration
     * @param silent if true, suppresses warning messages for missing properties
     */
    public Listener(Map<String, Object> conf, String listenerKey, Listener defaultValue, boolean silent) {
        this(findOrDefault(conf, "/" + listenerKey + "/" + ENABLED_KEY, defaultValue.enabled(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" + HOST_KEY, defaultValue.host(), silent),
            findOrDefault(conf, "/" + listenerKey + "/" + PORT_KEY, defaultValue.port(), silent));
    }
}
