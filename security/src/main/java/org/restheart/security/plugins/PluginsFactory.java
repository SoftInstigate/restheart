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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.restheart.security.Bootstrapper;

import org.restheart.security.ConfigurationException;
import org.restheart.security.ConfigurationKeys;
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
     * createAuthenticator
     *
     * @param conf the configuration Map
     * @return the Authenticator
     */
    static Authenticator createAuthenticator(
            Map<String, Object> conf)
            throws ConfigurationException {
        if (conf == null || conf.isEmpty()) {
            throw new ConfigurationException(
                    "Error configuring Authenticator, missing configuration");
        }

        Object _name = conf.get(ConfigurationKeys.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Authenticator, missing "
                    + ConfigurationKeys.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(ConfigurationKeys.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Authenticator "
                    + (String) _name
                    + ", missing "
                    + ConfigurationKeys.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(ConfigurationKeys.ARGS_KEY);

        try {
            if (_args == null) {
                return (Authenticator) Class.forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map<?, ?>)) {
                    throw new ConfigurationException(
                            "Error configuring Authenticator "
                            + (String) _name
                            + ", "
                            + ConfigurationKeys.ARGS_KEY
                            + " property is not a map");
                } else {
                    return (Authenticator) Class.forName(
                            (String) _clazz)
                            .getDeclaredConstructor(String.class, Map.class)
                            .newInstance((String) _name, (Map<?, ?>) _args);
                }
            }
        }
        catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new ConfigurationException(
                    "Error configuring Authenticator "
                    + _name, ex);

        }
    }

    /**
     *
     * @return the Authorizer
     */
    static Authorizer createAuthorizer(
            Map<String, Object> conf)
            throws ConfigurationException {
        if (conf == null || conf.isEmpty()) {
            throw new ConfigurationException(
                    "Error configuring Authorizer, missing configuration");
        }

        Object _name = conf.get(ConfigurationKeys.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Authorizer, missing "
                    + ConfigurationKeys.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(ConfigurationKeys.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Authorizer "
                    + (String) _name
                    + ", missing "
                    + ConfigurationKeys.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(ConfigurationKeys.ARGS_KEY);

        try {
            if (_args == null) {
                return (Authorizer) Class.forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map)) {
                    throw new ConfigurationException(
                            "Error configuring Authorizer "
                            + (String) _name
                            + ", property "
                            + ConfigurationKeys.ARGS_KEY + " is not a map");
                } else {
                    return (Authorizer) Class.forName((String) _clazz)
                            .getDeclaredConstructor(String.class, Map.class)
                            .newInstance((String) _name, (Map<?, ?>) _args);
                }
            }
        }
        catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new ConfigurationException(
                    "Error configuring Authorizer "
                    + _name, ex);
        }
    }

    /**
     * create the initializers
     */
    static Set<PluginRecord<Initializer>> tokenManagers() {
        return createPlugins(TokenManager.class, ARGS_CONFS);
    }

    /**
     * createTokenManager
     *
     * @return the TokenManager
     */
    static TokenManager createTokenManager(
            Map<String, Object> conf)
            throws ConfigurationException {
        if (conf == null || conf.isEmpty()) {
            throw new ConfigurationException(
                    "Error configuring Token Manager, missing configuration");
        }

        Object _name = conf.get(ConfigurationKeys.NAME_KEY);

        if (_name == null || !(_name instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Token Manager, missing "
                    + ConfigurationKeys.NAME_KEY
                    + " property");
        }

        Object _clazz = conf.get(ConfigurationKeys.CLASS_KEY);

        if (_clazz == null || !(_clazz instanceof String)) {
            throw new ConfigurationException(
                    "Error configuring Token Manager "
                    + (String) _name
                    + ", missing "
                    + ConfigurationKeys.CLASS_KEY
                    + " property");
        }

        Object _args = conf.get(ConfigurationKeys.ARGS_KEY);

        try {
            if (_args == null) {
                return (TokenManager) Class.forName((String) _clazz)
                        .getDeclaredConstructor(String.class)
                        .newInstance((String) _name);
            } else {
                if (!(_args instanceof Map)) {
                    throw new ConfigurationException(
                            "Error configuring Token Manager "
                            + (String) _name
                            + ", property "
                            + ConfigurationKeys.ARGS_KEY + " is not a map");
                } else {
                    return (TokenManager) Class.forName((String) _clazz)
                            .getDeclaredConstructor(String.class, Map.class)
                            .newInstance((String) _name, (Map<?, ?>) _args);
                }
            }
        }
        catch (ClassNotFoundException
                | IllegalAccessException
                | IllegalArgumentException
                | InstantiationException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException ex) {
            throw new ConfigurationException(
                    "Error configuring Token Manager "
                    + _name, ex);
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
     * creates the request interceptors
     */
    @SuppressWarnings("unchecked")
    static Set<PluginRecord<RequestInterceptor>> requestInterceptors() {
        return createPlugins(RequestInterceptor.class, ARGS_CONFS);
    }

    /**
     * creates the response interceptors
     */
    @SuppressWarnings("unchecked")
    static Set<PluginRecord<ResponseInterceptor>> responseInterceptors() {
        return createPlugins(ResponseInterceptor.class, ARGS_CONFS);
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
                .scan()) {
            var registeredPlugins = scanResult
                    .getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);

            ClassInfoList listOfType;

            if (type.isInterface()) {
                listOfType = scanResult.getClassesImplementing(type.getName());
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

                    if (!confs.containsKey(name)) {
                        LOGGER.debug("No configuration found for plugin {} ", name);
                    }
                    
                    var enabled = PluginRecord.isEnabled(enabledByDefault,
                            confs.get(name));

                    if (enabled) {
                        try {
                            i = plugin.loadClass(false)
                                    .getConstructor(Map.class)
                                    .newInstance(confs.get(name));
                        }
                        catch (NoSuchMethodException nme) {
                            // Plugin does not have constructor with confArgs
                            i = plugin.loadClass(false)
                                    .getConstructor()
                                    .newInstance();

                            // warn in case there is a configuration
                            // but the plugins does not take it via its constructor
                            // but for enabled key that is managed by pr.isEnabled()
                            if (confs.containsKey(name)
                                    && confs.get(name).keySet()
                                            .stream()
                                            .filter(k -> !"enabled".equals(k))
                                            .count() > 0) {
                                LOGGER.warn("{} {} don't implement the constructor {}"
                                        + "(Map<String, Object> confArgs)"
                                        + " to get arguments from the configuration file"
                                        + " but configuration has been defined for it {}",
                                        _type,
                                        name,
                                        i.getClass().getSimpleName(),
                                        confs.get(name));
                            }
                        }

                        var pr = new PluginRecord(
                                name,
                                description,
                                enabledByDefault,
                                plugin.getName(),
                                (T) i,
                                confs.get(name));

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
                catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException
                        | NoSuchMethodException t) {
                    LOGGER.error("Error registering {} {}",
                            _type,
                            plugin.getName(),
                            t);
                }
            });
        }

        return ret;
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
