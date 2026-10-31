/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package karate;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Copies a directory of polyglot plugins from {@code polyglot/src/test/resources} into the
 * plugins directory of the RESTHeart under test, {@code core/target/plugins}, where the
 * deployer picks it up.
 *
 * <p>The conformance suite is written against the HTTP responses of reference plugins, not
 * against a language: a second guest language provides the same plugins in its own directory
 * and the features call {@link #ensure(String)} with that name.</p>
 */
public class CopyFolderRecursively {
    private static final Set<String> DEPLOYED = ConcurrentHashMap.newKeySet();

    /** How long the deployer takes to notice a new directory and deploy its plugins. */
    private static final long DEPLOY_SECONDS = 10;

    /** The default plugins directory, the JavaScript reference plugins. */
    public static final String JS_PLUGINS = "test-js-plugins";

    // cp -r polyglot/src/test/resources/test-js-plugins core/target/plugins
    public void copyFolder() throws IOException, InterruptedException {
        copyFolder(JS_PLUGINS);
    }

    /**
     * Copies a plugins directory, replacing what is there.
     *
     * @param name the directory under {@code polyglot/src/test/resources}
     */
    public void copyFolder(String name) throws IOException, InterruptedException {
        var projectRoot = projectRoot();
        var src = Paths.get(projectRoot, "polyglot/src/test/resources/" + name);
        var dest = Paths.get(projectRoot, "core/target/plugins/" + name);

        // delete the directory and files within
        if (Files.exists(dest)) {
            try (Stream<Path> walk = Files.walk(dest)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }

        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> copy(source, dest.resolve(src.relativize(source))));
        }

        // file set last modified
        setLastModified(dest.toFile(), System.currentTimeMillis());
    }

    /**
     * Copies a plugins directory once per test run and waits for the deployer, so that every
     * feature using it can call this in its background without redeploying the plugins.
     *
     * @param name the directory under {@code polyglot/src/test/resources}
     */
    public void ensure(String name) throws IOException, InterruptedException {
        if (DEPLOYED.add(name)) {
            copyFolder(name);
            Thread.sleep(DEPLOY_SECONDS * 1000);
        }
    }

    private static String projectRoot() {
        var workingDir = System.getProperty("user.dir");
        return workingDir.substring(0, workingDir.lastIndexOf("/"));
    }

    private void copy(Path source, Path dest) {
        try {
            Files.copy(source, dest, COPY_ATTRIBUTES);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public void setLastModified(File file, long timestamp) {
        file.setLastModified(timestamp);
    }
}
