/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.polyglot;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to set the thread context classloader to the PluginsClassloader
 * before calling GraalVM's Source.findLanguage().
 *
 * <p>Source.findLanguage() discovers languages via ServiceLoader using the
 * thread context classloader. The js-language JAR lives in plugins/lib/,
 * which is only visible to the PluginsClassloader, not the system classloader.</p>
 */
public final class PolyglotClassloaderHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolyglotClassloaderHelper.class);

    private static final String PCL_CLASS = "org.restheart.plugins.PluginsClassloader";

    /**
     * Functional interface that allows IOException to be thrown.
     */
    @FunctionalInterface
    public interface IORunnable {
        void run() throws IOException;
    }

    /**
     * Functional interface that returns a value and allows IOException.
     */
    @FunctionalInterface
    public interface IOCallable<T> {
        T call() throws IOException;
    }

    private PolyglotClassloaderHelper() {}

    /**
     * Returns the PluginsClassloader instance via reflection, or null if
     * unavailable.
     */
    private static ClassLoader getPluginsClassloader() {
        try {
            Class<?> pclClass = Class.forName(PCL_CLASS);
            var getInstance = pclClass.getMethod("getInstance");
            return (ClassLoader) getInstance.invoke(null);
        } catch (Exception e) {
            LOGGER.debug("PluginsClassloader not available via reflection", e);
            return null;
        }
    }

    /**
     * Sets the thread context classloader to the PluginsClassloader for the
     * duration of the action, then restores the original.
     *
     * @param action code to run with the PluginsClassloader as context classloader
     * @throws IOException if the action throws an IOException
     */
    public static void withPluginsClassloader(IORunnable action) throws IOException {
        ClassLoader pluginsCl = getPluginsClassloader();
        if (pluginsCl == null) {
            action.run();
            return;
        }

        ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(pluginsCl);
        try {
            action.run();
        } finally {
            Thread.currentThread().setContextClassLoader(oldCl);
        }
    }

    /**
     * Sets the thread context classloader to the PluginsClassloader for the
     * duration of the callable, then restores the original. Returns the result.
     *
     * @param callable code to run with the PluginsClassloader as context classloader
     * @return the result of the callable
     * @throws IOException if the callable throws an IOException
     */
    public static <T> T withPluginsClassloaderResult(IOCallable<T> callable) throws IOException {
        ClassLoader pluginsCl = getPluginsClassloader();
        if (pluginsCl == null) {
            return callable.call();
        }

        ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(pluginsCl);
        try {
            return callable.call();
        } finally {
            Thread.currentThread().setContextClassLoader(oldCl);
        }
    }
}
