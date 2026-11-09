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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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

    /** The plugins that did not deploy within the timeout, so that the next scenarios fail at once. */
    private static final Set<String> NOT_DEPLOYED = ConcurrentHashMap.newKeySet();

    /** How long the deployer may take to deploy a plugins directory. */
    private static final Duration DEPLOY_TIMEOUT = Duration.ofSeconds(60);

    /** How often the registered plugins are checked while waiting. */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    /** Lists the registered services and interceptors, see DeployedPluginsService in restheart-test-plugins. */
    private static final URI DEPLOYED_PLUGINS = URI.create("http://localhost:8080/test-deployed-plugins");

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
        // next to the plugins directory, on the same file system, so that the move below is atomic
        var staging = Paths.get(projectRoot, "core/target/plugins-staging/" + name);

        delete(dest);
        delete(staging);
        Files.createDirectories(staging.getParent());

        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> copy(source, staging.resolve(src.relativize(source))));
        }

        setLastModified(staging.toFile(), System.currentTimeMillis());

        // the deployer watches the plugins directory and deploys a file when it appears, if package.json
        // declares it: a file copied before package.json was never deployed, depending on the order in
        // which the file system lists them (#802). The directory appears at once, with every file in it
        Files.move(staging, dest, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void delete(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
    }

    /**
     * Copies a plugins directory once per test run and waits until RESTHeart has registered the
     * given plugins, so that every feature using it can call this in its background without
     * redeploying the plugins.
     *
     * <p>The deployer loads the plugins one by one, each in its own context: how long it takes
     * depends on the machine, so a fixed sleep made the tests fail on slow runners (#802).</p>
     *
     * @param name    the directory under {@code polyglot/src/test/resources}
     * @param plugins the names of the services and interceptors to wait for
     */
    public void ensure(String name, List<String> plugins) throws IOException, InterruptedException {
        if (DEPLOYED.add(name)) {
            copyFolder(name);
        }

        awaitDeployed(plugins);
    }

    /**
     * Copies a plugins directory the way {@code cp -r} may do it, with the plugin files first and
     * {@code package.json} last, then waits until RESTHeart has registered the given plugins.
     *
     * <p>The deployer skipped a plugin file that appeared before {@code package.json} declared it,
     * so those plugins were never deployed (#804).</p>
     *
     * @param name    the directory under {@code polyglot/src/test/resources}
     * @param plugins the names of the services and interceptors to wait for
     */
    public void copyPackageJsonLast(String name, List<String> plugins) throws IOException, InterruptedException {
        if (DEPLOYED.add(name)) {
            var projectRoot = projectRoot();
            var src = Paths.get(projectRoot, "polyglot/src/test/resources/" + name);
            var dest = Paths.get(projectRoot, "core/target/plugins/" + name);

            delete(dest);
            Files.createDirectories(dest);
            // the deployer sees the directory empty
            Thread.sleep(1000);

            try (Stream<Path> files = Files.list(src)) {
                for (var file : files.filter(f -> !f.getFileName().toString().equals("package.json")).toList()) {
                    copy(file, dest.resolve(file.getFileName()));
                }
            }

            // the deployer sees the plugin files, that package.json does not declare yet
            Thread.sleep(2000);

            copy(src.resolve("package.json"), dest.resolve("package.json"));
        }

        awaitDeployed(plugins);
    }

    private static void awaitDeployed(Collection<String> plugins) throws InterruptedException {
        var known = plugins.stream().filter(NOT_DEPLOYED::contains).toList();

        if (!known.isEmpty()) {
            throw new IllegalStateException("plugins not deployed: " + known + ", see core/restheart.log");
        }

        var deadline = System.nanoTime() + DEPLOY_TIMEOUT.toNanos();
        var missing = Set.copyOf(plugins);

        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(DEPLOYED_PLUGINS).timeout(Duration.ofSeconds(5)).GET().build();

            while (true) {
                missing = missing(client, request, plugins, missing);

                if (missing.isEmpty()) {
                    return;
                }

                if (System.nanoTime() > deadline) {
                    NOT_DEPLOYED.addAll(missing);
                    throw new IllegalStateException("plugins not deployed after " + DEPLOY_TIMEOUT.toSeconds()
                            + " seconds: " + missing + ", see core/restheart.log");
                }

                Thread.sleep(POLL_INTERVAL);
            }
        }
    }

    /**
     * @return the plugins not registered yet; the previous result if RESTHeart does not answer,
     *         e.g. while it is busy deploying
     */
    private static Set<String> missing(HttpClient client, HttpRequest request, Collection<String> plugins, Set<String> previous)
            throws InterruptedException {
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return previous;
            }

            var body = JsonParser.parseString(response.body()).getAsJsonObject();
            var registered = new HashSet<String>();
            registered.addAll(names(body, "services"));
            registered.addAll(names(body, "interceptors"));

            var ret = new HashSet<>(plugins);
            ret.removeAll(registered);
            return ret;
        } catch (IOException | RuntimeException e) {
            return previous;
        }
    }

    private static List<String> names(JsonObject body, String key) {
        return body.getAsJsonArray(key).asList().stream().map(JsonElement::getAsString).toList();
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
