/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import static org.restheart.configuration.Utils.getOrDefault;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.restheart.Bootstrapper;
import org.restheart.configuration.Configuration;
import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authenticator;
import org.restheart.plugins.security.Authorizer;
import org.restheart.plugins.security.TokenManager;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PluginsFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginsFactory.class);

    private static final PluginsFactory SINGLETON = new PluginsFactory();

    public static PluginsFactory getInstance() {
        return SINGLETON;
    }

    private PluginsFactory() {
    }

    private Set<PluginRecord<AuthMechanism>> authMechanismsCache = null;

    /**
     *
     * @return the AuthenticationMechanisms
     */
    Set<PluginRecord<AuthMechanism>> authMechanisms() {
        if (authMechanismsCache == null) {
            var validPlugins = PluginsScanner.authMechanisms().stream().filter(p -> ProvidersChecker.checkDependencies(LOGGER, validProviders, p)).collect(Collectors.toList());
            authMechanismsCache = createPlugins(validPlugins, "Authentication Mechanism", Bootstrapper.getConfiguration());
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
            var validPlugins = PluginsScanner.authenticators().stream().filter(p -> ProvidersChecker.checkDependencies(LOGGER, validProviders, p)).collect(Collectors.toList());
            authenticatorsCache = createPlugins(validPlugins, "Authenticator", Bootstrapper.getConfiguration());
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
            var validPlugins = PluginsScanner.authorizers().stream().filter(p -> ProvidersChecker.checkDependencies(LOGGER, validProviders, p)).collect(Collectors.toList());
            authorizersCache = createPlugins(validPlugins, "Authorizer", Bootstrapper.getConfiguration());
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
            var validPlugins = PluginsScanner.tokenManagers().stream().filter(p -> ProvidersChecker.checkDependencies(LOGGER, validProviders, p)).collect(Collectors.toList());
            Set<PluginRecord<TokenManager>> tkms = createPlugins(validPlugins, "Token Manager", Bootstrapper.getConfiguration());

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
            var validPlugins = PluginsScanner.initializers().stream().filter(p -> ProvidersChecker.checkDependencies(LOGGER, validProviders, p)).collect(Collectors.toList());
            initializersCache = createPlugins(validPlugins, "Initializer", Bootstrapper.getConfiguration());
        }

        return initializersCache;
    }

    private Set<PluginRecord<Interceptor<?, ?>>> interceptorsCache = null;

    /**
     * creates the interceptors
     */
    Set<PluginRecord<Interceptor<?, ?>>> interceptors() {
        if (interceptorsCache == null) {
            var validPlugins = PluginsScanner.interceptors().stream().filter(p -> ProvidersChecker.checkDependencies(LOGGER, validProviders, p)).collect(Collectors.toList());
            interceptorsCache = createPlugins(validPlugins, "Interceptor", Bootstrapper.getConfiguration());
        }

        return interceptorsCache;
    }

    private Set<PluginRecord<Service<?, ?>>> servicesCache = null;

    /**
     * creates the services
     */
    Set<PluginRecord<Service<?, ?>>> services() {
        if (this.servicesCache == null) {
            var validPlugins = PluginsScanner.services().stream().filter(p -> ProvidersChecker.checkDependencies(LOGGER, validProviders, p)).collect(Collectors.toList());
            servicesCache = createPlugins(validPlugins, "Service", Bootstrapper.getConfiguration());
        }

        return servicesCache;
    }

    private Set<PluginDescriptor> validProviders = null;
    private Set<PluginRecord<Provider<?>>> providersCache = null;

    /**
     * creates the providers
     */
    Set<PluginRecord<Provider<?>>> providers() {
        if (this.providersCache == null) {
            // instantial all providers
            Set<PluginRecord<Provider<?>>> providers = createPlugins(PluginsScanner.providers(), "Provider", Bootstrapper.getConfiguration());

            // register providers rawTypes (i.e. the class of the provided object)
            // must be before ProvidersChecker.validProviders()
            providers.stream().forEach(p -> providersTypes.put(p.getName(), p.getInstance().rawType()));

            this.validProviders = ProvidersChecker.validProviders(LOGGER, PluginsScanner.providers());
            // only register valid plugins (from ProvidersChecker.validProviders())
            this.providersCache = validProviders.stream()
                .map(pd -> providers.stream().filter(p -> p.getClassName().equals(pd.clazz())).findFirst())
                .filter(p -> p.isPresent())
                .map(p -> p.get())
                .collect(Collectors.toSet());
        }

        return providersCache;
    }

    private static final Map<String, Class<?>> providersTypes = new HashMap<>();

    /**
     * NOTE: this is availabe only after providers instantiation happening in method providers()
     * @return a Map whose keys are the provider's name and values are the classes of the provided objects
     */
    static Map<String, Class<?>> providersTypes() {
        if (!PluginsScanner.providers().isEmpty() && providersTypes.keySet().isEmpty()) {
            throw new IllegalStateException("providersTypes are available only after providers instantiation happening in method providers()");
        }
        return providersTypes;
    }

    /**
     * @param type the class of the plugin , e.g. Initializer.class
     */
    @SuppressWarnings("unchecked")
    private <T extends Plugin> Set<PluginRecord<T>> createPlugins(List<PluginDescriptor> pluginDescriptors, String type, Configuration conf) {
        var ret = new LinkedHashSet<PluginRecord<T>>();

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
                var secure = secure(clazz);
                var enabledByDefault = enabledByDefault(clazz);
                Map<String, Object> pluginConf = getOrDefault(conf, name, null, true);
                var enabled = PluginRecord.isEnabled(enabledByDefault, pluginConf);

                if (enabled) {
                    i = instantiatePlugin(clazz, type, name);

                    var pr = new PluginRecord<>(name, description, secure, enabledByDefault, clazz.getName(), (T) i, pluginConf);

                    this.INSTANTIATED_PLUGINS_RECORDS.put(i.getClass().getName(), pr);

                    if (pr.isEnabled()) {
                        ret.add(pr);
                        LOGGER.debug("Registered {} {}: {}", type, name, description);

                        if (!plugin.injections().isEmpty()) {
                            var ip = new InstatiatedPlugin(name, type, plugin, clazz, i);
                            PLUGINS_TO_INJECT_DEPS.add(ip);
                        }
                    }
                } else {
                    LOGGER.debug("{} {} is disabled", type, name);
                }
            } catch (ClassNotFoundException | ConfigurationException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                LOGGER.error("Error registering {} {}: {}", type, plugin.clazz(), getRootException(e).getMessage(), e);
            }
        });

        return ret;
    }

    Map<String, Class<Plugin>> PC_CACHE = new HashMap<>();

    @SuppressWarnings("unchecked")
    private Class<Plugin> loadPluginClass(PluginDescriptor plugin) throws ClassNotFoundException {
        if (PC_CACHE.containsKey(plugin.clazz())) {
            return PC_CACHE.get(plugin.clazz());
        }

        return (Class<Plugin>) PluginsClassloader.getInstance().loadClass(plugin.clazz());
    }

    private Plugin instantiatePlugin(Class<Plugin> pc, String pluginType, String pluginName) throws InstantiationException, IllegalAccessException, InvocationTargetException, IllegalArgumentException, SecurityException, ClassNotFoundException {
        try {
            return pc.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException nme) {
            throw new ConfigurationException(pluginType + " " + pluginName + " does not have default constructor " + pc.getSimpleName() + "()");
        }
    }

    private final List<InstatiatedPlugin> PLUGINS_TO_INJECT_DEPS = new ArrayList<>();

    private final HashMap<String, PluginRecord<?>> INSTANTIATED_PLUGINS_RECORDS = new HashMap<>();

    void injectDependencies() {
        for (var ip: PLUGINS_TO_INJECT_DEPS) {
            try {
                inject(ip);
            } catch (InvocationTargetException ite) {
                if (ite.getCause() != null && ite.getCause() instanceof NoClassDefFoundError) {
                    var errMsg = "An external dependency is missing for " + ip.type
                            + " " + ip.name + ". Copying the missing dependency jar into the plugins directory "
                            + "should fix the error";

                    LOGGER.error(errMsg, ite);
                } else {
                    LOGGER.error("Error injecting dependency into {} {}: {}", ip.type, ip.name, getRootException(ite).getMessage(), ite);
                }
            } catch(NoProviderException npe) {
                LOGGER.error("Error injecting dependency into {} {}: {}", ip.type, ip.name, npe.getMessage());
            } catch (InstantiationException | IllegalAccessException ex) {
                LOGGER.error("Error injecting dependency into {} {}: {}", ip.type, ip.name, getRootException(ex).getMessage(), ex);
            }
        }
    }

    private void inject(InstatiatedPlugin ip) throws NoProviderException, InstantiationException, IllegalAccessException, InvocationTargetException {
        setInjectFields(ip);
        invokeOnInitMethods(ip);
    }

    private void setInjectFields(InstatiatedPlugin ip) throws NoProviderException, InstantiationException, IllegalAccessException, InvocationTargetException {
        // finds @Inject methods

        // we need to process methods that are annotated only with @Inject
        // and have only one method parameter

        var injections = new ArrayList<FieldInjectionDescriptor>();
        ip.descriptor.injections().stream()
            .filter(i -> i instanceof FieldInjectionDescriptor)
            .map(i -> (FieldInjectionDescriptor) i)
            .forEach(injections::add);

        for (var injection : injections) {
            // try to set @Inject field
            try {
                var field = ip.clazz.getDeclaredField(injection.field());

                // find the provider
                var providerName = injection.annotationParams().get(0).getValue();
                var _provider = providers().stream().filter(p -> p.getName().equals(providerName)).findFirst();

                if (_provider.isPresent()) {
                    var value = _provider.get().getInstance().get(this.INSTANTIATED_PLUGINS_RECORDS.get(ip.clazz.getName()));
                    field.setAccessible(true);
                    LOGGER.debug("Injecting {} into field {} of class {}", PluginUtils.name(_provider.get().getInstance()), field.getName(), ip.instance.getClass().getName());
                    field.set(ip.instance, value);
                } else {
                    throw new NoProviderException("no provider found for @Inject(\"" + providerName + "\")");
                }
            } catch(NoSuchFieldException nsfe) {
                // should not happen
                throw new InvocationTargetException(nsfe);
            }
        }
    }

    private void invokeOnInitMethods(InstatiatedPlugin ip) throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {
        // finds @Inject methods

        // we need to process methods that are annotated only with @Inject
        // and have only one method parameter

        var injections = new ArrayList<MethodInjectionDescriptor>();
        ip.descriptor.injections().stream()
            .filter(i -> i instanceof MethodInjectionDescriptor)
            .map(i -> (MethodInjectionDescriptor) i)
            .forEach(injections::add);

        for (var injection : injections) {
            if (OnInit.class.equals(injection.clazz()) && ip.descriptor.injections().stream()
                    .filter(p -> p instanceof MethodInjectionDescriptor)
                    .map(p -> (MethodInjectionDescriptor) p)
                    .filter(p -> p.methodHash() == injection.methodHash()).count() == 1) {
                // try to inovke @OnInit() method
                try {
                    ip.clazz.getDeclaredMethod(injection.method()).invoke(ip.instance);
                } catch (NoSuchMethodException nme) {
                    throw new ConfigurationException(ip.type + " " + ip.name + " has an invalid method @OnInit " + injection.method() + "()");
                } catch (Throwable t) {
                    throw new ConfigurationException("Error executing @OnInit method " + injection.method() + " of " + ip.type + " " + ip.name, getRootException(t));
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

    private Boolean secure(Class<Plugin> p) {
        return p.getAnnotation(RegisterPlugin.class).secure();
    }

    private static record InstatiatedPlugin(String name, String type, PluginDescriptor descriptor, Class<Plugin> clazz, Object instance) {
    }
}

class NoProviderException extends Exception {
    public NoProviderException(String msg) {
        super(msg);
    }
}
