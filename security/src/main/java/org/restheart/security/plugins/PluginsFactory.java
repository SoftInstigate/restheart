/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.plugins;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.restheart.ConfigurationException;
import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.Plugin;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authenticator;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.Initializer;
import org.restheart.plugins.security.Interceptor;
import org.restheart.plugins.security.PreStartupInitializer;
import org.restheart.plugins.security.Service;
import org.restheart.plugins.security.TokenManager;
import org.restheart.security.Bootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PluginsFactory {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(PluginsFactory.class);

    private static final String REGISTER_PLUGIN_CLASS_NAME = RegisterPlugin.class
            .getName();

    private static final Map<String, Map<String, Object>> ARGS_CONFS
            = consumePluginsConfiguration();

    /**
     *
     * @return the AuthenticationMechanisms
     */
    static Set<PluginRecord<AuthMechanism>> authMechanisms() {
        return createPlugins(AuthMechanism.class,
                Bootstrapper.getConfiguration().getAuthMechanisms());
    }

    /**
     *
     * @return the Authenticators
     */
    static Set<PluginRecord<Authenticator>> authenticators() {
        return createPlugins(Authenticator.class,
                Bootstrapper.getConfiguration().getAuthenticators());
    }

    /**
     *
     * @return the Authorizers
     */
    static Set<PluginRecord<Authorizer>> authorizers() {
        return createPlugins(Authorizer.class,
                Bootstrapper.getConfiguration().getAuthorizers());
    }

    /**
     *
     * @return the Token Manager
     */
    static PluginRecord<TokenManager> tokenManager() {
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
    static Set<PluginRecord<Initializer>> initializers() {
        return createPlugins(Initializer.class, ARGS_CONFS);
    }

    /**
     * create the pre statup initializers
     */
    static Set<PluginRecord<PreStartupInitializer>> preStartupInitializers() {
        return createPlugins(PreStartupInitializer.class, ARGS_CONFS);
    }

    /**
     * creates the interceptors
     */
    @SuppressWarnings("unchecked")
    static Set<PluginRecord<Interceptor>> interceptors() {
        return createPlugins(Interceptor.class, ARGS_CONFS);
    }

    /**
     * creates the services
     */
    @SuppressWarnings("unchecked")
    static Set<PluginRecord<Service>> services() {
        return createPlugins(Service.class, ARGS_CONFS);
    }

    /**
     * @param type the class of the plugin , e.g. Initializer.class
     */
    @SuppressWarnings("unchecked")
    private static <T extends Plugin> Set<PluginRecord<T>> createPlugins(
            Class type, Map<String, Map<String, Object>> confs) {
        Set<PluginRecord<T>> ret = new LinkedHashSet<>();

        var _type = type.getSimpleName();

        try (var scanResult = new ClassGraph()
                .addClassLoader(getPluginsClassloader())
                .enableAnnotationInfo()
                .enableMethodInfo()
                .scan()) {
            var registeredPlugins = scanResult
                    .getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);

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

            var plugins = registeredPlugins.intersect(listOfType);

            // sort by priority
            plugins.sort((ClassInfo ci1, ClassInfo ci2) -> {
                return Integer.compare(annotationParam(ci1, "priority"),
                        annotationParam(ci2, "priority"));
            });

            plugins.stream().forEachOrdered(plugin -> {
                Object i;

                try {
                    String name = annotationParam(plugin,
                            "name");
                    String description = annotationParam(plugin,
                            "description");
                    Boolean enabledByDefault = annotationParam(plugin,
                            "enabledByDefault");

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
                            LOGGER.info("Registered {} {}: {}",
                                    _type,
                                    name,
                                    description);
                        }
                    } else {
                        LOGGER.debug("{} {} is disabled", _type, name);
                    }
                }
                catch (ConfigurationException
                        | InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException t) {
                    LOGGER.error("Error registering {} {}: {}",
                            _type,
                            annotationParam(plugin, "name") != null
                            ? (String) annotationParam(plugin, "name")
                            : plugin.getSimpleName(),
                            getRootException(t).getMessage());
                }
            });
        }

        return ret;
    }

    private static Plugin instantiatePlugin(
            ClassInfo pluginClassInfo,
            String pluginType,
            String pluginName,
            Map confs)
            throws ConfigurationException,
            InstantiationException,
            IllegalAccessException,
            InvocationTargetException {
        var cil = pluginClassInfo.getDeclaredConstructorInfo();

        final Plugin ret;

        // check if a Constructor with @OnInit exists
        var contructorWihtOnInit = cil.stream()
                .filter(ci -> ci.hasAnnotation(OnInit.class.getName()))
                .findFirst();

        if (contructorWihtOnInit.isPresent()) {
            var ai = contructorWihtOnInit.get()
                    .getAnnotationInfo(OnInit.class.getName());

            // check
            var allConfScope = ai.getParameterValues().stream()
                    .anyMatch(p -> "scope".equals(p.getName())
                    && (ConfigurationScope.class.getName()
                            + "." + ConfigurationScope.ALL.name()).equals(
                            p.getValue().toString()));

            var scopedConf = (Map) (allConfScope
                    ? confs
                    : confs != null
                            ? confs.get(pluginName)
                            : null);

            if (scopedConf == null) {
                LOGGER.warn("{} {} defines constructor with @OnInit "
                        + "but no configuration found for it",
                        pluginType,
                        pluginName);
            }

            // try to instanitate the constructor 
            try {
                ret = (Plugin) pluginClassInfo.loadClass(false)
                        .getDeclaredConstructor(Map.class)
                        .newInstance(scopedConf);

                invokeOnInitMethods(pluginName,
                        pluginType,
                        pluginClassInfo,
                        ret,
                        confs);
            }
            catch (NoSuchMethodException nme) {
                throw new ConfigurationException(
                        pluginType
                        + " " + pluginName
                        + " has an invalid constructor with @OnInit. "
                        + "Constructor signature must be "
                        + pluginClassInfo.getSimpleName()
                        + "(Map<String, Object> configuration)");
            }
        } else {
            try {
                ret = (Plugin) pluginClassInfo.loadClass(false)
                        .getDeclaredConstructor()
                        .newInstance();

                invokeOnInitMethods(pluginName,
                        pluginType,
                        pluginClassInfo,
                        ret,
                        confs);
            }
            catch (NoSuchMethodException nme) {
                throw new ConfigurationException(
                        pluginType
                        + " " + pluginName
                        + " does not have default constructor "
                        + pluginClassInfo.getSimpleName()
                        + "()");
            }
        }

        return ret;
    }

    private static void invokeOnInitMethods(String pluginName,
            String pluginType,
            ClassInfo pluginClassInfo,
            Object pluingInstance,
            Map confs) throws ConfigurationException,
            InstantiationException,
            IllegalAccessException,
            InvocationTargetException {

        // finds @OnInit methods
        // check if a Constructor with @OnInit exists
        var mil = pluginClassInfo.getDeclaredMethodInfo();

        for (var mi : mil) {
            if (mi.hasAnnotation(OnInit.class.getName())) {
                var ai = mi.getAnnotationInfo(OnInit.class.getName());

                // check
                var allConfScope = ai.getParameterValues().stream()
                        .anyMatch(p -> "scope".equals(p.getName())
                        && (ConfigurationScope.class.getName()
                                + "." + ConfigurationScope.ALL.name()).equals(
                                p.getValue().toString()));

                var scopedConf = (Map) (allConfScope
                        ? confs
                        : confs != null
                                ? confs.get(pluginName)
                                : null);

                if (scopedConf == null) {
                    LOGGER.warn("{} {} defines method {} with @OnInit "
                            + "but no configuration found for it",
                            pluginType,
                            pluginName,
                            mi.getName());
                }

                // try to inovke @OnInit method
                try {
                    pluginClassInfo.loadClass(false)
                            .getDeclaredMethod(mi.getName(), Map.class)
                            .invoke(pluingInstance, scopedConf);
                }
                catch (NoSuchMethodException nme) {
                    throw new ConfigurationException(
                            pluginType
                            + " " + pluginName
                            + " has an invalid method with @OnInit. "
                            + "Method signature must be "
                            + mi.getName()
                            + "(Map<String, Object> configuration)");
                }
            }
        }
    }

    private static Throwable getRootException(Throwable t) {
        if (t.getCause() != null) {
            return t.getCause();
        } else {
            return t;
        }
    }

    private static <T extends Object> T annotationParam(ClassInfo ci,
            String param) {
        var annotationInfo = ci.getAnnotationInfo(REGISTER_PLUGIN_CLASS_NAME);
        var annotationParamVals = annotationInfo.getParameterValues();

        // The Route annotation has a parameter named "path"
        return (T) annotationParamVals.getValue(param);
    }

    private static URL[] findPluginsJars(Path pluginsDirectory) {
        var urls = new ArrayList<>();

        try (DirectoryStream<Path> directoryStream = Files
                .newDirectoryStream(pluginsDirectory, "*.jar")) {
            for (Path path : directoryStream) {
                var jar = path.toUri().toURL();
                urls.add(jar);
                LOGGER.info("Added to classpath the plugins jar {}", jar);
            }
        }
        catch (IOException ex) {
            LOGGER.error("Cannot read jars in plugins directory {}",
                    Bootstrapper.getConfiguration().getPluginsDirectory(),
                    ex.getMessage());
        }

        return urls.toArray(new URL[urls.size()]);
    }

    private static Path getPluginsDirectory() {
        var pluginsDir = Bootstrapper.getConfiguration().getPluginsDirectory();

        if (pluginsDir == null) {
            return null;
        }

        if (pluginsDir.startsWith("/")) {
            return Paths.get(pluginsDir);
        } else {
            // this is to allow specifying the plugin directory path 
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

    private static URL[] PLUGINS_JARS_CACHE = null;

    private static URLClassLoader getPluginsClassloader() {
        if (PLUGINS_JARS_CACHE == null) {
            PLUGINS_JARS_CACHE = findPluginsJars(getPluginsDirectory());
        }

        return new URLClassLoader(PLUGINS_JARS_CACHE);
    }

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
}
