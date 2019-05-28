/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security;

import static org.restheart.security.Configuration.DEFAULT_ROUTE;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public interface ConfigurationKeys {
    /**
     * default ajp listener.
     */
    public static final boolean DEFAULT_AJP_LISTENER = false;
    
    /**
     * default ajp host 0.0.0.0.
     */
    public static final String DEFAULT_AJP_HOST = DEFAULT_ROUTE;

    /**
     * default ajp port 8009.
     */
    public static final int DEFAULT_AJP_PORT = 8009;

    
    /**
     * default http listener.
     */
    public static final boolean DEFAULT_HTTP_LISTENER = true;
    
    /**
     * default http host 0.0.0.0.
     */
    public static final String DEFAULT_HTTP_HOST = DEFAULT_ROUTE;

    /**
     * default http port 8080.
     */
    public static final int DEFAULT_HTTP_PORT = 8080;

    /**
     * default https listener.
     */
    public static final boolean DEFAULT_HTTPS_LISTENER = true;
    
    /**
     * default https host 0.0.0.0.
     */
    public static final String DEFAULT_HTTPS_HOST = DEFAULT_ROUTE;

    /**
     * default https port 4443.
     */
    public static final int DEFAULT_HTTPS_PORT = 4443;

    /**
     * default instance name
     */
    public static final String DEFAULT_INSTANCE_NAME = "default";

    /**
     * the key for the services property.
     */
    public static final String SERVICES_KEY = "services";

    /**
     * the key for the args property.
     */
    public static final String ARGS_KEY = "args";

    /**
     * the key for the name property.
     */
    public static final String NAME_KEY = "name";

    /**
     * the key for the uri property.
     */
    public static final String SERVICE_URI_KEY = "uri";

    /**
     * the key for the secured property.
     */
    public static final String SERVICE_SECURED_KEY = "secured";

    /**
     * the key for the local-cache-enabled property.
     */
    public static final String LOCAL_CACHE_ENABLED_KEY = "local-cache-enabled";

    /**
     * the key for the force-gzip-encoding property.
     */
    public static final String FORCE_GZIP_ENCODING_KEY = "force-gzip-encoding";

    /**
     * the key for the direct-buffers property.
     */
    public static final String DIRECT_BUFFERS_KEY = "direct-buffers";

    /**
     * the key for the buffer-size property.
     */
    public static final String BUFFER_SIZE_KEY = "buffer-size";

    /**
     * the key for the worker-threads property.
     */
    public static final String WORKER_THREADS_KEY = "worker-threads";

    /**
     * the key for the io-threads property.
     */
    public static final String IO_THREADS_KEY = "io-threads";

    /**
     * the key for the requests-limit property.
     */
    public static final String REQUESTS_LIMIT_KEY = "requests-limit";

    /**
     * the key for the enable-log-file property.
     */
    public static final String ENABLE_LOG_FILE_KEY = "enable-log-file";

    /**
     * the key for the enable-log-console property.
     */
    public static final String ENABLE_LOG_CONSOLE_KEY = "enable-log-console";

    /**
     * the key for the log-level property.
     */
    public static final String LOG_LEVEL_KEY = "log-level";

    /**
     * the key for the log-file-path property.
     */
    public static final String LOG_FILE_PATH_KEY = "log-file-path";

    /**
     * the key for the class property.
     */
    public static final String CLASS_KEY = "class";

    /**
     * the key for the authorizers property.
     */
    public static final String AUTHORIZERS_KEY = "authorizers";

    /**
     * the key for the authenticators property.
     */
    public static final String AUTHENTICATORS_KEY = "authenticators";

    /**
     * the key for the auth Mechanism.
     */
    public static final String AUTH_MECHANISMS_KEY = "auth-mechanisms";

    /**
     * the key for the proxies property.
     */
    public static final String PROXY_KEY = "proxies";
    
    /**
     * the key for the location property.
     */
    public static final String PROXY_LOCATION_KEY = "location";

    /**
     * the key for the proxy-pass property.
     */
    public static final String PROXY_PASS_KEY = "proxy-pass";
    
    /**
     * the key for the proxy name property.
     */
    public static final String PROXY_NAME = "name";

    /**
     * the key for the rewrite-host-header.
     */
    public static final String PROXY_REWRITE_HOST_HEADER = "rewrite-host-header";

    /**
     * the key for the connections-per-thread property.
     */
    public static final String PROXY_CONNECTIONS_PER_THREAD = "connections-per-thread";

    /**
     * the key for the max-queue-size property.
     */
    public static final String PROXY_MAX_QUEUE_SIZE = "max-queue-size";

    /**
     * the key for the soft-max-connections-per-thread property.
     */
    public static final String PROXY_SOFT_MAX_CONNECTIONS_PER_THREAD = "soft-max-connections-per-thread";

    /**
     * the key for the connections-ttl property.
     */
    public static final String PROXY_TTL = "connections-ttl";

    /**
     * the key for the problem-server-retry property.
     */
    public static final String PROXY_PROBLEM_SERVER_RETRY = "problem-server-retry";

    /**
     * the key for the pluging-args property.
     */
    public static final String PLUGINS_ARGS_KEY = "plugins-args";
    
    /**
     * the key for the auth-db property.
     */
    public static final String MONGO_AUTH_DB_KEY = "auth-db";

    /**
     * the key for the certpassword property.
     */
    public static final String CERT_PASSWORD_KEY = "certpassword";

    /**
     * the key for the keystore-password property.
     */
    public static final String KEYSTORE_PASSWORD_KEY = "keystore-password";

    /**
     * the key for the keystore-file property.
     */
    public static final String KEYSTORE_FILE_KEY = "keystore-file";

    /**
     * the key for the use-embedded-keystore property.
     */
    public static final String USE_EMBEDDED_KEYSTORE_KEY = "use-embedded-keystore";

    /**
     * the key for the ajp-host property.
     */
    public static final String AJP_HOST_KEY = "ajp-host";

    /**
     * the key for the ajp-port property.
     */
    public static final String AJP_PORT_KEY = "ajp-port";

    /**
     * the key for the ajp-listener property.
     */
    public static final String AJP_LISTENER_KEY = "ajp-listener";

    /**
     * the key for the http-host property.
     */
    public static final String HTTP_HOST_KEY = "http-host";

    /**
     * the key for the http-port property.
     */
    public static final String HTTP_PORT_KEY = "http-port";

    /**
     * the key for http-listener the property.
     */
    public static final String HTTP_LISTENER_KEY = "http-listener";

    /**
     * the key for the https-host property.
     */
    public static final String HTTPS_HOST_KEY = "https-host";

    /**
     * the key for the https-port property.
     */
    public static final String HTTPS_PORT_KEY = "https-port";

    /**
     * the key for the https-listener property.
     */
    public static final String HTTPS_LISTENER = "https-listener";

    /**
     * the key for the instance-name property.
     */
    public static final String INSTANCE_NAME_KEY = "instance-name";

    /**
     * the key for the tokenManager property.
     */
    public static final String AUTH_TOKEN = "token-manager";

    /**
     * Force http requests logging even if DEBUG is not set
     */
    public static final String LOG_REQUESTS_LEVEL_KEY = "requests-log-level";

    /**
     * The key for enabling the Ansi console (for logging with colors)
     */
    public static final String ANSI_CONSOLE_KEY = "ansi-console";

    /**
     * The key to allow unescaped chars in URL
     */
    public static final String ALLOW_UNESCAPED_CHARACTERS_IN_URL = "allow-unescaped-characters-in-url";

    /**
     * The key to enable plugins
     */
    public static final String PLUGIN_ENABLED_KEY = "enabled";
    
    /**
     * undertow connetction options
     *
     * @see
     * http://undertow.io/undertow-docs/undertow-docs-1.3.0/index.html#common-listener-optionshttp://undertow.io/undertow-docs/undertow-docs-1.3.0/index.html#common-listener-options
     */
    public static final String CONNECTION_OPTIONS_KEY = "connection-options";
}
