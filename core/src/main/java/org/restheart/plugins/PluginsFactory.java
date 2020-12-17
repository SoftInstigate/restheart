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

import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.mongodb.MongoClient;
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
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PluginsFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginsFactory.class);

    private static final Map<String, Map<String, Object>> PLUGINS_CONFS = consumePluginsConfiguration();

    private static final PluginsFactory SINGLETON = new PluginsFactory();

    public static PluginsFactory getInstance() {
        return SINGLETON;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private static Map<String, Map<String, Object>> consumePluginsConfiguration() {
        Map<String, Map<String, Object>> pluginsArgs = Bootstrapper.getConfiguration().getPluginsArgs();

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

    private ArrayList<ClassLoader> classLoaders = new ArrayList<>();

    private PluginsFactory() {
        classLoaders.add(this.getClass().getClassLoader());

        // take classloaders from PluginsScanner into account
        if (PluginsScanner.jars != null) {
            classLoaders.add(new URLClassLoader(PluginsScanner.jars));
        }
    }

    private Set<PluginRecord<AuthMechanism>> authMechanismsCache = null;

    /**
     *
     * @return the AuthenticationMechanisms
     */
    Set<PluginRecord<AuthMechanism>> authMechanisms() {
        if (authMechanismsCache == null) {
            authMechanismsCache = createPlugins(PluginsScanner.AUTH_MECHANISMS, "Authentication Mechanism",
                    Bootstrapper.getConfiguration().getAuthMechanisms());
        }

        return authMechanismsCache;
    }

    private Set<PluginRecord<Authenticator>> authenticatorsCache = null;

    /**
     *
     * @return the Authenticators
     */
    Set<PluginRecord<Authenticator>> authenticators() {
        if (authenticatorsCache == null) {
            authenticatorsCache = createPlugins(PluginsScanner.AUTHENTICATORS, "Authenticators",
                    Bootstrapper.getConfiguration().getAuthenticators());
        }

        return authenticatorsCache;
    }

    private Set<PluginRecord<Authorizer>> authorizersCache = null;

    /**
     *
     * @return the Authorizers
     */
    Set<PluginRecord<Authorizer>> authorizers() {
        if (authorizersCache == null) {
            authorizersCache = createPlugins(PluginsScanner.AUTHORIZERS, "Authorizer",
                    Bootstrapper.getConfiguration().getAuthorizers());
        }

        return authorizersCache;
    }

    private PluginRecord<TokenManager> tokenManagerCache = null;

    /**
     *
     * @return the Token Manager
     */
    PluginRecord<TokenManager> tokenManager() {
        if (tokenManagerCache == null) {
            Set<PluginRecord<TokenManager>> tkms = createPlugins(PluginsScanner.TOKEN_MANAGERS, "Token Manager",
                    Bootstrapper.getConfiguration().getTokenManagers());

            if (tkms != null) {
                var tkm = tkms.stream().filter(t -> t.isEnabled()).findFirst();

                if (tkm != null && tkm.isPresent()) {
                    tokenManagerCache = tkm.get();
                }
            }
        }

        return tokenManagerCache;
    }

    private Set<PluginRecord<Initializer>> initializersCache = null;

    /**
     * create the initializers
     */
    Set<PluginRecord<Initializer>> initializers() {
        if (initializersCache == null) {
            initializersCache = createPlugins(PluginsScanner.INITIALIZERS, "Initializer", PLUGINS_CONFS);
        }

        return initializersCache;
    }

    @SuppressWarnings("rawtypes")
    private Set<PluginRecord<Interceptor>> interceptorsCache = null;

    /**
     * creates the interceptors
     */
    @SuppressWarnings("rawtypes")
    Set<PluginRecord<Interceptor>> interceptors() {
        if (interceptorsCache == null) {
            interceptorsCache = createPlugins(PluginsScanner.INTERCEPTORS, "Interceptor", PLUGINS_CONFS);
        }

        return interceptorsCache;
    }

    @SuppressWarnings("rawtypes")
    private Set<PluginRecord<Service>> servicesCache = null;

    /**
     * creates the services
     */
    @SuppressWarnings("rawtypes")
    Set<PluginRecord<Service>> services() {
        if (servicesCache == null) {
            servicesCache = createPlugins(PluginsScanner.SERVICES, "Service", PLUGINS_CONFS);
        }

        return servicesCache;
    }

    /**
     * @param type the class of the plugin , e.g. Initializer.class
     */
    @SuppressWarnings("unchecked")
    private <T extends Plugin> Set<PluginRecord<T>> createPlugins(ArrayList<PluginDescriptor> pluginDescriptors,
            String type, Map<String, Map<String, Object>> confs) {
        Set<PluginRecord<T>> ret = new LinkedHashSet<>();

        // sort by priority
        pluginDescriptors.sort((PluginDescriptor cd1, PluginDescriptor cd2) -> {
            try {
                var clazz1 = loadPluginClass(cd1);
                var clazz2 = loadPluginClass(cd2);
                return Integer.compare(priority(clazz1), priority(clazz2));
            } catch (ClassNotFoundException cnfe) {
                LOGGER.error("error sorting {} plugins by priority", type, cnfe);
                return -1;
            }
        });

        pluginDescriptors.stream().forEachOrdered(plugin -> {
            try {
                var clazz = loadPluginClass(plugin);
                Plugin i;

                var name = name(clazz);
                var description = description(clazz);
                var enabledByDefault = enabledByDefault(clazz);
                var enabled = PluginRecord.isEnabled(enabledByDefault, confs != null ? confs.get(name) : null);

                if (enabled) {
                    i = instantiatePlugin(clazz, type, name, confs);

                    var pr = new PluginRecord<>(name, description, enabledByDefault, name, (T) i,
                            confs != null ? confs.get(name) : null);

                    if (pr.isEnabled()) {
                        ret.add(pr);
                        LOGGER.debug("Registered {} {}: {}", type, name, description);

                        if (!plugin.injections.isEmpty()) {
                            var ip = new InstatiatedPlugin(name, type, plugin, clazz, i, confs);
                            PLUGINS_TO_INJECT_DEPS.add(ip);
                        }
                    }
                } else {
                    LOGGER.debug("{} {} is disabled", type, name);
                }
            } catch (ClassNotFoundException | ConfigurationException | InstantiationException | IllegalAccessException
                    | InvocationTargetException e) {
                LOGGER.error("Error registering {} {}: {}", type, plugin.getClass().getSimpleName(),
                        getRootException(e).getMessage(), e);
            }
        });

        return ret;
    }

    Map<String, Class<Plugin>> PC_CACHE = new HashMap<>();

    @SuppressWarnings("unchecked")
    private Class<Plugin> loadPluginClass(PluginDescriptor plugin) throws ClassNotFoundException {
        if (PC_CACHE.containsKey(plugin.clazz)) {
            return PC_CACHE.get(plugin.clazz);
        }

        for (var classLoader : this.classLoaders) {
            try {
                var pluginc = (Class<Plugin>) classLoader.loadClass(plugin.clazz);

                PC_CACHE.put(plugin.clazz, pluginc);
                return pluginc;
            } catch (ClassNotFoundException cnfe) {
                // nothing to do
            }
        }

        throw new ClassNotFoundException("plugin class not found " + plugin.clazz);
    }

    @SuppressWarnings("rawtypes")
    private Plugin instantiatePlugin(Class<Plugin> pc, String pluginType, String pluginName, Map confs)
            throws InstantiationException, IllegalAccessException, InvocationTargetException, IllegalArgumentException,
            SecurityException, ClassNotFoundException {
        try {
            return pc.getDeclaredConstructor().newInstance();

        } catch (NoSuchMethodException nme) {
            throw new ConfigurationException(
                    pluginType + " " + pluginName + " does not have default constructor " + pc.getSimpleName() + "()");
        }
    }

    private ArrayList<InstatiatedPlugin> PLUGINS_TO_INJECT_DEPS = new ArrayList<>();

    void injectCoreDependencies() {
        for (var ip: PLUGINS_TO_INJECT_DEPS) {
            try {
                invokeCoreInjectMethods(ip);
            } catch (InvocationTargetException ite) {
                if (ite.getCause() != null && ite.getCause() instanceof NoClassDefFoundError) {
                    var errMsg = "Error handling the request. " + "An external dependency is missing for " + ip.type
                            + " " + ip.name + ". Copy the missing dependency jar to the plugins directory "
                            + "to add it to the classpath";

                    LOGGER.error(errMsg, ite);
                } else {
                    LOGGER.error("Error injecting dependency to {} {}: {}", ip.type, ip.name,
                            getRootException(ite).getMessage(), ite);
                }
            } catch (ConfigurationException | InstantiationException | IllegalAccessException ex) {
                LOGGER.error("Error injecting dependency to {} {}: {}", ip.type, ip.name,
                        getRootException(ex).getMessage(), ex);
            }
        }
    }

    void injectMongoDbDependencies(MongoClient mclient) {
        for (var ip: PLUGINS_TO_INJECT_DEPS) {
            try {
                invokeInjectMongoClientMethods(ip, mclient);
            } catch (InvocationTargetException ite) {
                if (ite.getCause() != null && ite.getCause() instanceof NoClassDefFoundError) {
                    var errMsg = "Error handling the request. " + "An external dependency is missing for " + ip.type
                            + " " + ip.name + ". Copy the missing dependency jar to the plugins directory "
                            + "to add it to the classpath";

                    LOGGER.error(errMsg, ite);
                } else {
                    LOGGER.error("Error injecting dependency to {} {}: {}", ip.type, ip.name,
                            getRootException(ite).getMessage(), ite);
                }
            } catch (ConfigurationException | InstantiationException | IllegalAccessException ex) {
                LOGGER.error("Error injecting dependency to {} {}: {}", ip.type, ip.name,
                        getRootException(ex).getMessage(), ex);
            }
        }
    }

    private void invokeCoreInjectMethods(InstatiatedPlugin ip)
            throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {
        invokeInjectConfigurationMethods(ip);

        invokeInjectPluginsRegistryMethods(ip);

        invokeInjectConfigurationAndPluginsRegistryMethods(ip);
    }

    @SuppressWarnings("rawtypes")
    private void invokeInjectConfigurationMethods(InstatiatedPlugin ip)
            throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {
        // finds @InjectConfiguration methods

        // we need to process methods that are annotated only with @InjectConfiguration
        // a method can have both @InjectConfiguration and @InjectPluginsRegistry
        // so we check that method has only one annotation

        for (var injection : ip.descriptor.injections) {
            if (InjectConfiguration.class.equals(injection.clazz) && ip.descriptor.injections.stream()
                    .filter(p -> p.methodHash == injection.methodHash).count() == 1) {

                // check configuration scope
                var allConfScope = injection.params.stream()
                        .anyMatch(p -> "scope".equals(p.getKey())
                                && (ConfigurationScope.class.getName() + "." + ConfigurationScope.ALL.name())
                                        .equals(p.getValue().toString()));

                var scopedConf = (Map) (allConfScope ? Bootstrapper.getConfiguration().toMap()
                        : ip.confs != null ? ip.confs.get(ip.name) : null);

                if (scopedConf == null) {
                    LOGGER.warn(
                            "{} {} defines method {} with @InjectConfiguration " + "but no configuration found for it",
                            ip.type, ip.name, injection.method);
                }

                // try to inovke @InjectConfiguration method
                try {
                    ip.clazz.getDeclaredMethod(injection.method, Map.class).invoke(ip.instance, scopedConf);
                    LOGGER.trace("Injected Configuration into {}.{}()", ip.clazz.getSimpleName(),
                            injection.method);
                } catch (NoSuchMethodException nme) {
                    throw new ConfigurationException(ip.type + " " + ip.name
                            + " has an invalid method with @InjectConfiguration. " + "Method signature must be "
                            + injection.method + "(Map<String, Object> configuration)");
                }
            }
        }
    }

    private void invokeInjectPluginsRegistryMethods(InstatiatedPlugin ip)
            throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {
        // finds @InjectPluginRegistry methods

        // we need to process methods that are annotated only with @InjectConfiguration
        // a method can have both @InjectConfiguration and @InjectPluginsRegistry
        // so we check that method has only one annotation

        for (var injection : ip.descriptor.injections) {
            if (InjectPluginsRegistry.class.equals(injection.clazz) && ip.descriptor.injections.stream()
                    .filter(p -> p.methodHash == injection.methodHash).count() == 1) {
                // try to inovke @InjectPluginRegistry method
                try {
                    ip.clazz.getDeclaredMethod(injection.method, PluginsRegistry.class).invoke(ip.instance,
                            PluginsRegistryImpl.getInstance());
                    LOGGER.trace("Injected PluginsRegistry into {}.{}()", ip.clazz.getSimpleName(), injection.method);
                } catch (NoSuchMethodException nme) {
                    throw new ConfigurationException(ip.type + " " + ip.name
                            + " has an invalid method with @InjectPluginsRegistry. " + "Method signature must be "
                            + injection.method + "(PluginsRegistry pluginsRegistry)");
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private void invokeInjectConfigurationAndPluginsRegistryMethods(InstatiatedPlugin ip)
            throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {

        // we need to process methods that are annotated only with @InjectConfiguration

        var confMethods = ip.descriptor.injections.stream().filter(p -> InjectConfiguration.class.equals(p.clazz))
                .collect(Collectors.toList());
        var regiMethods = ip.descriptor.injections.stream().filter(p -> InjectPluginsRegistry.class.equals(p.clazz))
                .collect(Collectors.toList());

        // intersect
        var bothMethods = confMethods.stream()
                .filter(c -> regiMethods.stream().anyMatch(r -> r.methodHash == c.methodHash))
                .collect(Collectors.toList());

        // finds @InjectConfiguration methods
        for (var injection : bothMethods) {
            // check configuration scope
            var allConfScope = injection.params.stream()
                    .anyMatch(p -> "scope".equals(p.getKey())
                            && (ConfigurationScope.class.getName() + "." + ConfigurationScope.ALL.name())
                                    .equals(p.getValue().toString()));

            var scopedConf = (Map) (allConfScope ? Bootstrapper.getConfiguration().toMap()
                    : ip.confs != null ? ip.confs.get(ip.name) : null);

            if (scopedConf == null) {
                LOGGER.warn("{} {} defines method {} with @InjectConfiguration " + "but no configuration found for it",
                        ip.type, ip.name, injection.method);
            }

            // try to inovke @InjectConfiguration method
            try {
                ip.clazz.getDeclaredMethod(injection.method, Map.class, PluginsRegistry.class).invoke(ip.instance,
                        scopedConf, PluginsRegistryImpl.getInstance());
                LOGGER.trace("Injected PluginsRegistry and Configuration into {}.{}()",
                        ip.clazz.getSimpleName(), injection.method);
            } catch (NoSuchMethodException nme) {
                throw new ConfigurationException(
                        ip.type + " " + ip.name + " has an invalid method with @InjectConfiguration"
                                + " and @InjectPluginsRegistry." + " Method signature must be " + injection.method
                                + "(Map<String, Object> configuration," + " PluginsRegistry pluginsRegistry)");
            }

        }
    }

    private void invokeInjectMongoClientMethods(InstatiatedPlugin ip, MongoClient mclient)
            throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {
        // finds @InjectMongoClient methods

        for (var injection : ip.descriptor.injections) {
            if (InjectMongoClient.class.equals(injection.clazz)) {

                // try to inovke @InjectMongoClient method
                try {
                    ip.clazz.getDeclaredMethod(injection.method, MongoClient.class).invoke(ip.instance, mclient);
                    LOGGER.trace("Injected MongoClient into {}.{}()", ip.clazz.getSimpleName(),
                            injection.method);
                } catch (NoSuchMethodException nme) {
                    throw new ConfigurationException(ip.type + " " + ip.name
                            + " has an invalid method with @InjectMongoClient. " + "Method signature must be "
                            + injection.method + "(MongoClient mclient)");
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

    private int priority(Class<Plugin> p) {
        return p.getAnnotation(RegisterPlugin.class).priority();
    }

    private String name(Class<Plugin> p) {
        return p.getAnnotation(RegisterPlugin.class).name();
    }

    private String description(Class<Plugin> p) {
        return p.getAnnotation(RegisterPlugin.class).description();
    }

    private Boolean enabledByDefault(Class<Plugin> p) {
        return p.getAnnotation(RegisterPlugin.class).enabledByDefault();
    }

    @SuppressWarnings("rawtypes")
    private static class InstatiatedPlugin {
        private final String name;
        private final String type;
        private final PluginDescriptor descriptor;
        private final Class<Plugin> clazz;
        private final Object instance;
        private final Map confs;

        InstatiatedPlugin(String name, String type, PluginDescriptor descriptor, Class<Plugin> clazz, Object instance,
                Map confs) {
            this.name = name;
            this.type = type;
            this.descriptor = descriptor;
            this.clazz = clazz;
            this.instance = instance;
            this.confs = confs;
        }
    }
}
