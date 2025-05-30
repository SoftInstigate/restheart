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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.restheart.configuration.Utils.asMap;
import static org.restheart.configuration.Utils.getOrDefault;

/**
 * Core configuration module containing essential RESTHeart settings.
 * 
 * <p>This record encapsulates the core configuration parameters that control
 * RESTHeart's fundamental behavior including plugin management, threading,
 * buffering, and URL handling.</p>
 * 
 * <h2>Configuration Structure</h2>
 * <p>In the configuration file, these settings are under the {@code core} section:</p>
 * <pre>{@code
 * core:
 *   name: "my-restheart"
 *   plugins-directory: "./plugins"
 *   plugins-packages:
 *     - "org.restheart.plugins"
 *     - "com.example.myplugins"
 *   io-threads: 4
 *   workers-scheduler-parallelism: 8
 *   buffer-size: 16384
 *   direct-buffers: true
 * }</pre>
 * 
 * @param name the instance name for this RESTHeart server
 * @param pluginsDirectory path to the directory containing plugin JARs
 * @param pluginsPackages list of Java packages to scan for plugins
 * @param pluginsScanningVerbose if true, enables verbose logging during plugin scanning
 * @param baseUrl the base URL for this instance (optional, for URL construction)
 * @param ioThreads number of I/O threads (0 for automatic based on CPU cores)
 * @param workersSchedulerParallelism parallelism level for the workers thread pool
 * @param workersSchedulerMaxPoolSize maximum size of the workers thread pool
 * @param buffersPooling if true, enables buffer pooling for better performance
 * @param bufferSize size of I/O buffers in bytes
 * @param directBuffers if true, uses direct (off-heap) buffers
 * @param forceGzipEncoding if true, forces GZIP encoding for all responses
 * @param allowUnescapedCharsInUrl if true, allows unescaped characters in URLs
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 1.0
 */
