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

import io.github.classgraph.AnnotationEnumValue;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.AbstractMap;

import org.restheart.graal.ImageInfo;

import org.restheart.Bootstrapper;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authenticator;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.TokenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this class is configured to be initialized at build time by native-image
 * note: we cannot use logging in this class, otherwise native-image will fail
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PluginsScanner {
    private static final String REGISTER_PLUGIN_CLASS_NAME = RegisterPlugin.class.getName();

    private static final String INITIALIZER_CLASS_NAME = Initializer.class.getName();
    private static final String AUTHMECHANISM_CLASS_NAME = AuthMechanism.class.getName();
    private static final String AUTHORIZER_CLASS_NAME = Authorizer.class.getName();
    private static final String TOKEN_MANAGER_CLASS_NAME = TokenManager.class.getName();
    private static final String AUTHENTICATOR_CLASS_NAME = Authenticator.class.getName();
    private static final String INTERCEPTOR_CLASS_NAME = Interceptor.class.getName();
    private static final String SERVICE_CLASS_NAME = Service.class.getName();
    private static final String PROVIDER_CLASS_NAME = Provider.class.getName();

    private static final ArrayList<PluginDescriptor> INITIALIZERS = new ArrayList<>();
    private static final ArrayList<PluginDescriptor> AUTH_MECHANISMS = new ArrayList<>();
    private static final ArrayList<PluginDescriptor> AUTHORIZERS = new ArrayList<>();
    private static final ArrayList<PluginDescriptor> TOKEN_MANAGERS = new ArrayList<>();
    private static final ArrayList<PluginDescriptor> AUTHENTICATORS = new ArrayList<>();
    private static final ArrayList<PluginDescriptor> INTERCEPTORS = new ArrayList<>();
    private static final ArrayList<PluginDescriptor> SERVICES = new ArrayList<>();
    private static final ArrayList<PluginDescriptor> PROVIDERS = new ArrayList<>();

    static {
        if (!ImageInfo.inImageBuildtimeCode()) {
            var rtcg = new RuntimeClassGraph();
            var classGraph = rtcg.get();
            // apply plugins-scanning-verbose configuration option
            classGraph = classGraph.verbose(Bootstrapper.getConfiguration().coreModule().pluginsScanningVerbose());
            // apply plugins-packages configuration option
            var pluginsPackages = Bootstrapper.getConfiguration().coreModule().pluginsPackages();
            if (!Bootstrapper.getConfiguration().coreModule().pluginsPackages().isEmpty()) {
                classGraph = classGraph.acceptPackages(pluginsPackages.toArray(String[]::new));
            }

            rtcg.logStartScan();

            try (var scanResult = classGraph.scan(Runtime.getRuntime().availableProcessors())) {
                INITIALIZERS.addAll(collectPlugins(scanResult, INITIALIZER_CLASS_NAME));
                AUTH_MECHANISMS.addAll(collectPlugins(scanResult, AUTHMECHANISM_CLASS_NAME));
                AUTHORIZERS.addAll(collectPlugins(scanResult, AUTHORIZER_CLASS_NAME));
                TOKEN_MANAGERS.addAll(collectPlugins(scanResult, TOKEN_MANAGER_CLASS_NAME));
                AUTHENTICATORS.addAll(collectPlugins(scanResult, AUTHENTICATOR_CLASS_NAME));
                INTERCEPTORS.addAll(collectPlugins(scanResult, INTERCEPTOR_CLASS_NAME));
                SERVICES.addAll(collectPlugins(scanResult, SERVICE_CLASS_NAME));
                PROVIDERS.addAll(collectProviders(scanResult));
            }

            rtcg.logEndScan();
        }
    }

    // ClassGraph.scan() at class initialization time to support native image
    // generation with GraalVM
    // see https://github.com/SoftInstigate/classgraph-on-graalvm
    public static void initAtBuildTime() {
         if (!ImageInfo.inImageBuildtimeCode()) {
            throw new IllegalStateException("Called initAtBuildTime() but we are not at build time");
         }

        // requires PluginsClassloader being initialized
        // this is done by PluginsClassloaderInitFeature

        final var cg = new ClassGraph();

        var classGraph = cg
            .disableDirScanning() // added for GraalVM
            .disableNestedJarScanning() // added for GraalVM
            .disableRuntimeInvisibleAnnotations() // added for GraalVM
            .overrideClassLoaders(PluginsClassloader.getInstance()) // added for GraalVM. Mandatory, otherwise build fails
            .ignoreParentClassLoaders()
            .enableAnnotationInfo().enableMethodInfo().enableFieldInfo().ignoreFieldVisibility().initializeLoadedClasses();

        System.out.println("[PluginsScanner] Scanning plugins at build time with following classpath: " + cg.getClasspathURIs().stream().map(uri -> uri.getPath()).collect(Collectors.joining(File.pathSeparator)));

        try (var scanResult = classGraph.scan(Runtime.getRuntime().availableProcessors())) {
            INITIALIZERS.addAll(collectPlugins(scanResult, INITIALIZER_CLASS_NAME));
            AUTH_MECHANISMS.addAll(collectPlugins(scanResult, AUTHMECHANISM_CLASS_NAME));
            AUTHORIZERS.addAll(collectPlugins(scanResult, AUTHORIZER_CLASS_NAME));
            TOKEN_MANAGERS.addAll(collectPlugins(scanResult, TOKEN_MANAGER_CLASS_NAME));
            AUTHENTICATORS.addAll(collectPlugins(scanResult, AUTHENTICATOR_CLASS_NAME));
            INTERCEPTORS.addAll(collectPlugins(scanResult, INTERCEPTOR_CLASS_NAME));
            SERVICES.addAll(collectPlugins(scanResult, SERVICE_CLASS_NAME));
            PROVIDERS.addAll(collectProviders(scanResult));
        }
    }

    public static List<String> allPluginsClassNames() {
        var ret = new ArrayList<String>();
        INITIALIZERS.stream().map(p -> p.clazz()).forEachOrdered(ret::add);
        AUTH_MECHANISMS.stream().map(p -> p.clazz()).forEachOrdered(ret::add);
        AUTHORIZERS.stream().map(p -> p.clazz()).forEachOrdered(ret::add);
        TOKEN_MANAGERS.stream().map(p -> p.clazz()).forEachOrdered(ret::add);
        AUTHENTICATORS.stream().map(p -> p.clazz()).forEachOrdered(ret::add);
        INTERCEPTORS.stream().map(p -> p.clazz()).forEachOrdered(ret::add);
        SERVICES.stream().map(p -> p.clazz()).forEachOrdered(ret::add);
        PROVIDERS.stream().map(p -> p.clazz()).forEachOrdered(ret::add);

        return ret;
    }

    static final List<PluginDescriptor> providers() {
        return PROVIDERS;
    }

    static final List<PluginDescriptor> initializers() {
        return INITIALIZERS;
    }

    static final List<PluginDescriptor> authMechanisms() {
        return AUTH_MECHANISMS;
    }

    static final List<PluginDescriptor> authorizers() {
        return AUTHORIZERS;
    }

    static final List<PluginDescriptor> tokenManagers() {
        return TOKEN_MANAGERS;
    }

    static final List<PluginDescriptor> authenticators() {
        return AUTHENTICATORS;
    }

    static final List<PluginDescriptor> interceptors() {
        return INTERCEPTORS;
    }

    static final List<PluginDescriptor> services() {
        return SERVICES;
    }

    /**
     * @param type the class of the plugin , e.g. Initializer.class
     */
    private static List<PluginDescriptor> collectPlugins(ScanResult scanResult, String className) {
        var ret = new ArrayList<PluginDescriptor>();

        var registeredPlugins = scanResult.getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);

        if (registeredPlugins == null || registeredPlugins.isEmpty()) {
            return ret;
        }

        ClassInfoList listOfType;

        if (className.equals(AUTHENTICATOR_CLASS_NAME)) {
            var tms = scanResult.getClassesImplementing(TOKEN_MANAGER_CLASS_NAME);

            listOfType = scanResult.getClassesImplementing(className).exclude(tms);
        } else {
            listOfType = scanResult.getClassesImplementing(className);
        }

        var plugins = registeredPlugins.intersect(listOfType);

        return plugins.stream().map(c -> descriptor(c)).collect(Collectors.toList());
    }

    /**
     *
     */
    private static List<PluginDescriptor> collectProviders(ScanResult scanResult) {
        var ret = new ArrayList<PluginDescriptor>();

        var providers = scanResult.getClassesImplementing(PROVIDER_CLASS_NAME);

        if (providers == null || providers.isEmpty()) {
            return ret;
        }

        return providers.stream().map(c -> descriptor(c)).collect(Collectors.toList());
    }

    private static PluginDescriptor descriptor(ClassInfo pluginClassInfo) {
        var clazz = pluginClassInfo.getName();
        var name = pluginClassInfo.getAnnotationInfo(REGISTER_PLUGIN_CLASS_NAME).getParameterValues().stream()
                .filter(p -> "name".equals(p.getName())).map(p -> p.getValue()).findAny().get().toString();

        return new PluginDescriptor(name, clazz, isEnabled(name, pluginClassInfo), collectInjections(pluginClassInfo));
    }

    private static ArrayList<InjectionDescriptor> collectInjections(ClassInfo pluginClassInfo) {
        var ret = new ArrayList<InjectionDescriptor>();

        ret.addAll(collectFieldInjections(pluginClassInfo, Inject.class));
        ret.addAll(collectMethodInjections(pluginClassInfo, OnInit.class));

        return ret;
    }

    /**
     * NOTE:returns true at build time, to force native compilation of
     * all plugins
     *
     * @param name
     * @param pluginClassInfo
     * @return true if the plugin is enabled, taking into account enabledByDefault and its configuration
     */
    private static boolean isEnabled(String name, ClassInfo pluginClassInfo) {
        if (ImageInfo.inImageBuildtimeCode()) {
            return true;
        } else {
            var isEnabledByDefault = (boolean) pluginClassInfo.getAnnotationInfo(REGISTER_PLUGIN_CLASS_NAME).getParameterValues().stream()
                .filter(p -> "enabledByDefault".equals(p.getName())).map(p -> p.getValue()).findAny().get();

            Map<String, Object> confArgs = Bootstrapper.getConfiguration().getOrDefault(name, null);
            return PluginRecord.isEnabled(isEnabledByDefault, confArgs);
        }
    }

    private static ArrayList<InjectionDescriptor> collectMethodInjections(ClassInfo pluginClassInfo, Class<?> clazz) {
        var ret = new ArrayList<InjectionDescriptor>();

        var mil = pluginClassInfo.getDeclaredMethodInfo();

        for (var mi : mil) {
            if (mi.hasAnnotation(clazz.getName())) {
                ArrayList<AbstractMap.SimpleEntry<String, Object>> annotationParams = new ArrayList<>();
                for (var p : mi.getAnnotationInfo(clazz.getName()).getParameterValues()) {
                    var value = p.getValue();
                    if (value instanceof AnnotationEnumValue annotationEnumValue) {
                        removeRefToScanResult(annotationEnumValue);
                    }
                    annotationParams.add(new AbstractMap.SimpleEntry<>(p.getName(), value));
                }

                var methodParams = new ArrayList<String>();

                Arrays.stream(mi.getParameterInfo()).forEachOrdered(pi -> methodParams.add(pi.getTypeDescriptor().toString()));

                ret.add(new MethodInjectionDescriptor(mi.getName(), clazz, annotationParams, methodParams, mi.hashCode()));
            }
        }

        return ret;
    }

    private static ArrayList<InjectionDescriptor> collectFieldInjections(ClassInfo pluginClassInfo, Class<?> clazz) {
        var ret = new ArrayList<InjectionDescriptor>();

        var fil = pluginClassInfo.getDeclaredFieldInfo();

        for (var fi : fil) {
            if (fi.hasAnnotation(clazz.getName())) {
                var annotationParams = new ArrayList<AbstractMap.SimpleEntry<String, Object>>();
                for (var p : fi.getAnnotationInfo(clazz.getName()).getParameterValues()) {
                    var value = p.getValue();
                    if (value instanceof AnnotationEnumValue annotationEnumValue) {
                        removeRefToScanResult(annotationEnumValue);
                    }
                    annotationParams.add(new AbstractMap.SimpleEntry<>(p.getName(), value));
                }

                try {
                    var fieldClass = PluginsClassloader.getInstance().loadClass(fi.getTypeDescriptor().toString());
                    ret.add(new FieldInjectionDescriptor(fi.getName(), fieldClass, annotationParams, fi.hashCode()));
                } catch(ClassNotFoundException cnfe) {
                    // should not happen
                    throw new RuntimeException(cnfe);
                }
            }
        }

        return ret;
    }

    /**
     * this removes the reference to scanResult in the annotation info
     * otherwise the huge object won't be garbage collected
     *
     * @param obj
     */
    private static void removeRefToScanResult(AnnotationEnumValue obj) {
        try {
            var f = AnnotationEnumValue.class.getSuperclass().getDeclaredField("scanResult");
            f.setAccessible(true);
            f.set(obj, null);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException ex) {
            // nothing to do
        }
    }

    static class RuntimeClassGraph {
        private static final Logger LOGGER = LoggerFactory.getLogger(PluginsScanner.class);

        private final ClassGraph classGraph;

        URL[] jars = null;

        public RuntimeClassGraph() {
            var pdir = getPluginsDirectory();
            this.jars = findPluginsJars(pdir);

            if (!PluginsClassloader.isInitialized()) {
                PluginsClassloader.init(this.jars);
            }

            var libJars = Arrays.stream(this.jars)
                .map(jar -> jar.getPath())
                .map(path -> Path.of(path))
                .filter(jar -> isLibJar(jar))
                .map(path -> path.getFileName().toString())
                .toArray(String[]::new);

            this.classGraph = new ClassGraph().disableModuleScanning().disableDirScanning()
                .disableNestedJarScanning()
                .disableRuntimeInvisibleAnnotations()
                .addClassLoader(PluginsClassloader.getInstance())
                .addClassLoader(ClassLoader.getSystemClassLoader())
                .rejectJars(libJars) // avoids scanning lib jars
                .enableAnnotationInfo()
                .enableMethodInfo()
                .enableFieldInfo()
                .ignoreFieldVisibility()
                .initializeLoadedClasses();
        }

        private long starScanTime = 0;
        private long endScanTime = 0;

        public void logStartScan() {
            LOGGER.info("Scanning jars for plugins started");
            this.starScanTime = System.currentTimeMillis();
        }

        public void logEndScan() {
            this.endScanTime = System.currentTimeMillis();
            LOGGER.info("Scanning jars for plugins completed in {} msec", endScanTime-starScanTime);
        }

        public ClassGraph get() {
            return this.classGraph;
        }

        public static Path getPluginsDirectory() {
            var pluginsDir = Bootstrapper.getConfiguration().coreModule().pluginsDirectory();

            if (pluginsDir == null) {
                return null;
            }

            if (pluginsDir.startsWith("/")) {
                return Paths.get(pluginsDir);
            } else {
                // this is to allow specifying the plugins directory path
                // relative to the jar (also working when running from classes)
                var location = PluginsFactory.class.getProtectionDomain().getCodeSource().getLocation();

                try {
                    var decodedLocation = URLDecoder.decode(location.getPath(), StandardCharsets.UTF_8.toString());

                    var locationFile = new File(decodedLocation);

                    pluginsDir = locationFile.getParent() + File.separator + pluginsDir;

                    return FileSystems.getDefault().getPath(pluginsDir);
                } catch(UnsupportedEncodingException uee) {
                    throw new RuntimeException(uee);
                }
            }
        }

        private URL[] findPluginsJars(Path pluginsDirectory) {
            return _findPluginsJars(pluginsDirectory, 0);
        }

        private URL[] _findPluginsJars(Path dir, int depth) {
            var pluginsPackages = Bootstrapper.getConfiguration().coreModule().pluginsPackages();
            if (!pluginsPackages.isEmpty()) {
                LOGGER.info("Limiting the scanning of plugins to packages {}", pluginsPackages);
            }
            if (dir == null) {
                return new URL[0];
            } else {
                try {
                    checkPluginDirectory(dir);
                } catch(IllegalStateException ise) {
                    return new URL[0];
                }
            }

            var urls = new ArrayList<URL>();

            try (var ds = Files.newDirectoryStream(dir, "*.jar")) {
                for (Path path : ds) {
                    var jar = path.toUri().toURL();

                    if (!Files.isReadable(path)) {
                        LOGGER.error("Plugin jar {} is not readable", jar);
                        throw new IllegalStateException("Plugin jar " + jar + " is not readable");
                    }

                    urls.add(jar);

                    if (isLibJar(path)) {
                        LOGGER.debug("Found lib jar {}", URLDecoder.decode(jar.getPath(), StandardCharsets.UTF_8.toString()));
                    } else {
                        LOGGER.info("Found plugin jar {}", URLDecoder.decode(jar.getPath(), StandardCharsets.UTF_8.toString()));
                    }
                }
            } catch (IOException ex) {
                LOGGER.error("Cannot read jars in plugins directory {}", Bootstrapper.getConfiguration().coreModule().pluginsDirectory(), ex.getMessage());
            }

            // Scans the plugins directory up to two levels deep
            if (depth < 2) {
                try (var ds = Files.newDirectoryStream(dir, (Filter<Path>) (Path entry) -> Files.isDirectory(entry))) {
                    for (Path subdir : ds) {
                        if (Files.isReadable(subdir)) {
                            var subjars = _findPluginsJars(subdir, depth + 1);
                            if (subjars != null && subjars.length > 0) {
                                Arrays.stream(subjars).forEach(jar -> urls.add(jar));
                            }
                        } else {
                            LOGGER.warn("Subdirectory {} of plugins directory {} is not readable", subdir, Bootstrapper.getConfiguration().coreModule().pluginsDirectory());
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.error("Cannot read jars in plugins subdirectory", ex.getMessage());
                }
            }

            return urls.toArray(URL[]::new);
        }

        /**
         *
         */
        private void checkPluginDirectory(Path pluginsDirectory) {
            if (!Files.exists(pluginsDirectory)) {
                LOGGER.warn("Plugin directory {} does not exist", pluginsDirectory);
                throw new IllegalStateException("Plugins directory " + pluginsDirectory + " does not exist");
            }

            if (!Files.isReadable(pluginsDirectory)) {
                LOGGER.warn("Plugin directory {} is not readable", pluginsDirectory);
                throw new IllegalStateException("Plugins directory " + pluginsDirectory + " is not readable");
            }
        }

        /**
         * Determines whether the given JAR file is classified as a library.
         * A JAR is considered a library if it is located within a subdirectory of the plugins direcory
         * whose relative path contains "lib", "-lib", or "_lib" as part of the directory name.
         *
         * @param path the path of the JAR file to be checked
         * @return {@code true} if the JAR file is under a subdirectory of the plugins directory that contains "lib", "-lib",
         *         or "_lib" in its relative path; {@code false} otherwise
         */
        private boolean isLibJar(Path path) {
            var pluginsDirectory = getPluginsDirectory();

            try {
                var rpath = pluginsDirectory.relativize(path).toString();

                /*
                 * This regular expression matches paths containing directories with names
                 * that are exactly "lib", "<prefix>-lib", or "<prefix>_lib".
                 *
                 * The match works for both absolute and relative paths.
                 *
                 * The pattern is explained as follows:
                 *
                 * (^|.*[\\/\\\\]) Matches either the beginning of the string
                 *       (for relative paths like lib/pippo.jar or my-lib/pippo.jar),
                 *       or any preceding directories in the path for absolute paths.
                 *
                 * ([^\\/\\\\]+[-_])? Optionally matches a prefix consisting of one or more characters,
                 *       excluding directory separators, followed by either a hyphen ("-") or underscore ("_").
                 *
                 * lib Matches the literal string "lib"
                 *
                 * ([\\/\\\\].*|$) Matches either a directory separator followed by any characters
                 *
                 * ([\\/\\\\].*) or the end of the string ($).
                 */
                return rpath.matches("(^|.*[\\/\\\\])([^\\/\\\\]+[-_])?lib([\\/\\\\].*|$)");
            } catch(IllegalArgumentException iae) {
                return false;
            }
        }
    }
}

record PluginDescriptor(String name, String clazz, boolean enabled, ArrayList<InjectionDescriptor> injections) {}
interface InjectionDescriptor {}

record MethodInjectionDescriptor(String method, Class<?> clazz, ArrayList<AbstractMap.SimpleEntry<String, Object>> annotationParams, ArrayList<String> methodParams, int methodHash) implements InjectionDescriptor {}

record FieldInjectionDescriptor(String field, Class<?> clazz, ArrayList<AbstractMap.SimpleEntry<String, Object>> annotationParams, int fieldHash) implements InjectionDescriptor {}
