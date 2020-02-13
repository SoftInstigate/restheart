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
import java.util.Optional;

import org.restheart.security.Bootstrapper;
import org.restheart.security.cache.Cache;
import org.restheart.security.cache.CacheFactory;
import org.restheart.security.cache.LoadingCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.restheart.security.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PluginsRegistry {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(PluginsRegistry.class);

    private static final String AUTH_TOKEN_MANAGER_NAME = "@@authTokenManager";
    private static final String AUTHORIZER_NAME = "@@authorizer";

    private static final String REGISTER_PLUGIN_CLASS_NAME = RegisterPlugin.class
            .getName();

    private final Map<String, Map<String, Object>> confs = consumeConfiguration();

    private static final LoadingCache<String, Authenticator> AUTHENTICATORS_CACHE
            = CacheFactory.createLocalLoadingCache(
                    Integer.MAX_VALUE,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var authenticators = Bootstrapper.getConfiguration()
                                .getAuthenticators();

                        var authenticatorConf = authenticators.stream().filter(
                                authenticator -> name
                                        .equals(authenticator.get("name")))
                                .findFirst();

                        if (authenticatorConf.isPresent()) {
                            try {
                                return PluginsFactory
                                        .createAuthenticator(authenticatorConf.get());
                            }
                            catch (ConfigurationException pcex) {
                                throw new IllegalStateException(
                                        pcex.getMessage(), pcex);
                            }
                        } else {
                            var errorMsg = "Authenticator "
                            + name
                            + " not found.";

                            throw new IllegalStateException(errorMsg,
                                    new ConfigurationException(errorMsg));
                        }
                    });

    private static final LoadingCache<String, AuthMechanism> AUTH_MECHANISMS_CACHE
            = CacheFactory.createLocalLoadingCache(
                    Integer.MAX_VALUE,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var amsConf = Bootstrapper.getConfiguration().getAuthMechanisms();

                        var amConf = amsConf.stream().filter(am -> name
                        .equals(am.get("name")))
                                .findFirst();

                        if (amConf.isPresent()) {
                            try {
                                return PluginsFactory
                                        .createAutenticationMechanism(amConf.get());
                            }
                            catch (ConfigurationException pcex) {
                                throw new IllegalStateException(
                                        pcex.getMessage(), pcex);
                            }
                        } else {
                            var errorMsg = "Authentication Mechanism "
                            + name
                            + " not found.";

                            throw new IllegalStateException(errorMsg,
                                    new ConfigurationException(errorMsg));
                        }
                    });

    private static final LoadingCache<String, Authorizer> AUTHORIZERS_CACHE
            = CacheFactory.createLocalLoadingCache(
                    Integer.MAX_VALUE,
                    Cache.EXPIRE_POLICY.NEVER, -1, name -> {
                        var authorizersConf = Bootstrapper.getConfiguration()
                                .getAuthorizers();

                        var authorizerConf = authorizersConf.stream().filter(am -> name
                        .equals(am.get("name")))
                                .findFirst();

                        if (authorizerConf != null) {
                            try {
                                return PluginsFactory
                                        .createAuthorizer(authorizerConf.get());
                            }
                            catch (ConfigurationException pcex) {
                                throw new IllegalStateException(
                                        pcex.getMessage(), pcex);
                            }
                        } else {
                            var errorMsg = "Authorizer "
                            + " not configured.";

                            throw new IllegalStateException(errorMsg,
                                    new ConfigurationException(errorMsg));
                        }
                    });

    private final Set<PluginRecord<Service>> services
            = new LinkedHashSet<>();

    private final Set<PluginRecord<Initializer>> initializers
            = new LinkedHashSet<>();

    private final Set<PluginRecord<PreStartupInitializer>> preStartupInitializers
            = new LinkedHashSet<>();

    private final Set<PluginRecord<RequestInterceptor>> requestInterceptors
            = new LinkedHashSet<>();

    private final Set<PluginRecord<ResponseInterceptor>> responseInterceptors
            = new LinkedHashSet<>();

    private static PluginsRegistry HOLDER;

    public static synchronized PluginsRegistry getInstance() {
        if (HOLDER == null) {
            HOLDER = new PluginsRegistry();
        }

        return HOLDER;
    }

    private PluginsRegistry() {
        this.initializers.addAll(findIPlugins(Initializer.class.getName()));
        this.preStartupInitializers.addAll(findIPlugins(PreStartupInitializer.class.getName()));
        findServices();
        findInterceptors();
    }

    private Map<String, Map<String, Object>> consumeConfiguration() {
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

    public Set<PluginRecord<Initializer>> getInitializers() {
        return this.initializers;
    }

    public Set<PluginRecord<PreStartupInitializer>> getPreStartupInitializers() {
        return this.preStartupInitializers;
    }

    public Set<PluginRecord<Service>> getServices() {
        return this.services;
    }

    public Authenticator getAuthenticator(String name)
            throws ConfigurationException {
        Optional<Authenticator> op = AUTHENTICATORS_CACHE
                .getLoading(name);

        if (op == null) {
            throw new ConfigurationException(
                    "No Authenticator configured with name: " + name);
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new ConfigurationException(
                    "No Authenticator configured with name: " + name);
        }
    }

    public AuthMechanism getAuthenticationMechanism(String name)
            throws ConfigurationException {
        Optional<AuthMechanism> op = AUTH_MECHANISMS_CACHE
                .getLoading(name);

        if (op == null) {
            throw new ConfigurationException(
                    "No Authentication Mechanism configured with name: " + name);
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new ConfigurationException(
                    "No Authentication Mechanism configured with name: " + name);
        }
    }

    public Authorizer getAuthorizer(String name)
            throws ConfigurationException {
        Optional<Authorizer> op = AUTHORIZERS_CACHE
                .getLoading(name);

        if (op == null) {
            throw new ConfigurationException(
                    "No Authorizer configured");
        }

        if (op.isPresent()) {
            return op.get();
        } else {
            throw new ConfigurationException(
                    "No Authorizer configured");
        }
    }

    /**
     * finds the services
     */
    @SuppressWarnings("unchecked")
    private void findInterceptors() {
        try (var scanResult = new ClassGraph()
                .enableAnnotationInfo()
                .addClassLoader(getPluginsClassloader())
                .scan()) {
            var registeredPlugins = scanResult
                    .getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);

            var listOfType = scanResult.getClassesImplementing(Interceptor.class.getName());

            var registeredInterceptors = registeredPlugins.intersect(listOfType);

            registeredInterceptors.stream().forEach(registeredInterceptor -> {
                Object srv;

                try {
                    String name = annotationParam(registeredInterceptor,
                            "name");
                    String description = annotationParam(registeredInterceptor,
                            "description");
                    Boolean enabledByDefault = annotationParam(registeredInterceptor,
                            "enabledByDefault");

                    srv = registeredInterceptor.loadClass(false)
                            .getConstructor(Map.class)
                            .newInstance(confs.get(name));

                    var pr = new PluginRecord(
                            name,
                            description,
                            enabledByDefault,
                            registeredInterceptor.getName(),
                            (Interceptor) srv,
                            confs.get(name));

                    if (pr.isEnabled()) {
                        if (pr.getInstance() instanceof RequestInterceptor) {
                            this.requestInterceptors.add(pr);
                            LOGGER.info("Registered request interceptor {}: {}",
                                name,
                                description);
                        } else if (pr.getInstance() instanceof ResponseInterceptor) {
                            this.responseInterceptors.add(pr);
                            LOGGER.info("Registered response interceptor {}: {}",
                                name,
                                description);
                        }
                    } else {
                        LOGGER.debug("Interceptor {} is disabled", name);
                    }
                }
                catch (NoSuchMethodException nsme) {
                    LOGGER.error("Plugin class {} annotated with "
                            + "@RegisterPlugin must have a constructor "
                            + "with single argument of type Map<String, Object>",
                            registeredInterceptor.getName());
                }
                catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException t) {
                    LOGGER.error("Error registering interceptor {}",
                            registeredInterceptor.getName(),
                            t);
                }
                catch (Throwable t) {
                    LOGGER.error("Error registering interceptor {}",
                            registeredInterceptor.getName(),
                            t);
                }
            });
        }
    }
    
    /**
     * finds the services
     */
    @SuppressWarnings("unchecked")
    private void findServices() {
        try (var scanResult = new ClassGraph()
                .enableAnnotationInfo()
                .addClassLoader(getPluginsClassloader())
                .scan()) {
            var registeredPlugins = scanResult
                    .getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);

            var listOfType = scanResult.getSubclasses(Service.class.getName());

            var registeredServices = registeredPlugins.intersect(listOfType);

            registeredServices.stream().forEach(registeredService -> {
                Object srv;

                try {
                    String name = annotationParam(registeredService,
                            "name");
                    String description = annotationParam(registeredService,
                            "description");
                    Boolean enabledByDefault = annotationParam(registeredService,
                            "enabledByDefault");

                    srv = registeredService.loadClass(false)
                            .getConstructor(Map.class)
                            .newInstance(confs.get(name));

                    var pr = new PluginRecord(
                            name,
                            description,
                            enabledByDefault,
                            registeredService.getName(),
                            (Service) srv,
                            confs.get(name));

                    if (pr.isEnabled()) {
                        this.services.add(pr);
                        LOGGER.info("Registered service {}: {}",
                                name,
                                description);
                    } else {
                        LOGGER.debug("Service {} is disabled", name);
                    }
                }
                catch (NoSuchMethodException nsme) {
                    LOGGER.error("Plugin class {} annotated with "
                            + "@RegisterPlugin must have a constructor "
                            + "with single argument of type Map<String, Object>",
                            registeredService.getName());
                }
                catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException t) {
                    LOGGER.error("Error registering service {}",
                            registeredService.getName(),
                            t);
                }
                catch (Throwable t) {
                    LOGGER.error("Error registering service {}",
                            registeredService.getName(),
                            t);
                }
            });
        }
    }

    public TokenManager getTokenManager()
            throws ConfigurationException {
        Optional<Authenticator> op = AUTHENTICATORS_CACHE
                .get(AUTH_TOKEN_MANAGER_NAME);

        if (op == null) {
            Authenticator atm = PluginsFactory
                    .createTokenManager(Bootstrapper.getConfiguration()
                            .getTokenManager());

            AUTHENTICATORS_CACHE.put(AUTH_TOKEN_MANAGER_NAME, atm);
            return (TokenManager) atm;
        }

        if (op.isPresent()) {
            return (TokenManager) op.get();
        } else {
            throw new ConfigurationException(
                    "No Token Manager configured");
        }
    }

    public Set<PluginRecord<RequestInterceptor>> getRequestInterceptors() {
        return requestInterceptors;
    }

    public Set<PluginRecord<ResponseInterceptor>> getResponseInterceptors() {
        return responseInterceptors;
    }

    /**
     * finds the initializers
     */
    @SuppressWarnings("unchecked")
    private <T extends Plugin> Set<PluginRecord<T>> findIPlugins(String interfaceName) {
        Set<PluginRecord<T>> ret = new LinkedHashSet<>();

        try (var scanResult = new ClassGraph()
                .addClassLoader(getPluginsClassloader())
                .enableAnnotationInfo()
                .scan()) {
            var registeredPlugins = scanResult
                    .getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);

            var listOfType = scanResult.getClassesImplementing(interfaceName);

            var registeredInitializers = registeredPlugins.intersect(listOfType);

            // sort @Initializers by priority
            registeredInitializers.sort((ClassInfo ci1, ClassInfo ci2) -> {
                return Integer.compare(annotationParam(ci1, "priority"),
                        annotationParam(ci2, "priority"));
            });

            registeredInitializers.stream().forEachOrdered(registeredInitializer -> {
                Object i;

                try {
                    i = registeredInitializer.loadClass(false)
                            .getConstructor()
                            .newInstance();

                    String name = annotationParam(registeredInitializer,
                            "name");
                    String description = annotationParam(registeredInitializer,
                            "description");
                    Boolean enabledByDefault = annotationParam(registeredInitializer,
                            "enabledByDefault");

                    var pr = new PluginRecord(
                            name,
                            description,
                            enabledByDefault,
                            registeredInitializer.getName(),
                            (T) i,
                            confs.get(name));

                    if (pr.isEnabled()) {
                        ret.add(pr);
                        LOGGER.info("Registered initializer {}: {}",
                                name,
                                description);
                    } else {
                        LOGGER.debug("Initializer {} is disabled", name);
                    }
                }
                catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException
                        | NoSuchMethodException t) {
                    LOGGER.error("Error registering initializer {}",
                            registeredInitializer.getName(),
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

    private URL[] findPluginsJars(Path pluginsDirectory) {
        var urls = new ArrayList<URL>();

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

    private Path getPluginsDirectory() {
        var pluginsDir = Bootstrapper.getConfiguration().getPluginsDirectory();

        if (pluginsDir == null) {
            return null;
        }

        if (pluginsDir.startsWith("/")) {
            return Paths.get(pluginsDir);
        } else {
            // this is to allow specifying the plugin directory path 
            // relative to the jar (also working when running from classes)
            URL location = this.getClass().getProtectionDomain()
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

    private URLClassLoader getPluginsClassloader() {
        if (PLUGINS_JARS_CACHE == null) {
            PLUGINS_JARS_CACHE = findPluginsJars(getPluginsDirectory());
        }

        return new URLClassLoader(PLUGINS_JARS_CACHE);
    }
}
