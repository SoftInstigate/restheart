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
import java.util.LinkedHashSet;
import java.util.Map;
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

    private final Set<PluginRecord<Initializer>> initializers = new LinkedHashSet<>();

    private final Set<PluginRecord<Service>> services = new LinkedHashSet<>();

    private final Map<String, Map<String, Object>> confs = consumeConfiguration();

    private PluginsRegistry() {
        findInitializers();
        findServices();
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
     * finds the initializers defined via @RegisterInitializer annotation
     */
    @SuppressWarnings("unchecked")
    private void findInitializers() {
        String registerPluginAnnotationClassName = RegisterPlugin.class.getName();
        String initializerIntefaceClassName = Initializer.class.getName();

        try (var scanResult = new ClassGraph()
                .enableAnnotationInfo()
                .scan()) {
            var registeredPlugins = scanResult
                    .getClassesWithAnnotation(registerPluginAnnotationClassName);
                    
            var initializers = scanResult
                    .getClassesImplementing(initializerIntefaceClassName);
            
            var registeredInitializers = registeredPlugins.intersect(initializers);

            // sort @Initializers by priority
            registeredInitializers.sort(new Comparator<ClassInfo>() {
                @Override
                public int compare(ClassInfo ci1, ClassInfo ci2) {
                    int p1 = annotationParam(ci1, registerPluginAnnotationClassName, "priority");

                    int p2 = annotationParam(ci2, registerPluginAnnotationClassName, "priority");

                    return Integer.compare(p1, p2);
                }
            });

            for (var ci : registeredInitializers) {
                Object i;

                try {
                    i = ci.loadClass(false)
                            .getConstructor()
                            .newInstance();

                    String name = annotationParam(ci, registerPluginAnnotationClassName, "name");
                    String description = annotationParam(ci, registerPluginAnnotationClassName, "description");

                    if (i instanceof Initializer) {
                        this.initializers.add(new PluginRecord(
                                name,
                                description,
                                ci.getName(),
                                (Initializer) i,
                                confs.get(name)));
                    } else {
                        LOGGER.error("Plugin class {} annotated with "
                                + "@RegisterInitializer must implement interface Initalizer",
                                ci.getName());
                    }

                } catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException
                        | NoSuchMethodException t) {
                    LOGGER.error("Error registering initializer {}",
                            ci.getName(),
                            t);
                }
            }
        }
    }

    /**
     * finds the service defined via @RegisterServic annoetation
     */
    @SuppressWarnings("unchecked")
    private void findServices() {
        String registerPluginAnnotationClassName = RegisterPlugin.class.getName();
        String serviceClassName = Service.class.getName();
        
        
        try (var scanResult = new ClassGraph()
                .enableAnnotationInfo()
                .scan()) {
            var registeredPlugins = scanResult
                    .getClassesWithAnnotation(registerPluginAnnotationClassName);
            
            var services = scanResult
                    .getSubclasses(serviceClassName);
            
            var registeredServices = registeredPlugins.intersect(services);

            for (var registeredService : registeredServices) {
                Object srv;

                try {
                    String name = annotationParam(registeredService,
                            registerPluginAnnotationClassName,
                            "name");

                    String description = annotationParam(registeredService,
                            registerPluginAnnotationClassName,
                            "description");

                    srv = registeredService.loadClass(false)
                            .getConstructor(Map.class)
                            .newInstance(confs.get(name));

                    if (srv instanceof Service) {
                        this.services.add(
                                new PluginRecord(
                                        name,
                                        description,
                                        registeredService.getName(),
                                        (Service) srv,
                                        confs.get(name)));
                    } else {
                        LOGGER.error("Plugin class {} annotated with "
                                + "@RegisterService must extend abstract class Service",
                                registeredService.getName());
                    }

                } catch (NoSuchMethodException nsme) {
                    LOGGER.error("Plugin class {} annotated with "
                            + "@RegisterService must have a constructor "
                            + "with single argument of type Map<String, Object>",
                            registeredService.getName());
                } catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException t) {
                    LOGGER.error("Error registering service {}",
                            registeredService.getName(),
                            t);
                }
            }
        }
    }

    private static <T extends Object> T annotationParam(ClassInfo ci,
            String annotationClassName,
            String param) {
        var annotationInfo = ci.getAnnotationInfo(annotationClassName);
        var annotationParamVals = annotationInfo.getParameterValues();

        // The Route annotation has a parameter named "path"
        return (T) annotationParamVals.getValue(param);
    }
}
