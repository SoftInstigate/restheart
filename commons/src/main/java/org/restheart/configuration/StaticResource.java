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
import static org.restheart.configuration.Utils.asListOfMaps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Configuration for static resource serving.
 * 
 * <p>This record represents the configuration for serving static files such as HTML,
 * CSS, JavaScript, images, and other static content. Static resources can be served
 * from the filesystem or from embedded resources within the classpath.</p>
 * 
 * <h2>Configuration Structure</h2>
 * <p>In the configuration file, static resources are configured as:</p>
 * <pre>{@code
 * static-resources:
 *   - what: "/app"
 *     where: "/static"
 *     welcome-file: "index.html"
 *     embedded: false
 *   - what: "/docs"
 *     where: "/documentation"
 *     welcome-file: "readme.html"
 *     embedded: true
 * }</pre>
 * 
 * <h2>Resource Types</h2>
 * <ul>
 *   <li><b>File System Resources</b> (embedded: false) - Serves files from the 
 *       filesystem. The "what" path should be absolute or relative to the working directory.</li>
 *   <li><b>Embedded Resources</b> (embedded: true) - Serves files from the classpath,
 *       useful for packaging static content within the application JAR.</li>
 * </ul>
 * 
 * <h2>URL Mapping</h2>
 * <p>The "where" parameter defines the URL path where the resources will be available.
 * For example, if "what" is "/app" and "where" is "/static", then a file at
 * "/app/css/style.css" will be served at the URL "/static/css/style.css".</p>
 * 
 * <h2>Security Considerations</h2>
 * <ul>
 *   <li>Be careful with filesystem paths to avoid directory traversal attacks</li>
 *   <li>Consider using embedded resources for sensitive static content</li>
 *   <li>Apply appropriate access control to static resource URLs</li>
 * </ul>
 * 
 * @param what the source path for static resources (filesystem path or classpath)
 * @param where the URL path where resources will be served
 * @param welcomeFile the default file to serve for directory requests (e.g., "index.html")
 * @param embedded if true, serves from classpath; if false, serves from filesystem
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 1.0
 */
public record StaticResource(String what, String where, String welcomeFile, boolean embedded) {
    /**
     * Configuration key for the static resources list in the main configuration.
     */
    public static final String STATIC_RESOURCES_MOUNTS_KEY = "static-resources";
    
    /**
     * Configuration key for the source path of static resources.
     */
    public static final String STATIC_RESOURCES_MOUNT_WHAT_KEY = "what";
    
    /**
     * Configuration key for the URL path where resources are served.
     */
    public static final String STATIC_RESOURCES_MOUNT_WHERE_KEY = "where";
    
    /**
     * Configuration key for the welcome file name.
     */
    public static final String STATIC_RESOURCES_MOUNT_WELCOME_FILE_KEY = "welcome-file";
    
    /**
     * Configuration key for embedded resource flag.
     */
    public static final String STATIC_RESOURCES_MOUNT_EMBEDDED_KEY = "embedded";

    /**
     * Creates a StaticResource from a configuration map.
     * 
     * <p>This constructor extracts static resource configuration values from the
     * provided map. The "what" and "where" fields are required, while "welcome-file"
     * defaults to "index.html" and "embedded" defaults to false.</p>
     * 
     * @param conf the configuration map containing static resource settings
     * @param silent if true, suppresses warning messages for missing optional properties
     */
    public StaticResource(Map<String, Object> conf, boolean silent) {
        this(getOrDefault(conf, STATIC_RESOURCES_MOUNT_WHAT_KEY, null, silent),
            getOrDefault(conf, STATIC_RESOURCES_MOUNT_WHERE_KEY, null, silent),
            // following are optional parameter, so get them always in silent mode
            getOrDefault(conf, STATIC_RESOURCES_MOUNT_WELCOME_FILE_KEY, "index.html", true),
            getOrDefault(conf, STATIC_RESOURCES_MOUNT_EMBEDDED_KEY, false, true));
    }

    /**
     * Builds a list of StaticResource configurations from the main configuration map.
     * 
     * <p>This method looks for the {@code static-resources} section in the configuration
     * map, which should contain a list of static resource configurations. Each resource
     * configuration is validated and converted into a StaticResource instance.</p>
     * 
     * <h3>Example Configuration</h3>
     * <pre>{@code
     * static-resources:
     *   - what: "/var/www/html"
     *     where: "/web"
     *   - what: "/app/static"
     *     where: "/"
     *     welcome-file: "home.html"
     * }</pre>
     * 
     * @param conf the main configuration map
     * @param silent if true, suppresses warning messages for missing optional properties
     * @return list of configured static resources, empty list if none configured
     */
    public static List<StaticResource> build(Map<String, Object> conf, boolean silent) {
        var staticResources = asListOfMaps(conf, STATIC_RESOURCES_MOUNTS_KEY, null, silent);

        if (staticResources != null) {
            return staticResources.stream().map(p -> new StaticResource(p, silent)).collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }
}