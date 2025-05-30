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

import static org.restheart.configuration.Utils.getOrDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.restheart.configuration.Utils.asListOfMaps;

/**
 * Configuration for proxied resources (reverse proxy).
 * 
 * <p>This record represents the configuration for reverse proxy endpoints that forward
 * requests from RESTHeart to backend services. Each proxied resource defines a location
 * pattern and one or more backend URLs to forward matching requests to.</p>
 * 
 * <h2>Configuration Structure</h2>
 * <p>In the configuration file, proxied resources are configured as:</p>
 * <pre>{@code
 * proxies:
 *   - name: "backend-api"
 *     location: "/api"
 *     proxy-pass:
 *       - "http://backend1:8080/api"
 *       - "http://backend2:8080/api"
 *     rewrite-host-header: true
 *     connections-per-thread: 10
 *     max-queue-size: 100
 * }</pre>
 * 
 * <h2>Load Balancing</h2>
 * <p>When multiple proxy-pass URLs are configured, RESTHeart will load balance
 * requests across them using a round-robin algorithm.</p>
 * 
 * <h2>Connection Pooling</h2>
 * <p>The proxy maintains a connection pool to backend services for efficiency.
 * Pool parameters can be tuned based on expected load:</p>
 * <ul>
 *   <li>connections-per-thread: Max connections per I/O thread</li>
 *   <li>soft-max-connections-per-thread: Soft limit before creating new connections</li>
 *   <li>connections-ttl: Time-to-live for idle connections (seconds)</li>
 * </ul>
 * 
 * @param name a descriptive name for this proxy configuration
 * @param location the URL path pattern to match for proxying
 * @param proxyPass list of backend URLs to forward requests to
 * @param rewriteHostHeader if true, rewrites the Host header to match the backend
 * @param connectionPerThread maximum connections per I/O thread
 * @param maxQueueSize maximum number of queued requests (0 for unlimited)
 * @param softMaxConnectionsPerThread soft limit for connections per thread
 * @param connectionsTTL time-to-live for idle connections in seconds (-1 for no limit)
 * @param problemServerRetry seconds to wait before retrying a failed backend
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 1.0
 */
public record ProxiedResource(String name,
    String location,
    List<String> proxyPass,
    boolean rewriteHostHeader,
    int connectionPerThread,
    int maxQueueSize,
    int softMaxConnectionsPerThread,
    int connectionsTTL,
    int problemServerRetry) {

    /**
     * Configuration key for the proxies list in the main configuration.
     */
    public static final String PROXIED_RESOURCES_KEY = "proxies";

    /**
     * Configuration key for the proxy name.
     */
    public static final String PROXY_NAME = "name";
    
    /**
     * Configuration key for the location pattern to proxy.
     */
    public static final String PROXY_LOCATION_KEY = "location";
    
    /**
     * Configuration key for the backend URLs to proxy to.
     */
    public static final String PROXY_PASS_KEY = "proxy-pass";

    /**
     * Configuration key for rewriting the Host header.
     */
    public static final String PROXY_REWRITE_HOST_HEADER = "rewrite-host-header";
    
    /**
     * Configuration key for connections per thread limit.
     */
    public static final String PROXY_CONNECTIONS_PER_THREAD = "connections-per-thread";
    
    /**
     * Configuration key for maximum queue size.
     */
    public static final String PROXY_MAX_QUEUE_SIZE = "max-queue-size";
    
    /**
     * Configuration key for soft maximum connections per thread.
     */
    public static final String PROXY_SOFT_MAX_CONNECTIONS_PER_THREAD = "soft-max-connections-per-thread";
    
    /**
     * Configuration key for connection time-to-live.
     */
    public static final String PROXY_TTL = "connections-ttl";
    
    /**
     * Configuration key for problem server retry delay.
     */
    public static final String PROXY_PROBLEM_SERVER_RETRY = "problem-server-retry";

    /**
     * Creates a ProxiedResource from a configuration map.
     * 
     * <p>This constructor extracts proxy configuration values from the provided map.
     * Required fields are name, location, and proxy-pass. Other fields use sensible
     * defaults if not specified.</p>
     * 
     * @param conf the configuration map containing proxy settings
     * @param silent if true, suppresses warning messages for missing optional properties
     */
    public ProxiedResource(Map<String, Object> conf, boolean silent) {
        this(getOrDefault(conf, PROXY_NAME, null, silent),
            getOrDefault(conf, PROXY_LOCATION_KEY, null, silent),
            _proxyPass(conf, silent),
            // following are optional parameter, so get them always in silent mode
            getOrDefault(conf, PROXY_REWRITE_HOST_HEADER, true, true),
            getOrDefault(conf, PROXY_CONNECTIONS_PER_THREAD, 10, true),
            getOrDefault(conf, PROXY_MAX_QUEUE_SIZE, 0, true),
            getOrDefault(conf, PROXY_SOFT_MAX_CONNECTIONS_PER_THREAD, 5, true),
            getOrDefault(conf, PROXY_TTL, -1, true),
            getOrDefault(conf, PROXY_PROBLEM_SERVER_RETRY, 10, true));
    }

    /**
     * Parses the proxy-pass configuration value.
     * 
     * <p>The proxy-pass can be specified as either a single string or a list of strings.
     * This method normalizes both formats into a list and validates the values.</p>
     * 
     * @param conf the configuration map
     * @param silent if true, suppresses warning messages
     * @return list of backend URLs, empty list if invalid or missing
     */
    private static List<String> _proxyPass(Map<String, Object> conf, boolean silent) {
        var _proxyPass = getOrDefault(conf, PROXY_PASS_KEY, null, silent);

        if (_proxyPass == null) {
            return new ArrayList<String>();
        } else if (_proxyPass instanceof String s) {
            var ret = new ArrayList<String>();
            ret.add(s);
            return ret;
        } else if (_proxyPass instanceof List<?> l) {
            l.stream().filter(p -> !(p instanceof String))
                    .forEach(ip -> Configuration.LOGGER.warn("Invalid proxy-pass {} ", ip));
            return l.stream().filter(p -> p instanceof String).map(p -> (String) p).collect(Collectors.toList());
        } else {
            Configuration.LOGGER.warn("Invalid proxy-pass value {}", _proxyPass);
            return new ArrayList<String>();
        }
    }

    /**
     * Builds a list of ProxiedResource configurations from the main configuration map.
     * 
     * <p>This method looks for the {@code proxies} section in the configuration map,
     * which should contain a list of proxy configurations. Each proxy configuration
     * is validated and converted into a ProxiedResource instance.</p>
     * 
     * @param conf the main configuration map
     * @param silent if true, suppresses warning messages for missing optional properties
     * @return list of configured proxied resources, empty list if none configured
     */
    public static List<ProxiedResource> build(Map<String, Object> conf, boolean silent) {
        var proxies = asListOfMaps(conf, PROXIED_RESOURCES_KEY, null, silent);

        if (proxies != null) {
            return proxies.stream().map(p -> new ProxiedResource(p, silent)).collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }
}
