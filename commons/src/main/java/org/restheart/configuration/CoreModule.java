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
package org.restheart.configuration;

import static org.restheart.configuration.Utils.getOrDefault;
import static org.restheart.configuration.Utils.asMap;
import java.util.Map;

public record CoreModule(String name,
        String pluginsDirectory,
        String baseUrl,
        int ioThreads,
        int workerThreads,
        int requestsLimit,
        int bufferSize,
        boolean directBuffers,
        boolean forceGzipEncoding,
        boolean allowUnescapedCharsInUrl) {

    public static final String CORE_KEY = "core";
    public static final String INSTANCE_NAME_KEY = "name";
    public static final String PLUGINS_DIRECTORY_PATH_KEY = "plugins-directory";
    public static final String BASE_URL_KEY = "base-url";
    public static final String IO_THREADS_KEY = "io-threads";
    public static final String WORKER_THREADS_KEY = "worker-threads";
    public static final String REQUESTS_LIMIT_KEY = "requests-limit";
    public static final String BUFFER_SIZE_KEY = "buffer-size";
    public static final String DIRECT_BUFFERS_KEY = "direct-buffers";
    public static final String FORCE_GZIP_ENCODING_KEY = "force-gzip-encoding";
    public static final String ALLOW_UNESCAPED_CHARS_IN_ULR_KEY = "allow-unescaped-characters-in-url";

    private static final CoreModule DEFAULT_CORE_MODULE = new CoreModule("default", "plugins", null, 0, -1, 1000, 16364, true, false, true);

    public CoreModule(Map<String, Object> conf, boolean silent) {
        this(
                getOrDefault(conf, INSTANCE_NAME_KEY, DEFAULT_CORE_MODULE.name(), silent),
                getOrDefault(conf, PLUGINS_DIRECTORY_PATH_KEY, DEFAULT_CORE_MODULE.pluginsDirectory(), silent),
                // following is optional, so get it always in silent mode
                getOrDefault(conf, BASE_URL_KEY, DEFAULT_CORE_MODULE.baseUrl(), true),
                getOrDefault(conf, IO_THREADS_KEY, DEFAULT_CORE_MODULE.ioThreads(), silent),
                getOrDefault(conf, WORKER_THREADS_KEY, DEFAULT_CORE_MODULE.workerThreads(), silent),
                getOrDefault(conf, REQUESTS_LIMIT_KEY, DEFAULT_CORE_MODULE.requestsLimit(), silent),
                getOrDefault(conf, BUFFER_SIZE_KEY, DEFAULT_CORE_MODULE.bufferSize(), silent),
                getOrDefault(conf, DIRECT_BUFFERS_KEY, DEFAULT_CORE_MODULE.directBuffers(), silent),
                // following is optional, so get it always in silent mode
                getOrDefault(conf, FORCE_GZIP_ENCODING_KEY, DEFAULT_CORE_MODULE.forceGzipEncoding(), true),
                // following is optional, so get it always in silent mode
                getOrDefault(conf, ALLOW_UNESCAPED_CHARS_IN_ULR_KEY, DEFAULT_CORE_MODULE.allowUnescapedCharsInUrl(),
                        true));
    }

    public static CoreModule build(Map<String, Object> conf, boolean silent) {
        var core = asMap(conf, CORE_KEY, null, silent);

        if (core != null) {
            return new CoreModule(core, silent);
        } else {
            return DEFAULT_CORE_MODULE;
        }
    }
}