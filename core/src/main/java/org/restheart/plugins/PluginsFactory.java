/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import org.restheart.Bootstrapper;
import org.restheart.ConfigurationException;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authenticator;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.TokenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PluginsFactory implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(PluginsFactory.class);

    private static final String REGISTER_PLUGIN_CLASS_NAME = RegisterPlugin.class
            .getName();

    private static final Map<String, Map<String, Object>> PLUGINS_CONFS
            = consumePluginsConfiguration();

    private static final PluginsFactory SINGLETON = new PluginsFactory();

    public static PluginsFactory getInstance() {
        return SINGLETON;
    }

    private static URL[] findPluginsJars(Path pluginsDirectory) {
        if (pluginsDirectory == null) {
            return new URL[0];
        }

        var urls = new ArrayList<>();

        if (!Files.exists(pluginsDirectory)) {
            LOGGER.error("Plugin directory {} does not exist", pluginsDirectory);
            throw new IllegalStateException("Plugins directory "
                    + pluginsDirectory
                    + " does not exist");
        }

        if (!Files.isReadable(pluginsDirectory)) {
            LOGGER.error("Plugin directory {} is not readable", pluginsDirectory);
            throw new IllegalStateException("Plugins directory "
                    + pluginsDirectory
                    + " is not readable");
        }

        try (DirectoryStream<Path> directoryStream = Files
                .newDirectoryStream(pluginsDirectory, "*.jar")) {
            for (Path path : directoryStream) {
                var jar = path.toUri().toURL();

                if (!Files.isReadable(path)) {
                    LOGGER.error("Plugin jar {} is not readable", jar);
                    throw new IllegalStateException("Plugin jar "
                            + jar
                            + " is not readable");
                }

                urls.add(jar);
                LOGGER.info("Found plugin jar {}", jar);
            }
        } catch (IOException ex) {
            LOGGER.error("Cannot read jars in plugins directory {}",
                    Bootstrapper.getConfiguration().getPluginsDirectory(),
                    ex.getMessage());
        }

        return urls.isEmpty() ? null: urls.toArray(new URL[urls.size()]);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> consumePluginsConfiguration() {
        Map<String, Map<String, Object>> pluginsArgs = Bootstrapper
                .getConfiguration()
                .getPluginsArgs();

        Map<String, Map<String, Object>> confs = new HashMap<>();

        pluginsArgs.forEach((name, params) -> {
            if (params instanceof Map) {
                confs.put(name, (Map) params);
            } else {
                confs.put(name, new HashMap<>());
            }
        });

        return confs;
    }

    private final ScanResult scanResult;
    /**
     * A deque containing the instantiated plugins than have to be injected
     * dependecies. this is done after instantiation because using a dependency,
     * eg the plugin registry, can change the order of plugins instantiation
     * causing all sort of weird issues
     */
    private final Deque<InstatiatedPlugin> PLUGINS_TO_INJECT_DEPS = new LinkedList<>();
    private URLClassLoader PLUGINS_CL_CACHE = null;

    private PluginsFactory() {
        var jars = findPluginsJars(getPluginsDirectory());

        // LOGGER.debug("****** ClassLoader.getSystemClassLoader() {}", ClassLoader.getSystemClassLoader());
        // LOGGER.debug("****** this.getClass().getClassLoader() {}", this.getClass().getClassLoader());
        // LOGGER.debug("****** Thread.currentThread().getContextClassLoader() {}", Thread.currentThread().getContextClassLoader());
        // LOGGER.debug("****** Bootstrapper.class.getClassLoader() {}", Bootstrapper.class.getClassLoader());

        if (jars != null && jars.length != 0) {
            this.scanResult = new ClassGraph()
                    .disableModuleScanning()              // added for GraalVM
                    .disableDirScanning()                 // added for GraalVM
                    .disableNestedJarScanning()           // added for GraalVM
                    .disableRuntimeInvisibleAnnotations() // added for GraalVM
                    .addClassLoader(getPluginsClassloader(jars))
                    .addClassLoader(ClassLoader.getSystemClassLoader()) // see https://github.com/oracle/graal/issues/470#issuecomment-401022008
                    .enableAnnotationInfo()
                    .enableMethodInfo()
                    .initializeLoadedClasses()
                    .scan(8); // use parallel scan for better startup time
        } else {
            this.scanResult = new ClassGraph()
                    .disableModuleScanning()              // added for GraalVM
                    .disableDirScanning()                 // added for GraalVM
                    .disableNestedJarScanning()           // added for GraalVM
                    .disableRuntimeInvisibleAnnotations() // added for GraalVM
                    .addClassLoader(ClassLoader.getSystemClassLoader()) // see https://github.com/oracle/graal/issues/470#issuecomment-401022008
                    .enableAnnotationInfo()
                    .enableMethodInfo()
                    .initializeLoadedClasses()
                    .scan(8); // use parallel scan for better startup time
        }
    }

    @Override
    public void close() {
        if (this.scanResult != null) {
            this.scanResult.close();
        }
    }

    /**
     *
     * @return the AuthenticationMechanisms
     */
    Set<PluginRecord<AuthMechanism>> authMechanisms() {
        return createPlugins(AuthMechanism.class,
                Bootstrapper.getConfiguration().getAuthMechanisms());
    }

    /**
     *
     * @return the Authenticators
     */
    Set<PluginRecord<Authenticator>> authenticators() {
        return createPlugins(Authenticator.class,
                Bootstrapper.getConfiguration().getAuthenticators());
    }

    /**
     *
     * @return the Authorizers
     */
    Set<PluginRecord<Authorizer>> authorizers() {
        return createPlugins(Authorizer.class,
                Bootstrapper.getConfiguration().getAuthorizers());
    }

    /**
     *
     * @return the Token Manager
     */
    PluginRecord<TokenManager> tokenManager() {
        Set<PluginRecord<TokenManager>> tkms = createPlugins(TokenManager.class,
                Bootstrapper.getConfiguration().getTokenManagers());

        if (tkms != null) {
            var tkm = tkms.stream().filter(t -> t.isEnabled()).findFirst();

            if (tkm != null && tkm.isPresent()) {
                return tkm.get();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * create the initializers
     */
    Set<PluginRecord<Initializer>> initializers() {
        return createPlugins(Initializer.class, PLUGINS_CONFS);
    }

    /**
     * creates the interceptors
     */
    @SuppressWarnings("unchecked")
    Set<PluginRecord<Interceptor>> interceptors() {
        return createPlugins(Interceptor.class, PLUGINS_CONFS);
    }

    private ClassInfoList getRegisteredPlugins(Class type) {
        // scanResult is null if the plugins directory is empty
        if (this.scanResult == null) {
            return null;
        }

        try {
            return this.scanResult.getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);
        } catch (Throwable t) {
            LOGGER.error("Error deploying plugins: {}", t.getMessage());
            throw t;
        }
    }

    /**
     * creates the services
     */
    @SuppressWarnings("unchecked")
    Set<PluginRecord<Service>> services() {
        return createPlugins(Service.class, PLUGINS_CONFS);
    }

    /**
     * @param type the class of the plugin , e.g. Initializer.class
     */
    @SuppressWarnings("unchecked")
    private <T extends Plugin> Set<PluginRecord<T>> createPlugins(
            Class type, Map<String, Map<String, Object>> confs) {
        Set<PluginRecord<T>> ret = new LinkedHashSet<>();

        // scanResult is null if the plugins directory is empty
        if (this.scanResult == null) {
            return ret;
        }

        ClassInfoList registeredPlugins = getRegisteredPlugins(type);

        if (registeredPlugins == null || registeredPlugins.isEmpty()) {
            return ret;
        }

        ClassInfoList listOfType;

        if (type.isInterface()) {
            if (type.equals(Authenticator.class)) {
                var tms = scanResult.getClassesImplementing(TokenManager.class.getName());

                listOfType = scanResult
                        .getClassesImplementing(type.getName())
                        .exclude(tms);
            } else {
                listOfType = scanResult.getClassesImplementing(type.getName());
            }
        } else {
            listOfType = scanResult.getSubclasses(type.getName());
        }

        LOGGER.debug("***** registeredPlugins of type {}: {}",
            type.getSimpleName(),
            listOfType.getNames());

        var plugins = registeredPlugins.intersect(listOfType);

        // sort by priority
        plugins.sort((ClassInfo ci1, ClassInfo ci2) -> {
            return Integer.compare(annotationParam(ci1, "priority"),
                    annotationParam(ci2, "priority"));
        });

        plugins.stream().forEachOrdered(plugin -> {
            Object i;
            var _type = type.getSimpleName();

            try {
                String name = annotationParam(plugin, "name");
                String description = annotationParam(plugin, "description");
                Boolean enabledByDefault = annotationParam(plugin, "enabledByDefault");

                var enabled = PluginRecord.isEnabled(enabledByDefault,
                        confs != null ? confs.get(name) : null);

                if (enabled) {
                    i = instantiatePlugin(plugin, _type, name, confs);

                    var pr = new PluginRecord(
                            name,
                            description,
                            enabledByDefault,
                            plugin.getName(),
                            (T) i,
                            confs != null
                                    ? confs.get(name)
                                    : null);

                    if (pr.isEnabled()) {
                        ret.add(pr);
                        LOGGER.debug("Registered {} {}: {}",
                                _type,
                                name,
                                description);
                    }
                } else {
                    LOGGER.debug("{} {} is disabled", _type, name);
                }
            } catch (ConfigurationException
                    | InstantiationException
                    | IllegalAccessException
                    | InvocationTargetException t) {
                LOGGER.error("Error registering {} {}: {}",
                        _type,
                        annotationParam(plugin, "name") != null
                        ? annotationParam(plugin, "name")
                        : plugin.getSimpleName(),
                        getRootException(t).getMessage(),
                        t);
            }
        });

        return ret;
    }

    private Plugin instantiatePlugin(
            ClassInfo pluginClassInfo,
            String pluginType,
            String pluginName,
            Map confs)
            throws ConfigurationException,
            InstantiationException,
            IllegalAccessException,
            InvocationTargetException {
        final Plugin plugin;

        try {
            plugin = (Plugin) pluginClassInfo.loadClass(false)
                    .getDeclaredConstructor()
                    .newInstance();

            PLUGINS_TO_INJECT_DEPS.add(
                    new InstatiatedPlugin(pluginName,
                            pluginType,
                            pluginClassInfo,
                            plugin,
                            confs));
        } catch (NoSuchMethodException nme) {
            throw new ConfigurationException(
                    pluginType
                    + " " + pluginName
                    + " does not have default constructor "
                    + pluginClassInfo.getSimpleName()
                    + "()");
        }

        return plugin;
    }

    public void injectDependencies() {
        for (Iterator<InstatiatedPlugin> it = PLUGINS_TO_INJECT_DEPS.iterator();
                it.hasNext();) {
            var ip = it.next();

            try {
                invokeInjectMethods(ip);
            } catch (InvocationTargetException ite) {
                if (ite.getCause() != null
                        && ite.getCause() instanceof NoClassDefFoundError) {
                    var errMsg = "Error handling the request. "
                            + "An external dependency is missing for "
                            + ip.pluginType
                            + " "
                            + ip.pluginName
                            + ". Copy the missing dependency jar to the plugins directory "
                            + "to add it to the classpath";

                    LOGGER.error(errMsg, ite);
                } else {
                    LOGGER.error("Error injecting dependency to {} {}: {}",
                            ip.pluginType,
                            ip.pluginName,
                            getRootException(ite).getMessage(),
                            ite);
                }
            } catch (ConfigurationException
                    | InstantiationException
                    | IllegalAccessException ex) {
                LOGGER.error("Error injecting dependency to {} {}: {}",
                        ip.pluginType,
                        ip.pluginName,
                        getRootException(ex).getMessage(),
                        ex);
            }

            it.remove();
        }
    }

    private void invokeInjectMethods(InstatiatedPlugin ip) throws ConfigurationException,
            InstantiationException,
            IllegalAccessException,
            InvocationTargetException {
        invokeInjectConfigurationMethods(ip.pluginName,
                ip.pluginType,
                ip.pluginClassInfo,
                ip.pluingInstance,
                ip.confs);

        invokeInjectPluginsRegistryMethods(ip.pluginName,
                ip.pluginType,
                ip.pluginClassInfo,
                ip.pluingInstance);

        invokeInjectConfigurationAndPluginsRegistryMethods(ip.pluginName,
                ip.pluginType,
                ip.pluginClassInfo,
                ip.pluingInstance,
                ip.confs);
    }

    private void invokeInjectConfigurationMethods(String pluginName,
            String pluginType,
            ClassInfo pluginClassInfo,
            Object pluingInstance,
            Map confs) throws ConfigurationException,
            InstantiationException,
            IllegalAccessException,
            InvocationTargetException {

        // finds @InjectConfiguration methods
        var mil = pluginClassInfo.getDeclaredMethodInfo();

        for (var mi : mil) {
            if (mi.hasAnnotation(InjectConfiguration.class.getName())
                    && !mi.hasAnnotation(InjectPluginsRegistry.class.getName())) {
                var ai = mi.getAnnotationInfo(InjectConfiguration.class.getName());

                // check configuration scope
                var allConfScope = ai.getParameterValues().stream()
                        .anyMatch(p -> "scope".equals(p.getName())
                        && (ConfigurationScope.class.getName()
                                + "." + ConfigurationScope.ALL.name()).equals(
                                p.getValue().toString()));

                var scopedConf = (Map) (allConfScope
                        ? Bootstrapper.getConfiguration().toMap()
                        : confs != null
                                ? confs.get(pluginName)
                                : null);

                if (scopedConf == null) {
                    LOGGER.warn("{} {} defines method {} with @InjectConfiguration "
                            + "but no configuration found for it",
                            pluginType,
                            pluginName,
                            mi.getName());
                }

                // try to inovke @InjectConfiguration method
                try {
                    pluginClassInfo.loadClass(false)
                            .getDeclaredMethod(mi.getName(),
                                    Map.class)
                            .invoke(pluingInstance, scopedConf);
                } catch (NoSuchMethodException nme) {
                    throw new ConfigurationException(
                            pluginType
                            + " " + pluginName
                            + " has an invalid method with @InjectConfiguration. "
                            + "Method signature must be "
                            + mi.getName()
                            + "(Map<String, Object> configuration)");
                }
            }
        }
    }

    private void invokeInjectPluginsRegistryMethods(String pluginName,
            String pluginType,
            ClassInfo pluginClassInfo,
            Object pluingInstance) throws ConfigurationException,
            InstantiationException,
            IllegalAccessException,
            InvocationTargetException {

        // finds @InjectPluginRegistry methods
        var mil = pluginClassInfo.getDeclaredMethodInfo();

        for (var mi : mil) {
            if (mi.hasAnnotation(InjectPluginsRegistry.class.getName())
                    && !mi.hasAnnotation(InjectConfiguration.class.getName())) {
                // try to inovke @InjectPluginRegistry method
                try {
                    pluginClassInfo.loadClass(false)
                            .getDeclaredMethod(mi.getName(),
                                    PluginsRegistry.class)
                            .invoke(pluingInstance,
                                    PluginsRegistryImpl.getInstance());
                } catch (NoSuchMethodException nme) {
                    throw new ConfigurationException(
                            pluginType
                            + " " + pluginName
                            + " has an invalid method with @InjectPluginsRegistry. "
                            + "Method signature must be "
                            + mi.getName()
                            + "(PluginsRegistry pluginsRegistry)");
                }
            }
        }
    }

    private void invokeInjectConfigurationAndPluginsRegistryMethods(String pluginName,
            String pluginType,
            ClassInfo pluginClassInfo,
            Object pluingInstance,
            Map confs) throws ConfigurationException,
            InstantiationException,
            IllegalAccessException,
            InvocationTargetException {

        // finds @InjectConfiguration methods
        var mil = pluginClassInfo.getDeclaredMethodInfo();

        for (var mi : mil) {
            if (mi.hasAnnotation(InjectConfiguration.class.getName())
                    && mi.hasAnnotation(InjectPluginsRegistry.class.getName())) {
                var ai = mi.getAnnotationInfo(InjectConfiguration.class.getName());

                // check configuration scope
                var allConfScope = ai.getParameterValues().stream()
                        .anyMatch(p -> "scope".equals(p.getName())
                        && (ConfigurationScope.class.getName()
                                + "." + ConfigurationScope.ALL.name()).equals(
                                p.getValue().toString()));

                var scopedConf = (Map) (allConfScope
                        ? Bootstrapper.getConfiguration().toMap()
                        : confs != null
                                ? confs.get(pluginName)
                                : null);

                if (scopedConf == null) {
                    LOGGER.warn("{} {} defines method {} with @InjectConfiguration "
                            + "but no configuration found for it",
                            pluginType,
                            pluginName,
                            mi.getName());
                }

                // try to inovke @InjectConfiguration method
                try {
                    pluginClassInfo.loadClass(false)
                            .getDeclaredMethod(mi.getName(),
                                    Map.class, PluginsRegistry.class)
                            .invoke(pluingInstance, scopedConf,
                                    PluginsRegistryImpl.getInstance());
                } catch (NoSuchMethodException nme) {
                    throw new ConfigurationException(
                            pluginType
                            + " " + pluginName
                            + " has an invalid method with @InjectConfiguration"
                            + " and @InjectPluginsRegistry."
                            + " Method signature must be "
                            + mi.getName()
                            + "(Map<String, Object> configuration,"
                            + " PluginsRegistry pluginsRegistry)");
                }
            }
        }
    }

    private Throwable getRootException(Throwable t) {
        if (t.getCause() != null) {
            return getRootException(t.getCause());
        } else {
            return t;
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Object> T annotationParam(ClassInfo ci, String param) {
        var annotationInfo = ci.getAnnotationInfo(REGISTER_PLUGIN_CLASS_NAME);
        var annotationParamVals = annotationInfo.getParameterValues();

        // TO BE REMOVED!!!!
        if (annotationParamVals.getValue(param) == null) {
            // Added to debug GraalVM
            LOGGER.warn("****** {}.RegisterPlugin.{}=null (returning a test value)", ci.getSimpleName(), param);
            if ("enabledByDefault".equals(param)) {
                return  (T) Boolean.TRUE;
            } else if ("priority".equals(param)) {
                return(T) Integer.valueOf(0);
            } else {
                return (T) "Ops";
            }
        } else {
            LOGGER.trace("****** {}.RegisterPlugin.{}={}", ci.getSimpleName(), param, annotationParamVals.getValue(param));
        }

        return (T) annotationParamVals.getValue(param);
    }

    private Path getPluginsDirectory() {
        var pluginsDir = Bootstrapper.getConfiguration().getPluginsDirectory();

        if (pluginsDir == null) {
            return null;
        }

        if (pluginsDir.startsWith("/")) {
            return Paths.get(pluginsDir);
        } else {
            // this is to allow specifying the plugins directory path
            // relative to the jar (also working when running from classes)
            URL location = PluginsFactory.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation();

            File locationFile = new File(location.getPath());

            pluginsDir = locationFile.getParent()
                    + File.separator
                    + pluginsDir;

            return FileSystems.getDefault().getPath(pluginsDir);
        }
    }

    /**
     *
     * @return the URLClassLoader that resolve plugins classes
     */
    private URLClassLoader getPluginsClassloader(URL[] jars) {
        if (PLUGINS_CL_CACHE == null) {
            PLUGINS_CL_CACHE = new URLClassLoader(jars);
        }

        return PLUGINS_CL_CACHE;
    }

    private static class InstatiatedPlugin {

        private final String pluginName;
        private final String pluginType;
        private final ClassInfo pluginClassInfo;
        private final Object pluingInstance;
        private final Map confs;

        InstatiatedPlugin(String pluginName,
                String pluginType,
                ClassInfo pluginClassInfo,
                Object pluingInstance,
                Map confs) {
            this.pluginName = pluginName;
            this.pluginType = pluginType;
            this.pluginClassInfo = pluginClassInfo;
            this.pluingInstance = pluingInstance;
            this.confs = confs;
        }
    }
}
