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

import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.ansi;
import org.graalvm.home.Version;
import org.graalvm.polyglot.Source;
import org.restheart.configuration.Configuration;
import static org.restheart.configuration.Utils.findOrDefault;
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
@RegisterPlugin(name = "polyglotDeployer",
        description = "handles GraalVM polyglot plugins",
        enabledByDefault = true
)
public class PolyglotDeployer implements Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolyglotDeployer.class);

    private Path pluginsDirectory = null;

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

        pluginsDirectory = getPluginsDirectory(config.toMap());

        LOGGER.trace("pluginsDirectory: {}", pluginsDirectory);

        this.mclient = mongoClient(registry);

        this.jsInterceptorFactory = new JSInterceptorFactory(this.mclient, this.config);
        deployAll(pluginsDirectory);
        watch(pluginsDirectory);
    }

    private Optional<MongoClient> mongoClient(PluginsRegistry registry) {
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
        } catch (Throwable cnfe) {
            return false;
        }
    }

    @Override
    public void init() {
        // nothing to do
    }

    private void deployAll(Path pluginsDirectory) {
        for (var pluginPath : findJsPluginDirectories(pluginsDirectory)) {
            try {
                var services = findServices(pluginPath);
                var nodeServices = findNodeServices(pluginPath);
                var interceptors = findInterceptors(pluginPath);
                deploy(services, nodeServices, interceptors);
            } catch (IOException | InterruptedException t) {
                LOGGER.error("Error deploying {}", pluginPath.toAbsolutePath(), t);
            }
        }
    }

    private Path pluginPathFromEvent(Path pluginsDirectory, Path path) {
        // Get the relative path between pluginsDirectory and subdirectory
        var relativePath = pluginsDirectory.relativize(path);

        // Return the first element from the relative path (i.e., the pluginDirectory)
        return pluginsDirectory.resolve(relativePath.getName(0));
    }

    private void watch(Path pluginsDirectory) {
        try {
            final var watcher = new DirectoryWatcher(pluginsDirectory, (path, kind) -> {
                try {
                    var pluginPath = pluginPathFromEvent(pluginsDirectory, path);
                    switch (kind.name()) {
                        case "ENTRY_CREATE" ->
                            deploy(findServices(pluginPath), findNodeServices(pluginPath), findInterceptors(pluginPath));
                        case "ENTRY_DELETE" ->
                            undeploy(pluginPath);
                        case "ENTRY_MODIFY" -> {
                            undeploy(pluginPath);
                            deploy(findServices(pluginPath), findNodeServices(pluginPath), findInterceptors(pluginPath));
                        }
                        default -> {
                        }
                    }
                } catch (IOException | InterruptedException ex) {
                    LOGGER.error("Error handling fs event {} for file {}", kind, path, ex);
                }
            });

            ThreadsUtils.virtualThreadsExecutor().execute(watcher);
        } catch (IOException ex) {
            LOGGER.error("Error watching: {}", pluginsDirectory.toAbsolutePath(), ex);
        }
    }

    public static final String PLUGINS_DIRECTORY_XPATH = "/core/plugins-directory";

    private Path getPluginsDirectory(Map<String, Object> args) {
        var pluginsPath = Path.of(findOrDefault(args, PLUGINS_DIRECTORY_XPATH, "plugins", false));

        if (pluginsPath.isAbsolute()) {
            return pluginsPath;
        }
        // this is to allow specifying the plugins directory path
        // relative to the jar (also working when running from classes)
        var location = this.getClass().getProtectionDomain().getCodeSource().getLocation();
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
        } catch (URISyntaxException uee) {
            throw new RuntimeException(uee);
        }

    }

    private List<Path> findJsPluginDirectories(Path pluginsDirectory) {
        if (!checkPluginDirectory(pluginsDirectory)) {
            LOGGER.error("js plugins will not be deployed");
            return Lists.newArrayList();
        }

        var paths = new ArrayList<Path>();

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(pluginsDirectory)) {
            for (Path path : directoryStream) {
                var services = findServices(path, false);
                var interceptors = findInterceptors(path, false);
                var nodeServices = findNodeServices(path, false);
                if (!services.isEmpty() || !nodeServices.isEmpty() || !interceptors.isEmpty()) {
                    paths.add(path);
                    LOGGER.info("Found js plugin directory {}", path);
                }
            }
        } catch (IOException ex) {
            LOGGER.error("Cannot read js plugin directory {}", pluginsDirectory, ex);
        }

        return paths;
    }

    private boolean checkPluginDirectory(Path pluginsDirectory) {
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

    private List<Path> findServices(Path path) {
        return findServices(path, true);
    }

    private List<Path> findServices(Path path, boolean checkPluginFiles) {
        return findDeclaredPlugins(path, "rh:services", checkPluginFiles);
    }

    private List<Path> findNodeServices(Path path) {
        return findNodeServices(path, true);
    }

    private List<Path> findNodeServices(Path path, boolean checkPluginFiles) {
        return findDeclaredPlugins(path, "rh:node-services", checkPluginFiles);
    }

    private List<Path> findInterceptors(Path path) {
        return findInterceptors(path, true);
    }

    private List<Path> findInterceptors(Path path, boolean checkPluginFiles) {
        return findDeclaredPlugins(path, "rh:interceptors", checkPluginFiles);
    }

    private List<Path> findDeclaredPlugins(Path path, String prop, boolean checkPluginFiles) {
        if (!Files.isDirectory(path)) {
            return Lists.newArrayList();
        }

        var packagePath = path.resolve("package.json");

        if (!Files.isRegularFile(packagePath)) {
            return Lists.newArrayList();
        }

        try {
            var p = JsonParser.parseReader(Files.newBufferedReader(packagePath));

            if (p.isJsonObject()
                    && p.getAsJsonObject().has(prop)
                    && p.getAsJsonObject().get(prop).isJsonArray()
                    && p.getAsJsonObject().getAsJsonArray(prop).size() > 0) {
                List<Path> ret = Lists.newArrayList();

                p.getAsJsonObject().getAsJsonArray(prop).forEach(item -> {
                    if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                        var pluginPath = path.resolve(item.getAsString());

                        if (checkPluginFiles) {
                            if (Files.isRegularFile(pluginPath)) {
                                try {
                                    var language = Source.findLanguage(pluginPath.toFile());
                                    if ("js".equals(language)) {
                                        ret.add(pluginPath);
                                    } else {
                                        LOGGER.warn("{} is not javascript", pluginPath.toAbsolutePath());
                                    }
                                } catch (IOException e) {
                                    LOGGER.warn("{} is not javascript", pluginPath.toAbsolutePath(), e);
                                }
                            } else {
                                LOGGER.warn("pluging not found {}, it is declared in {}", pluginPath.toAbsolutePath(), packagePath.toAbsolutePath());
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

    private void deploy(List<Path> services, List<Path> nodeServices, List<Path> interceptors) throws IOException, InterruptedException {
        for (Path service : services) {
            deployService(service);
        }

        for (Path nodeService : nodeServices) {
            deployNodeService(nodeService);
        }

        for (Path interceptor : interceptors) {
            deployInterceptor(interceptor);
        }
    }

    private void deployService(Path pluginPath) throws IOException, InterruptedException {
        if (isRunningOnNode()) {
            throw new IllegalStateException("Cannot deploy a CommonJs service, RESTHeart is running on Node");
        }

        try {
            var srv = new JSStringService(pluginPath, this.mclient, this.config);

            var record = new PluginRecord<Service<? extends ServiceRequest<?>, ? extends ServiceResponse<?>>>(srv.name(),
                    srv.getDescription(),
                    srv.secured(),
                    true,
                    srv.getClass().getName(),
                    srv,
                    new HashMap<>());

            registry.plugService(record, srv.uri(), srv.matchPolicy(), srv.secured());

            DEPLOYEES.put(pluginPath.toAbsolutePath(), srv);

            LOGGER.info(ansi().fg(GREEN).a("Service '{}' deployed at URI '{}' with description: '{}'. Secured: {}. Uri match policy: {}").reset().toString(), srv.name(), srv.uri(), srv.getDescription(), srv.secured(), srv.matchPolicy());
        } catch (IllegalArgumentException | IllegalStateException e) {
            if (e.getMessage().contains("require is not defined")) {
                LOGGER.error("Error deploying plugin {}. Resolution: Try running 'npm install' for required dependencies.", pluginPath);
            } else {
                LOGGER.error("Error deploying plugin {}", pluginPath, e);
            }
        }
    }

    private void deployNodeService(Path pluginPath) throws IOException {
        if (!isRunningOnNode()) {
            throw new IllegalStateException("Cannot deploy a node service, RESTHeart is not running on Node");
        }

        LOGGER.warn("Node JS plugins are experimental and are likely to change in future");
        var executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                var srvf = NodeService.get(pluginPath, this.mclient, this.config);

                var srv = srvf.get(30, TimeUnit.SECONDS);

                var record = new PluginRecord<Service<? extends ServiceRequest<?>, ? extends ServiceResponse<?>>>(srv.name(), "description", srv.secured(), true, srv.getClass().getName(), srv, new HashMap<>());

                registry.plugService(record, srv.uri(), srv.matchPolicy(), srv.secured());

                DEPLOYEES.put(pluginPath.toAbsolutePath(), srv);

                LOGGER.info(ansi().fg(GREEN).a("Service '{}' deployed at URI '{}' with description: '{}'. Secured: {}. Uri match policy: {}").reset().toString(), srv.name(), srv.uri(), srv.getDescription(), srv.secured(), srv.matchPolicy());
            } catch (IOException | InterruptedException | ExecutionException | TimeoutException ex) {
                LOGGER.error("Error deploying node service {}", pluginPath, ex);
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        });
        executor.shutdown();
    }

    private void deployInterceptor(Path pluginPath) throws IOException, InterruptedException {
        if (isRunningOnNode()) {
            throw new IllegalStateException("Cannot deploy a CommonJs interceptor, RESTHeart is running on Node");
        }

        var interceptorRecord = this.jsInterceptorFactory.create(pluginPath);

        registry.addInterceptor(interceptorRecord);

        DEPLOYEES.put(pluginPath.toAbsolutePath(), (JSPlugin) interceptorRecord.getInstance());

        LOGGER.info(ansi().fg(GREEN).a("Interceptor '{}' deployed with description: '{}'").reset().toString(),
                interceptorRecord.getName(),
                interceptorRecord.getDescription());
    }

    private void undeploy(Path pluginPath) {
        undeployServices(pluginPath);
        undeployInterceptors(pluginPath);
    }

    private void undeployServices(Path pluginPath) {
        var pathsToUndeploy = DEPLOYEES.keySet().stream()
                .filter(path -> (DEPLOYEES.get(path) instanceof JSService))
                .filter(path -> path.startsWith(pluginPath))
                .collect(Collectors.toList());

        for (var pathToUndeploy : pathsToUndeploy) {
            var _toUndeploy = DEPLOYEES.remove(pathToUndeploy);

            if (_toUndeploy != null && _toUndeploy instanceof JSService toUndeploy) {
                registry.unplug(toUndeploy.uri(), toUndeploy.matchPolicy());

                LOGGER.info(ansi().fg(GREEN).a("Service '{}' bound to '{}' undeployed").reset().toString(), toUndeploy.name(), toUndeploy.uri());
            }
        }
    }

    private void undeployInterceptors(Path pluginPath) {
        var pathsToUndeploy = DEPLOYEES.keySet().stream()
                .filter(path -> DEPLOYEES.get(path) instanceof JSInterceptor)
                .filter(path -> path.startsWith(pluginPath))
                .collect(Collectors.toList());

        for (var pathToUndeploy : pathsToUndeploy) {
            var toUndeploy = DEPLOYEES.remove(pathToUndeploy);
            var removed = registry.removeInterceptorIf(interceptor -> Objects.equal(interceptor.getName(), toUndeploy.name()));

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
