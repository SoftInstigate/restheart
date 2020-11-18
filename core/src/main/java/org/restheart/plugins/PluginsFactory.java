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
public class PluginsFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginsFactory.class);

    private static final Map<String, Map<String, Object>> PLUGINS_CONFS = consumePluginsConfiguration();

    private static final PluginsFactory SINGLETON = new PluginsFactory();

    public static PluginsFactory getInstance() {
        return SINGLETON;
    }

    @SuppressWarnings("unchecked")
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

    private PluginsFactory() {
    }

    /**
     *
     * @return the AuthenticationMechanisms
     */
    Set<PluginRecord<AuthMechanism>> authMechanisms() {
        return createPlugins(PluginsScanner.AUTH_MECHANISMS, "Authentication Mechanism",
                Bootstrapper.getConfiguration().getAuthMechanisms());
    }

    /**
     *
     * @return the Authenticators
     */
    Set<PluginRecord<Authenticator>> authenticators() {
        return createPlugins(PluginsScanner.AUTHENTICATORS, "Authenticators",
                Bootstrapper.getConfiguration().getAuthenticators());
    }

    /**
     *
     * @return the Authorizers
     */
    Set<PluginRecord<Authorizer>> authorizers() {
        return createPlugins(PluginsScanner.AUTHORIZERS, "Authorizer",
                Bootstrapper.getConfiguration().getAuthorizers());
    }

    /**
     *
     * @return the Token Manager
     */
    PluginRecord<TokenManager> tokenManager() {
        Set<PluginRecord<TokenManager>> tkms = createPlugins(PluginsScanner.TOKEN_MANAGERS, "Token Manager",
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
        return createPlugins(PluginsScanner.INITIALIZERS, "Initializer", PLUGINS_CONFS);
    }

    /**
     * creates the interceptors
     */
    @SuppressWarnings("unchecked")
    Set<PluginRecord<Interceptor>> interceptors() {
        return createPlugins(PluginsScanner.INTERCEPTORS, "Interceptor", PLUGINS_CONFS);
    }

    /**
     * creates the services
     */
    @SuppressWarnings("unchecked")
    Set<PluginRecord<Service>> services() {
        return createPlugins(PluginsScanner.SERVICES, "Service", PLUGINS_CONFS);
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
                    }
                } else {
                    LOGGER.debug("{} {} is disabled", type, name);
                }
            } catch (ClassNotFoundException | ConfigurationException | InstantiationException | IllegalAccessException
                    | InvocationTargetException e) {
                LOGGER.error("Error registering {} {}: {}", type, plugin.getClass().getSimpleName(), getRootException(e).getMessage(), e);
            }
        });

        return ret;
    }

    private Class<Plugin> loadPluginClass(PluginDescriptor plugin) throws ClassNotFoundException {
        return (Class<Plugin>) Class.forName(plugin.clazz);
    }

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

    // public void injectDependencies() {
    // for (Iterator<InstatiatedPlugin> it = PLUGINS_TO_INJECT_DEPS.iterator();
    // it.hasNext();) {
    // var ip = it.next();

    // try {
    // invokeInjectMethods(ip);
    // } catch (InvocationTargetException ite) {
    // if (ite.getCause() != null && ite.getCause() instanceof NoClassDefFoundError)
    // {
    // var errMsg = "Error handling the request. " + "An external dependency is
    // missing for "
    // + ip.pluginType + " " + ip.pluginName
    // + ". Copy the missing dependency jar to the plugins directory "
    // + "to add it to the classpath";

    // LOGGER.error(errMsg, ite);
    // } else {
    // LOGGER.error("Error injecting dependency to {} {}: {}", ip.pluginType,
    // ip.pluginName,
    // getRootException(ite).getMessage(), ite);
    // }
    // } catch (ConfigurationException | InstantiationException |
    // IllegalAccessException ex) {
    // LOGGER.error("Error injecting dependency to {} {}: {}", ip.pluginType,
    // ip.pluginName,
    // getRootException(ex).getMessage(), ex);
    // }

    // it.remove();
    // }
    // }

    private void invokeInjectMethods(InstatiatedPlugin ip)
            throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {
        invokeInjectConfigurationMethods(ip.pluginName, ip.pluginType, ip.pluginClassInfo, ip.pluingInstance, ip.confs);

        invokeInjectPluginsRegistryMethods(ip.pluginName, ip.pluginType, ip.pluginClassInfo, ip.pluingInstance);

        invokeInjectConfigurationAndPluginsRegistryMethods(ip.pluginName, ip.pluginType, ip.pluginClassInfo,
                ip.pluingInstance, ip.confs);
    }

    private void invokeInjectConfigurationMethods(String pluginName, String pluginType, ClassInfo pluginClassInfo,
            Object pluingInstance, Map confs)
            throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {

        // finds @InjectConfiguration methods
        var mil = pluginClassInfo.getDeclaredMethodInfo();

        for (var mi : mil) {
            if (mi.hasAnnotation(InjectConfiguration.class.getName())
                    && !mi.hasAnnotation(InjectPluginsRegistry.class.getName())) {
                var ai = mi.getAnnotationInfo(InjectConfiguration.class.getName());

                // check configuration scope
                var allConfScope = ai.getParameterValues().stream()
                        .anyMatch(p -> "scope".equals(p.getName())
                                && (ConfigurationScope.class.getName() + "." + ConfigurationScope.ALL.name())
                                        .equals(p.getValue().toString()));

                var scopedConf = (Map) (allConfScope ? Bootstrapper.getConfiguration().toMap()
                        : confs != null ? confs.get(pluginName) : null);

                if (scopedConf == null) {
                    LOGGER.warn(
                            "{} {} defines method {} with @InjectConfiguration " + "but no configuration found for it",
                            pluginType, pluginName, mi.getName());
                }

                // try to inovke @InjectConfiguration method
                try {
                    pluginClassInfo.loadClass(false).getDeclaredMethod(mi.getName(), Map.class).invoke(pluingInstance,
                            scopedConf);
                } catch (NoSuchMethodException nme) {
                    throw new ConfigurationException(pluginType + " " + pluginName
                            + " has an invalid method with @InjectConfiguration. " + "Method signature must be "
                            + mi.getName() + "(Map<String, Object> configuration)");
                }
            }
        }
    }

    private void invokeInjectPluginsRegistryMethods(String pluginName, String pluginType, ClassInfo pluginClassInfo,
            Object pluingInstance)
            throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {

        // finds @InjectPluginRegistry methods
        var mil = pluginClassInfo.getDeclaredMethodInfo();

        for (var mi : mil) {
            if (mi.hasAnnotation(InjectPluginsRegistry.class.getName())
                    && !mi.hasAnnotation(InjectConfiguration.class.getName())) {
                // try to inovke @InjectPluginRegistry method
                try {
                    pluginClassInfo.loadClass(false).getDeclaredMethod(mi.getName(), PluginsRegistry.class)
                            .invoke(pluingInstance, PluginsRegistryImpl.getInstance());
                } catch (NoSuchMethodException nme) {
                    throw new ConfigurationException(
                            pluginType + " " + pluginName + " has an invalid method with @InjectPluginsRegistry. "
                                    + "Method signature must be " + mi.getName() + "(PluginsRegistry pluginsRegistry)");
                }
            }
        }
    }

    private void invokeInjectConfigurationAndPluginsRegistryMethods(String pluginName, String pluginType,
            ClassInfo pluginClassInfo, Object pluingInstance, Map confs)
            throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {

        // finds @InjectConfiguration methods
        var mil = pluginClassInfo.getDeclaredMethodInfo();

        for (var mi : mil) {
            if (mi.hasAnnotation(InjectConfiguration.class.getName())
                    && mi.hasAnnotation(InjectPluginsRegistry.class.getName())) {
                var ai = mi.getAnnotationInfo(InjectConfiguration.class.getName());

                // check configuration scope
                var allConfScope = ai.getParameterValues().stream()
                        .anyMatch(p -> "scope".equals(p.getName())
                                && (ConfigurationScope.class.getName() + "." + ConfigurationScope.ALL.name())
                                        .equals(p.getValue().toString()));

                var scopedConf = (Map) (allConfScope ? Bootstrapper.getConfiguration().toMap()
                        : confs != null ? confs.get(pluginName) : null);

                if (scopedConf == null) {
                    LOGGER.warn(
                            "{} {} defines method {} with @InjectConfiguration " + "but no configuration found for it",
                            pluginType, pluginName, mi.getName());
                }

                // try to inovke @InjectConfiguration method
                try {
                    pluginClassInfo.loadClass(false).getDeclaredMethod(mi.getName(), Map.class, PluginsRegistry.class)
                            .invoke(pluingInstance, scopedConf, PluginsRegistryImpl.getInstance());
                } catch (NoSuchMethodException nme) {
                    throw new ConfigurationException(
                            pluginType + " " + pluginName + " has an invalid method with @InjectConfiguration"
                                    + " and @InjectPluginsRegistry." + " Method signature must be " + mi.getName()
                                    + "(Map<String, Object> configuration," + " PluginsRegistry pluginsRegistry)");
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

    private static class InstatiatedPlugin {

        private final String pluginName;
        private final String pluginType;
        private final ClassInfo pluginClassInfo;
        private final Object pluingInstance;
        private final Map confs;

        InstatiatedPlugin(String pluginName, String pluginType, ClassInfo pluginClassInfo, Object pluingInstance,
                Map confs) {
            this.pluginName = pluginName;
            this.pluginType = pluginType;
            this.pluginClassInfo = pluginClassInfo;
            this.pluingInstance = pluingInstance;
            this.confs = confs;
        }
    }
}
