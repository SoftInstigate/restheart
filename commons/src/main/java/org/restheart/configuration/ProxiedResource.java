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

public record ProxiedResource(String name,
    String location,
    List<String> proxyPass,
    boolean rewriteHostHeader,
    int connectionPerThread,
    int maxQueueSize,
    int softMaxConnectionsPerThread,
    int connectionsTTL,
    int problemServerRetry) {

    public static final String PROXIED_RESOURCES_KEY = "proxies";

    public static final String PROXY_NAME = "name";
    public static final String PROXY_LOCATION_KEY = "location";
    public static final String PROXY_PASS_KEY = "proxy-pass";

    public static final String PROXY_REWRITE_HOST_HEADER = "rewrite-host-header";
    public static final String PROXY_CONNECTIONS_PER_THREAD = "connections-per-thread";
    public static final String PROXY_MAX_QUEUE_SIZE = "max-queue-size";
    public static final String PROXY_SOFT_MAX_CONNECTIONS_PER_THREAD = "soft-max-connections-per-thread";
    public static final String PROXY_TTL = "connections-ttl";
    public static final String PROXY_PROBLEM_SERVER_RETRY = "problem-server-retry";

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

    public static List<ProxiedResource> build(Map<String, Object> conf, boolean silent) {
        var proxies = asListOfMaps(conf, PROXIED_RESOURCES_KEY, null, silent);

        if (proxies != null) {
            return proxies.stream().map(p -> new ProxiedResource(p, silent)).collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }
}
