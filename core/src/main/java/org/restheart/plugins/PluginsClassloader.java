/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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
 * Loads a class, including searching within all plugin JAR files.
 * <p>
 * This method is essential for the {@code collectFieldInjections()} method, which processes field injections
 * annotated with {@code @Inject}. Specifically, it addresses cases where the {@code @Inject} annotation
 * references a {@link org.restheart.plugins.Provider} that returns an object. The class of this object may reside
 * in a plugin JAR file, necessitating a comprehensive search to locate and load the class correctly.
 * </p>
 */
public class PluginsClassloader extends URLClassLoader {
    private static PluginsClassloader SINGLETON = null;

    /**
     * call after PluginsScanner.jars array is populated
     * @param jars
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
                }).toArray(size -> new URL[size]);
            try {
                SINGLETON = new PluginsClassloader(urls);
            } catch(IOException ioe) {
                throw new RuntimeException("error initializing", ioe);
            }
        }
    }

    public static boolean isInitialized() {
        return SINGLETON != null;
    }

    private PluginsClassloader(URL[] jars) throws IOException {
        super(jars);
    }

    public static PluginsClassloader getInstance() {
        if (SINGLETON == null) {
            throw new IllegalStateException("not initialized");
        } else {
            return SINGLETON;
        }
    }

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