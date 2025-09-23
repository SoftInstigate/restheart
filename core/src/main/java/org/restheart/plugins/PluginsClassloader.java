/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

import org.restheart.utils.LambdaUtils;

/**
 * Custom class loader for loading classes from plugin JAR files.
 * 
 * This class loader extends URLClassLoader to provide the ability to load classes
 * from plugin JAR files that may not be available in the standard classpath.
 * It implements a singleton pattern to ensure there's only one instance managing
 * all plugin class loading throughout the application lifecycle.
 * 
 * <p>
 * This class loader is essential for the dependency injection mechanism, particularly
 * for the {@code collectFieldInjections()} method, which processes field injections
 * annotated with {@code @Inject}. It addresses cases where the {@code @Inject} annotation
 * references a {@link org.restheart.plugins.Provider} that returns an object whose class
 * may reside in a plugin JAR file, necessitating a comprehensive search to locate and
 * load the class correctly.
 * </p>
 * 
 * <p>
 * The class loader uses a hierarchical approach: it first attempts to load classes
 * using the standard class loader, and only falls back to searching plugin JARs
 * if the class is not found in the standard classpath.
 * </p>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see URLClassLoader
 * @see PluginsScanner
 * @see org.restheart.plugins.Provider
 * @see org.restheart.plugins.Inject
 */
public class PluginsClassloader extends URLClassLoader {
    /**
     * Singleton instance of the plugins class loader.
     */
    private static PluginsClassloader SINGLETON = null;

    /**
     * Initializes the singleton PluginsClassloader with the given JAR URLs.
     * 
     * This method must be called after the PluginsScanner.jars array is populated
     * and before any attempt to use the class loader. It can only be called once
     * during the application lifecycle.
     * 
     * @param jars array of URLs pointing to plugin JAR files to be included in the classpath
     * @throws IllegalStateException if the class loader has already been initialized
     * @throws RuntimeException if an I/O error occurs during initialization
     */
    public static void init(URL[] jars) {
        if (SINGLETON != null) {
            throw new IllegalStateException("already initialized");
        } else {
            try {
                SINGLETON = new PluginsClassloader(jars);
            } catch(IOException ioe) {
                throw new RuntimeException("error initializing", ioe);
            }
        }
    }

    /**
     * Initializes the singleton PluginsClassloader with the given file paths.
     * 
     * This convenience method converts a list of file paths to URLs and initializes
     * the class loader. It must be called before any attempt to use the class loader
     * and can only be called once during the application lifecycle.
     * 
     * @param paths list of file paths pointing to plugin JAR files to be included in the classpath
     * @throws IllegalStateException if the class loader has already been initialized
     * @throws RuntimeException if an I/O error occurs during initialization or if a path cannot be converted to a URL
     */
    public static void init(List<Path> paths) {
        if (SINGLETON != null) {
            throw new IllegalStateException("already initialized");
        } else {
            var urls = paths.stream().map(p -> {
                    try {
                        return p.toUri().toURL();
                    } catch(MalformedURLException murle) {
                        LambdaUtils.throwsSneakyException(murle);
                        return null;
                    }
                }).toArray(URL[]::new);

            try {
                SINGLETON = new PluginsClassloader(urls);
            } catch(IOException ioe) {
                throw new RuntimeException("error initializing", ioe);
            }
        }
    }

    /**
     * Checks whether the PluginsClassloader singleton has been initialized.
     * 
     * @return true if the class loader has been initialized, false otherwise
     */
    public static boolean isInitialized() {
        return SINGLETON != null;
    }

    /**
     * Private constructor that creates a new PluginsClassloader with the given JAR URLs.
     * 
     * This constructor is private to enforce the singleton pattern. Use the static
     * {@link #init(URL[])} or {@link #init(List)} methods to initialize the class loader.
     * 
     * @param jars array of URLs pointing to plugin JAR files
     * @throws IOException if an I/O error occurs during initialization
     */
    private PluginsClassloader(URL[] jars) throws IOException {
        super(jars);
    }

    /**
     * Returns the singleton instance of the PluginsClassloader.
     * 
     * This method provides access to the initialized class loader instance.
     * The class loader must be initialized using one of the {@code init()} methods
     * before calling this method.
     * 
     * @return the singleton PluginsClassloader instance
     * @throws IllegalStateException if the class loader has not been initialized
     */
    public static PluginsClassloader getInstance() {
        if (SINGLETON == null) {
            throw new IllegalStateException("not initialized");
        } else {
            return SINGLETON;
        }
    }

    /**
     * Loads the class with the specified binary name.
     * 
     * This method implements a hierarchical class loading strategy:
     * <ol>
     * <li>First attempts to load the class using the PluginsScanner's class loader</li>
     * <li>If the class is not found, falls back to searching in the plugin JAR files
     *     using the parent URLClassLoader implementation</li>
     * </ol>
     * 
     * This approach ensures that standard classes are loaded efficiently while
     * still providing access to classes that exist only in plugin JARs.
     * 
     * @param name the binary name of the class to load
     * @return the resulting Class object
     * @throws ClassNotFoundException if the class was not found in either the
     *         standard classpath or the plugin JAR files
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            // first try to load the class with the PluginsScanner's classloader
            return PluginsScanner.class.getClassLoader().loadClass(name);
        } catch (ClassNotFoundException cnfe) {
            // then use the URLClassLoader to try loading the class from the plugins jars
            return super.loadClass(name);
        }
    }
}