public record CoreModule(String name,
        String pluginsDirectory,
        List<String> pluginsPackages,
        boolean pluginsScanningVerbose,
        String baseUrl,
        int ioThreads,
        int workersSchedulerParallelism,
        int workersSchedulerMaxPoolSize,
        boolean buffersPooling,
        int bufferSize,
        boolean directBuffers,
        boolean forceGzipEncoding,
        boolean allowUnescapedCharsInUrl) {

    /**
     * Configuration key for the core section in the configuration file.
     */
    public static final String CORE_KEY = "core";
    
    /**
     * Configuration key for the instance name.
     */
    public static final String INSTANCE_NAME_KEY = "name";
    
    /**
     * Configuration key for the plugins directory path.
     */
    public static final String PLUGINS_DIRECTORY_PATH_KEY = "plugins-directory";
    
    /**
     * Configuration key for the list of packages to scan for plugins.
     */
    public static final String PLUGINS_PACKAGES_KEY = "plugins-packages";
    
    /**
     * Configuration key for enabling verbose plugin scanning output.
     */
    public static final String PLUGINS_SCANNING_VERBOSE_KEY = "plugins-scanning-verbose";
    
    /**
     * Configuration key for the base URL of this instance.
     */
    public static final String BASE_URL_KEY = "base-url";
    
    /**
     * Configuration key for the number of I/O threads.
     */
    public static final String IO_THREADS_KEY = "io-threads";
    
    /**
     * Configuration key for workers scheduler parallelism level.
     */
    public static final String WORKERS_SCHEDULER_PARALLELISM_KEY ="workers-scheduler-parallelism";
    
    /**
     * Configuration key for workers scheduler maximum pool size.
     */
    public static final String WORKERS_SCHEDULER_MAX_POOL_SIZE_KEY = "workers-scheduler-max-pool-size";
    
    /**
     * Configuration key for enabling buffer pooling.
     */
    public static final String BUFFERS_POOLING_KEY = "buffers-pooling";
    
    /**
     * Configuration key for the I/O buffer size in bytes.
     */
    public static final String BUFFER_SIZE_KEY = "buffer-size";
    
    /**
     * Configuration key for enabling direct (off-heap) buffers.
     */
    public static final String DIRECT_BUFFERS_KEY = "direct-buffers";
    
    /**
     * Configuration key for forcing GZIP encoding on responses.
     */
    public static final String FORCE_GZIP_ENCODING_KEY = "force-gzip-encoding";
    
    /**
     * Configuration key for allowing unescaped characters in URLs.
     */
    public static final String ALLOW_UNESCAPED_CHARS_IN_ULR_KEY = "allow-unescaped-characters-in-url";

    /**
     * Default CoreModule configuration used when no configuration is provided.
     * 
     * <p>Default values:</p>
     * <ul>
     *   <li>name: "default"</li>
     *   <li>plugins-directory: "plugins"</li>
     *   <li>plugins-packages: empty list</li>
     *   <li>io-threads: 0 (auto-detect based on CPU cores)</li>
     *   <li>workers-scheduler-parallelism: 0 (uses default)</li>
     *   <li>workers-scheduler-max-pool-size: 256</li>
     *   <li>buffer-size: 16384 bytes</li>
     *   <li>direct-buffers: true</li>
     *   <li>force-gzip-encoding: false</li>
     *   <li>allow-unescaped-characters-in-url: true</li>
     * </ul>
     */
    private static final CoreModule DEFAULT_CORE_MODULE = new CoreModule("default", "plugins", new ArrayList<>(), false, null, 0, 0, 256, true, 16364, true, false, true);

    /**
     * Creates a CoreModule from a configuration map.
     * 
     * <p>This constructor extracts core configuration values from the provided map,
     * using default values for any missing properties.</p>
     * 
     * @param conf the configuration map containing core settings
     * @param silent if true, suppresses warning messages for missing optional properties
     */
    public CoreModule(Map<String, Object> conf, boolean silent) {
        this(getOrDefault(conf, INSTANCE_NAME_KEY, DEFAULT_CORE_MODULE.name(), silent),
            getOrDefault(conf, PLUGINS_DIRECTORY_PATH_KEY, DEFAULT_CORE_MODULE.pluginsDirectory(), silent),
            // following is optional, so get it always in silent mode
            getOrDefault(conf, PLUGINS_PACKAGES_KEY, DEFAULT_CORE_MODULE.pluginsPackages(), true),
            getOrDefault(conf, PLUGINS_SCANNING_VERBOSE_KEY, false, true),
            getOrDefault(conf, BASE_URL_KEY, DEFAULT_CORE_MODULE.baseUrl(), true),
            getOrDefault(conf, IO_THREADS_KEY, DEFAULT_CORE_MODULE.ioThreads(), silent),
            getOrDefault(conf, WORKERS_SCHEDULER_PARALLELISM_KEY, DEFAULT_CORE_MODULE.workersSchedulerParallelism(), silent),
            getOrDefault(conf, WORKERS_SCHEDULER_MAX_POOL_SIZE_KEY, DEFAULT_CORE_MODULE.workersSchedulerMaxPoolSize(), silent),
            getOrDefault(conf, BUFFERS_POOLING_KEY, DEFAULT_CORE_MODULE.buffersPooling(), silent),
            getOrDefault(conf, BUFFER_SIZE_KEY, DEFAULT_CORE_MODULE.bufferSize(), silent),
            getOrDefault(conf, DIRECT_BUFFERS_KEY, DEFAULT_CORE_MODULE.directBuffers(), silent),
            // following is optional, so get it always in silent mode
            getOrDefault(conf, FORCE_GZIP_ENCODING_KEY, DEFAULT_CORE_MODULE.forceGzipEncoding(), true),
            // following is optional, so get it always in silent mode
            getOrDefault(conf, ALLOW_UNESCAPED_CHARS_IN_ULR_KEY, DEFAULT_CORE_MODULE.allowUnescapedCharsInUrl(), true));
    }

    /**
     * Builds a CoreModule from the main configuration map.
     * 
     * <p>This method looks for the {@code core} section in the configuration map.
     * If found, it creates a CoreModule from that section. If not found, returns
     * the default CoreModule configuration.</p>
     * 
     * @param conf the main configuration map
     * @param silent if true, suppresses warning messages for missing optional properties
     * @return a CoreModule instance with configuration values or defaults
     */
    public static CoreModule build(Map<String, Object> conf, boolean silent) {
        var core = asMap(conf, CORE_KEY, null, silent);

        if (core != null) {
            return new CoreModule(core, silent);
        } else {
            return DEFAULT_CORE_MODULE;
        }
    }
}