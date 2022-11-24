/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2022 SoftInstigate
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
package org.restheart;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface ConfigurationKeys {
    /**
     * default instance name
     */
    public static final String DEFAULT_INSTANCE_NAME = "default";

    /**
     * the key for the log-file-path property.
     */
    public static final String PLUGINS_DIRECTORY_PATH_KEY = "plugins-directory";

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
     * the key for the requests-log-level property.
     */
    public static final String REQUESTS_LOG_LEVEL_KEY = "requests-log-level";

    /**
     * the key for the requests-log-tracing-headers property.
     */
    public static final String REQUESTS_LOG_TRACE_HEADERS_KEY = "requests-log-trace-headers";

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
     * the key for the auth-db property.
     */
    public static final String MONGO_AUTH_DB_KEY = "auth-db";

    /**
     * the key for the instance-name property.
     */
    public static final String INSTANCE_NAME_KEY = "instance-name";

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
     * See
     * http://undertow.io/undertow-docs/undertow-docs-1.3.0/index.html#common-listener-optionshttp://undertow.io/undertow-docs/undertow-docs-1.3.0/index.html#common-listener-options
     */
    public static final String CONNECTION_OPTIONS_KEY = "connection-options";
}
