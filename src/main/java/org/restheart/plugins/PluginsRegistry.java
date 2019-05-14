/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.plugins;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.restheart.Bootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PluginsRegistry {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(PluginsRegistry.class);

    private static final String REGISTER_PLUGIN_CLASS_NAME = RegisterPlugin.class
            .getName();

    private final Set<PluginRecord<Initializer>> initializers
            = new LinkedHashSet<>();

    private final Set<PluginRecord<Service>> services
            = new LinkedHashSet<>();

    private final Map<String, PluginRecord<Transformer>> transformers
            = new LinkedHashMap<>();

    private final Map<String, PluginRecord<Hook>> hooks
            = new LinkedHashMap<>();

    private final Map<String, PluginRecord<Checker>> checkers
            = new LinkedHashMap<>();

    private final Map<String, Map<String, Object>> confs = consumeConfiguration();

    private PluginsRegistry() {
        findInitializers();
        findServices();
        findTransformers();
        findHooks();
        findCheckers();
    }

    public static PluginsRegistry getInstance() {
        return ExtensionsRegistryHolder.INSTANCE;
    }

    private static class ExtensionsRegistryHolder {
        private static final PluginsRegistry INSTANCE = new PluginsRegistry();
    }

    /**
     *
     * @return the initializers sorted by priority
     */
    public Set<PluginRecord<Initializer>> getInitializers() {
        return initializers;
    }

    /**
     * @return the services
     */
    public Set<PluginRecord<Service>> getServices() {
        return services;
    }

    /**
     * @param name
     * @return the transformer called name
     */
    public PluginRecord<Transformer> getTransformer(String name)
            throws NoSuchElementException {
        if (!transformers.containsKey(name)) {
            throw new NoSuchElementException("Transformer "
                    + name
                    + " is not registered");
        } else {
            return transformers.get(name);
        }
    }

    /**
     * @param name
     * @return the transformer called name
     */
    public PluginRecord<Checker> getChecker(String name)
            throws NoSuchElementException {
        if (!checkers.containsKey(name)) {
            throw new NoSuchElementException("Checker "
                    + name
                    + " is not registered");
        } else {
            return checkers.get(name);
        }
    }

    /**
     * @param name
     * @return the hook called name
     */
    public PluginRecord<Hook> getHook(String name)
            throws NoSuchElementException {
        if (!hooks.containsKey(name)) {
            throw new NoSuchElementException("Hook "
                    + name
                    + " is not registered");
        } else {
            return hooks.get(name);
        }
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

    /**
     * finds the initializers
     */
    @SuppressWarnings("unchecked")
    private void findInitializers() {
        try (var scanResult = new ClassGraph()
                .enableAnnotationInfo()
                .scan()) {
            var registeredPlugins = scanResult
                    .getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);

            var listOfType = scanResult.getClassesImplementing(Initializer.class
                    .getName());

            var registeredInitializers = registeredPlugins.intersect(listOfType);

            // sort @Initializers by priority
            registeredInitializers.sort(new Comparator<ClassInfo>() {
                @Override
                public int compare(ClassInfo ci1, ClassInfo ci2) {
                    int p1 = annotationParam(ci1, "priority");

                    int p2 = annotationParam(ci2, "priority");

                    return Integer.compare(p1, p2);
                }
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
                            (Initializer) i,
                            confs.get(name));

                    if (pr.isEnabled()) {
                        this.initializers.add(pr);
                        LOGGER.info("Registered initializer {}: {}",
                                name,
                                description);
                    } else {
                        LOGGER.debug("Initializer {} is disabled", name);
                    }
                } catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException
                        | NoSuchMethodException t) {
                    LOGGER.error("Error registering initializer {}",
                            registeredInitializer.getName(),
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

                } catch (NoSuchMethodException nsme) {
                    LOGGER.error("Plugin class {} annotated with "
                            + "@RegisterPlugin must have a constructor "
                            + "with single argument of type Map<String, Object>",
                            registeredService.getName());
                } catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException t) {
                    LOGGER.error("Error registering service {}",
                            registeredService.getName(),
                            t);
                }
            });
        }
    }

    /**
     * finds the transformers
     */
    @SuppressWarnings("unchecked")
    private void findTransformers() {
        try (var scanResult = new ClassGraph()
                .enableAnnotationInfo()
                .scan()) {
            var registeredPlugins = scanResult
                    .getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);

            var listOfType = scanResult.getClassesImplementing(Transformer.class.getName());

            var registeredTransformers = registeredPlugins.intersect(listOfType);

            registeredTransformers.stream().forEach(registeredTransformer -> {
                Object transformer;

                try {
                    String name = annotationParam(registeredTransformer,
                            "name");

                    String description = annotationParam(registeredTransformer,
                            "description");

                    Boolean enabledByDefault = annotationParam(registeredTransformer,
                            "enabledByDefault");

                    transformer = registeredTransformer.loadClass(false)
                            .getConstructor()
                            .newInstance();

                    var pr = new PluginRecord(
                            name,
                            description,
                            enabledByDefault,
                            registeredTransformer.getName(),
                            (Transformer) transformer,
                            confs.get(name));

                    if (pr.isEnabled()) {
                        this.transformers.put(name, pr);
                        LOGGER.info("Registered transformer {}: {}",
                                name,
                                description);
                    } else {
                        LOGGER.debug("Transformer {} is disabled", name);
                    }
                } catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException
                        | NoSuchMethodException t) {
                    LOGGER.error("Error registering transformer {}",
                            registeredTransformer.getName(),
                            t);
                }
            });
        }
    }

    /**
     * finds the hooks
     */
    @SuppressWarnings("unchecked")
    private void findHooks() {
        try (var scanResult = new ClassGraph()
                .enableAnnotationInfo()
                .scan()) {
            var registeredPlugins = scanResult
                    .getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);

            var listOfType = scanResult.getClassesImplementing(Hook.class.getName());

            var registeredHooks = registeredPlugins.intersect(listOfType);

            registeredHooks.stream().forEach(registeredHook -> {
                Object hook;

                try {
                    String name = annotationParam(registeredHook,
                            "name");
                    String description = annotationParam(registeredHook,
                            "description");
                    Boolean enabledByDefault = annotationParam(registeredHook,
                            "enabledByDefault");

                    hook = registeredHook.loadClass(false)
                            .getConstructor()
                            .newInstance();

                    var pr = new PluginRecord(
                            name,
                            description,
                            enabledByDefault,
                            registeredHook.getName(),
                            (Hook) hook,
                            confs.get(name));

                    if (enabledByDefault) {
                        this.hooks.put(name, pr);
                        LOGGER.info("Registered hook {}: {}",
                                name,
                                description);
                    } else {
                        LOGGER.debug("Hook {} is disabled", name);
                    }
                } catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException
                        | NoSuchMethodException t) {
                    LOGGER.error("Error registering hook {}",
                            registeredHook.getName(),
                            t);
                }
            });
        }
    }

    /**
     * finds the checkers
     */
    @SuppressWarnings("unchecked")
    private void findCheckers() {
        try (var scanResult = new ClassGraph()
                .enableAnnotationInfo()
                .scan()) {
            var registeredPlugins = scanResult
                    .getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);

            var listOfType = scanResult.getClassesImplementing(Checker.class.getName());

            var registeredCheckers = registeredPlugins.intersect(listOfType);

            registeredCheckers.stream().forEach(registeredChecker -> {
                Object checker;

                try {
                    String name = annotationParam(registeredChecker,
                            "name");
                    String description = annotationParam(registeredChecker,
                            "description");
                    Boolean enabledByDefault = annotationParam(registeredChecker,
                            "enabledByDefault");

                    checker = registeredChecker.loadClass(false)
                            .getConstructor()
                            .newInstance();

                    var pr = new PluginRecord(
                            name,
                            description,
                            enabledByDefault,
                            registeredChecker.getName(),
                            (Checker) checker,
                            confs.get(name));

                    if (enabledByDefault) {
                        this.checkers.put(name, pr);
                        LOGGER.info("Registered checker {}: {}",
                                name,
                                description);
                    } else {
                        LOGGER.debug("Checker {} is disabled", name);
                    }
                } catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException
                        | NoSuchMethodException t) {
                    LOGGER.error("Error registering checker {}",
                            registeredChecker.getName(),
                            t);
                }
            });
        }
    }

    private static <T extends Object> T annotationParam(ClassInfo ci,
            String param) {
        var annotationInfo = ci.getAnnotationInfo(REGISTER_PLUGIN_CLASS_NAME);
        var annotationParamVals = annotationInfo.getParameterValues();

        // The Route annotation has a parameter named "path"
        return (T) annotationParamVals.getValue(param);
    }
}
