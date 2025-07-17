/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.restheart.configuration.Utils.findOrDefault;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.graalvm.home.Version;
import org.graalvm.polyglot.Source;
import org.restheart.configuration.Configuration;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.graal.ImageInfo;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.polyglot.interceptors.JSInterceptor;
import org.restheart.polyglot.interceptors.JSInterceptorFactory;
import org.restheart.polyglot.services.JSService;
import org.restheart.polyglot.services.JSStringService;
import org.restheart.polyglot.services.NodeService;
import org.restheart.utils.DirectoryWatcher;
import org.restheart.utils.ThreadsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mongodb.client.MongoClient;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
    name = "polyglotDeployer",
    description = "handles GraalVM polyglot plugins",
    enabledByDefault = true)
public class PolyglotDeployer implements Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolyglotDeployer.class);
    private static final Map<Path, JSPlugin> DEPLOYEES = new HashMap<>();
    private JSInterceptorFactory jsInterceptorFactory;

    @Inject("registry")
    private PluginsRegistry registry;

    @Inject("rh-config")
    private Configuration config;

    private Optional<MongoClient> mclient;

    @OnInit
    public void onInit() {
        if (!isRunningOnGraalVM()) {
            LOGGER.warn("Not running on GraalVM, polyglot plugins deployer disabled!");
            return;
        }

        final Path pluginsDirectory = getPluginsDirectory(config.toMap());

        LOGGER.trace("pluginsDirectory: {}", pluginsDirectory);

        this.mclient = mongoClient(registry);

        this.jsInterceptorFactory = new JSInterceptorFactory(this.mclient, this.config);
        deployAll(pluginsDirectory);
        watch(pluginsDirectory);
    }

    private Optional<MongoClient> mongoClient(final PluginsRegistry registry) {
        return registry.getProviders().stream()
                .filter(pd -> pd.isEnabled())
                .map(pd -> pd.getInstance())
                .filter(p -> MongoClient.class.getName().equals(p.rawType().getName()))
                .map(p -> (MongoClient) p.get(null))
                .findFirst();
    }

    private boolean isRunningOnGraalVM() {
        try {
            return Version.getCurrent().isRelease();
        } catch (final Throwable cnfe) {
            return false;
        }
    }

    @Override
    public void init() {
        // nothing to do
    }

    private void deployAll(final Path pluginsDirectory) {
        for (final var pluginPath : findJsPluginDirectories(pluginsDirectory)) {
            try {
                final var services = findServices(pluginPath);
                final var nodeServices = findNodeServices(pluginPath);
                final var interceptors = findInterceptors(pluginPath);
                deploy(services, nodeServices, interceptors);
            } catch (final IOException t) {
                LOGGER.error("Error deploying {}", pluginPath.toAbsolutePath(), t);
            } catch (final InterruptedException t) {
                Thread.currentThread().interrupt();
                LOGGER.error("Error deploying {}", pluginPath.toAbsolutePath(), t);
            }
        }
    }

    private Path pluginPathFromEvent(final Path pluginsDirectory, final Path path) {
        // Get the relative path between pluginsDirectory and subdirectory
        final var relativePath = pluginsDirectory.relativize(path);

        // Return the first element from the relative path (i.e., the pluginDirectory)
        return pluginsDirectory.resolve(relativePath.getName(0));
    }

    private void watch(final Path pluginsDirectory) {
        try {
            final var watcher = new DirectoryWatcher(pluginsDirectory, (path, kind) -> {
                try {
                    if (Files.isDirectory(path)) {
                        return;
                    }
                    if (path.toString().endsWith(".mjs")) {
                        switch (kind.name()) {
                            case "ENTRY_CREATE", "ENTRY_MODIFY" -> {
                                undeploy(path);
                                final var pluginDir = pluginPathFromEvent(pluginsDirectory, path);
                                final var services = findServices(pluginDir);
                                final var interceptors = findInterceptors(pluginDir);

                                // Deploy all services
                                for (final Path service : services) {
                                    if (service.equals(path)) {
                                        deployService(path);
                                    }
                                }

                                // Deploy all interceptors
                                for (final Path interceptor : interceptors) {
                                    if (interceptor.equals(path)) {
                                        deployInterceptor(path);
                                    }
                                }
                            }
                            case "ENTRY_DELETE" -> undeploy(path);
                            default -> {
                            }
                        }
                    }
                } catch (IOException | InterruptedException ex) {
                    LOGGER.error("Error handling fs event {} for file {}", kind, path, ex);
                }
            });

            ThreadsUtils.virtualThreadsExecutor().execute(watcher);
        } catch (final IOException ex) {
            LOGGER.error("Error watching: {}", pluginsDirectory.toAbsolutePath(), ex);
        }
    }

    public static final String PLUGINS_DIRECTORY_XPATH = "/core/plugins-directory";

    private Path getPluginsDirectory(final Map<String, Object> args) {
        final var pluginsPath = Path.of(findOrDefault(args, PLUGINS_DIRECTORY_XPATH, "plugins", false));

        if (pluginsPath.isAbsolute()) {
            return pluginsPath;
        }
        // this is to allow specifying the plugins directory path
        // relative to the jar (also working when running from classes)
        final var location = this.getClass().getProtectionDomain().getCodeSource().getLocation();
        URI locationUri;

        try {
            // Handle Windows paths correctly
            if (location.getProtocol().equals("file")) {
                String path = location.getPath();
                // Remove leading slash from Windows paths
                if (path.matches("^/[A-Za-z]:/.*")) {
                    path = path.substring(1);
                }
                locationUri = new File(path).toURI();
            } else {
                locationUri = location.toURI();
            }

            if (ImageInfo.inImageRuntimeCode()) {
                // directory relative to the one containing the native image executable
                LOGGER.info("Code is executing at image runtime");
                return Path.of(locationUri).toAbsolutePath().normalize().getParent().resolve(pluginsPath);
            } else {
                // the directory containing the plugin jar is the plugins directory
                LOGGER.info("Code is executing at development time");
                return Path.of(locationUri).toAbsolutePath().normalize().getParent();
            }
        } catch (final URISyntaxException uee) {
            throw new IllegalStateException(uee);
        }

    }

    private List<Path> findJsPluginDirectories(final Path pluginsDirectory) {
        if (!checkPluginDirectory(pluginsDirectory)) {
            LOGGER.error("js plugins will not be deployed");
            return Lists.newArrayList();
        }

        final var paths = new ArrayList<Path>();

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(pluginsDirectory)) {
            for (final Path path : directoryStream) {
                final var services = findServices(path, false);
                final var interceptors = findInterceptors(path, false);
                final var nodeServices = findNodeServices(path, false);
                if (!services.isEmpty() || !nodeServices.isEmpty() || !interceptors.isEmpty()) {
                    paths.add(path);
                    LOGGER.info("Found js plugin directory {}", path);
                }
            }
        } catch (final IOException ex) {
            LOGGER.error("Cannot read js plugin directory {}", pluginsDirectory, ex);
        }

        return paths;
    }

    private boolean checkPluginDirectory(final Path pluginsDirectory) {
        if (pluginsDirectory == null) {
            LOGGER.error("Plugin directory {} configuration option not set");
            return false;
        }

        if (!Files.exists(pluginsDirectory)) {
            LOGGER.error("Plugin directory {} does not exist", pluginsDirectory);
            return false;
        }

        if (!Files.isReadable(pluginsDirectory)) {
            LOGGER.error("Plugin directory {} is not readable", pluginsDirectory);
            return false;
        }

        return true;
    }

    private List<Path> findServices(final Path path) {
        return findServices(path, true);
    }

    private List<Path> findServices(final Path path, final boolean checkPluginFiles) {
        return findDeclaredPlugins(path, "rh:services", checkPluginFiles);
    }

    private List<Path> findNodeServices(final Path path) {
        return findNodeServices(path, true);
    }

    private List<Path> findNodeServices(final Path path, final boolean checkPluginFiles) {
        return findDeclaredPlugins(path, "rh:node-services", checkPluginFiles);
    }

    private List<Path> findInterceptors(final Path path) {
        return findInterceptors(path, true);
    }

    private List<Path> findInterceptors(final Path path, final boolean checkPluginFiles) {
        return findDeclaredPlugins(path, "rh:interceptors", checkPluginFiles);
    }

    private List<Path> findDeclaredPlugins(final Path path, final String prop, final boolean checkPluginFiles) {
        if (!Files.isDirectory(path)) {
            return Lists.newArrayList();
        }

        final var packagePath = path.resolve("package.json");

        if (!Files.isRegularFile(packagePath)) {
            return Lists.newArrayList();
        }

        try {
            final var p = JsonParser.parseReader(Files.newBufferedReader(packagePath));

            if (p.isJsonObject()
                    && p.getAsJsonObject().has(prop)
                    && p.getAsJsonObject().get(prop).isJsonArray()
                    && p.getAsJsonObject().getAsJsonArray(prop).size() > 0) {
                final List<Path> ret = Lists.newArrayList();

                p.getAsJsonObject().getAsJsonArray(prop).forEach(item -> {
                    if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                        final var pluginPath = path.resolve(item.getAsString());

                        if (checkPluginFiles) {
                            if (Files.isRegularFile(pluginPath)) {
                                try {
                                    final var language = Source.findLanguage(pluginPath.toFile());
                                    if ("js".equals(language)) {
                                        ret.add(pluginPath);
                                    } else {
                                        LOGGER.warn("{} is not javascript", pluginPath.toAbsolutePath());
                                    }
                                } catch (final IOException e) {
                                    LOGGER.warn("{} is not javascript", pluginPath.toAbsolutePath(), e);
                                }
                            } else {
                                LOGGER.warn("pluging not found {}, it is declared in {}", pluginPath.toAbsolutePath(),
                                        packagePath.toAbsolutePath());
                            }
                        } else {
                            ret.add(pluginPath);
                        }
                    }
                });

                return ret;
            } else {
                return Lists.newArrayList();
            }
        } catch (JsonIOException | JsonSyntaxException | IOException t) {
            LOGGER.error("Error reading {}", packagePath, t);
            return Lists.newArrayList();
        }
    }

    private void deploy(final List<Path> services, final List<Path> nodeServices, final List<Path> interceptors)
            throws IOException, InterruptedException {
        for (final Path service : services) {
            deployService(service);
        }

        for (final Path nodeService : nodeServices) {
            deployNodeService(nodeService);
        }

        for (final Path interceptor : interceptors) {
            deployInterceptor(interceptor);
        }
    }

    private void deployService(final Path pluginPath) throws IOException, InterruptedException {
        if (isRunningOnNode()) {
            throw new IllegalStateException("Cannot deploy a CommonJs service, RESTHeart is running on Node");
        }

        try {
            final var srv = new JSStringService(pluginPath, this.mclient, this.config);

            final var pluginRecord = new PluginRecord<Service<? extends ServiceRequest<?>, ? extends ServiceResponse<?>>>(
                    srv.name(),
                    srv.getDescription(),
                    srv.secured(),
                    true,
                    srv.getClass().getName(),
                    srv,
                    new HashMap<>());

            registry.plugService(pluginRecord, srv.uri(), srv.matchPolicy(), srv.secured());

            DEPLOYEES.put(pluginPath.toAbsolutePath(), srv);

            LOGGER.info(ansi().fg(GREEN)
                    .a("Service '{}' deployed at URI '{}' with description: '{}'. Secured: {}. Uri match policy: {}")
                    .reset().toString(), srv.name(), srv.uri(), srv.getDescription(), srv.secured(), srv.matchPolicy());
        } catch (IllegalArgumentException | IllegalStateException e) {
            if (e.getMessage().contains("require is not defined")) {
                LOGGER.error(
                        "Error deploying plugin {}. Resolution: Try running 'npm install' for required dependencies.",
                        pluginPath);
            } else {
                LOGGER.error("Error deploying plugin {}", pluginPath, e);
            }
        }
    }

    private void deployNodeService(final Path pluginPath) throws IOException {
        if (!isRunningOnNode()) {
            throw new IllegalStateException("Cannot deploy a node service, RESTHeart is not running on Node");
        }

        LOGGER.warn("Node JS plugins are experimental and are likely to change in future");
        final var executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                final var srvf = NodeService.get(pluginPath, this.mclient, this.config);

                final var srv = srvf.get(30, TimeUnit.SECONDS);

                final var pluginRecord = new PluginRecord<Service<? extends ServiceRequest<?>, ? extends ServiceResponse<?>>>(
                        srv.name(), "description", srv.secured(), true, srv.getClass().getName(), srv, new HashMap<>());

                registry.plugService(pluginRecord, srv.uri(), srv.matchPolicy(), srv.secured());

                DEPLOYEES.put(pluginPath.toAbsolutePath(), srv);

                LOGGER.info(ansi().fg(GREEN).a(
                        "Service '{}' deployed at URI '{}' with description: '{}'. Secured: {}. Uri match policy: {}")
                        .reset().toString(), srv.name(), srv.uri(), srv.getDescription(), srv.secured(),
                        srv.matchPolicy());
            } catch (IOException | InterruptedException | ExecutionException | TimeoutException ex) {
                LOGGER.error("Error deploying node service {}", pluginPath, ex);
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        });
        executor.shutdown();
    }

    private void deployInterceptor(final Path pluginPath) throws IOException, InterruptedException {
        if (isRunningOnNode()) {
            throw new IllegalStateException("Cannot deploy a CommonJs interceptor, RESTHeart is running on Node");
        }

        final var interceptorRecord = this.jsInterceptorFactory.create(pluginPath);

        registry.addInterceptor(interceptorRecord);

        DEPLOYEES.put(pluginPath.toAbsolutePath(), (JSPlugin) interceptorRecord.getInstance());

        LOGGER.info(ansi().fg(GREEN).a("Interceptor '{}' deployed with description: '{}'").reset().toString(),
                interceptorRecord.getName(),
                interceptorRecord.getDescription());
    }

    private void undeploy(final Path pluginPath) {
        undeployServices(pluginPath);
        undeployInterceptors(pluginPath);
    }

    private void undeployServices(final Path pluginPath) {
        final var pathsToUndeploy = DEPLOYEES.keySet().stream()
                .filter(path -> (DEPLOYEES.get(path) instanceof JSService))
                .filter(path -> path.equals(pluginPath))
                .collect(Collectors.toList());

        for (final var pathToUndeploy : pathsToUndeploy) {
            final var _toUndeploy = DEPLOYEES.remove(pathToUndeploy);

            if (_toUndeploy != null && _toUndeploy instanceof final JSService toUndeploy) {
                registry.unplug(toUndeploy.uri(), toUndeploy.matchPolicy());

                LOGGER.info(ansi().fg(GREEN).a("Service '{}' bound to '{}' undeployed").reset().toString(),
                        toUndeploy.name(), toUndeploy.uri());
            }
        }
    }

    private void undeployInterceptors(final Path pluginPath) {
        final var pathsToUndeploy = DEPLOYEES.keySet().stream()
                .filter(path -> DEPLOYEES.get(path) instanceof JSInterceptor)
                .filter(path -> path.equals(pluginPath))
                .collect(Collectors.toList());

        for (final var pathToUndeploy : pathsToUndeploy) {
            final var toUndeploy = DEPLOYEES.remove(pathToUndeploy);
            final var removed = registry
                    .removeInterceptorIf(interceptor -> Objects.equal(interceptor.getName(), toUndeploy.name()));

            if (removed) {
                LOGGER.info(ansi().fg(GREEN).a("Interceptor '{}' undeployed").reset().toString(), toUndeploy.name());
            } else {
                LOGGER.warn("Interceptor {} was not undeployed", toUndeploy.name());
            }
        }
    }

    private boolean isRunningOnNode() {
        return NodeQueue.instance().isRunningOnNode();
    }
}