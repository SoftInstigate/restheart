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
// import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.util.ArrayList;

/**
 * this class is configured to be initialized at build time by native-image
 * note: we cannot use logging in this class, otherwise native-image will fail
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GraalPluginsScanner {
    private static final String REGISTER_PLUGIN_CLASS_NAME = "org.restheart.plugins.RegisterPlugin";
    private static final String INITIALIZER_CLASS_NAME = "org.restheart.plugins.Initializer";
    private static final String AUTHMECHANISM_CLASS_NAME = "org.restheart.plugins.security.AuthMechanism";
    private static final String AUTHORIZER_CLASS_NAME = "org.restheart.plugins.security.Authorizer";
    private static final String TOKEN_MANAGER_CLASS_NAME = "org.restheart.plugins.security.TokenManager";
    private static final String AUTHENTICATOR_CLASS_NAME = "org.restheart.plugins.security.Authenticator";
    private static final String INTERCEPTOR_CLASS_NAME = "org.restheart.plugins.Interceptor";
    private static final String SERVICE_CLASS_NAME= "org.restheart.plugins.Service";

    public static final ArrayList<PluginDescriptor> PLUGINS = new ArrayList<>();
    public static final ArrayList<InjectionDescriptor> INJECTIONS = new ArrayList<>();

    // ClassGraph.scan() at class initialization time to support native image
    // generation with GraalVM
    // see https://github.com/SoftInstigate/classgraph-on-graalvm
    static {
        var classGraph = new ClassGraph()
                .disableModuleScanning() // added for GraalVM
                .disableDirScanning() // added for GraalVM
                .disableNestedJarScanning() // added for GraalVM
                .disableRuntimeInvisibleAnnotations() // added for GraalVM
                .overrideClassLoaders(GraalPluginsScanner.class.getClassLoader()) // added for GraalVM
                .enableAnnotationInfo().enableMethodInfo();

        try (var scanResult = classGraph.scan(8)) {
            PLUGINS.addAll(collectPlugins(scanResult, INITIALIZER_CLASS_NAME));
            PLUGINS.addAll(collectPlugins(scanResult, AUTHMECHANISM_CLASS_NAME));
            PLUGINS.addAll(collectPlugins(scanResult, AUTHORIZER_CLASS_NAME));
            PLUGINS.addAll(collectPlugins(scanResult, TOKEN_MANAGER_CLASS_NAME));
            PLUGINS.addAll(collectPlugins(scanResult, AUTHENTICATOR_CLASS_NAME));
            PLUGINS.addAll(collectPlugins(scanResult, INTERCEPTOR_CLASS_NAME));
            PLUGINS.addAll(collectPlugins(scanResult, SERVICE_CLASS_NAME));

            var INJECTIONS = collectInjectDependencies(PLUGINS);
        }

        System.out.println("*******************************************");

        System.out.println(GraalPluginsScanner.PLUGINS);

        System.out.println("*******************************************");
    }

    /**
     * @param type the class of the plugin , e.g. Initializer.class
     */
    @SuppressWarnings("unchecked")
    private static ArrayList<PluginDescriptor> collectPlugins(ScanResult scanResult, String className) {
        System.out.println("************************* collectPlugins " + className);
        var ret = new ArrayList<PluginDescriptor>();

        System.out.println("************************* getting registered plugins");
        var registeredPlugins = scanResult.getClassesWithAnnotation(REGISTER_PLUGIN_CLASS_NAME);

        if (registeredPlugins == null || registeredPlugins.isEmpty()) {
            return ret;
        }

        System.out.println("************************* getting classes implementing interface");
        ClassInfoList listOfType;

        if (className.equals(AUTHENTICATOR_CLASS_NAME)) {
            var tms = scanResult.getClassesImplementing(TOKEN_MANAGER_CLASS_NAME);

            listOfType = scanResult.getClassesImplementing(className).exclude(tms);
        } else {
            listOfType = scanResult.getClassesImplementing(className);
        }

        // TO CHECK we had this!!
        // if (type.isInterface()) {
            // above statement
        // } else {
        //     listOfType = scanResult.getSubclasses(className);
        // }

        var plugins = registeredPlugins.intersect(listOfType);

        // System.out.println("************************* sorting " + className);
        // // sort by priority
        // plugins.sort((ClassInfo ci1, ClassInfo ci2) -> {
        //     return Integer.compare(annotationParam(ci1, "priority"), annotationParam(ci2, "priority"));
        // });

        plugins.stream().forEachOrdered(plugin -> {
            ret.add(new PluginDescriptor(plugin.getName()));
        });

        return ret;
    }

    private static ArrayList<InjectionDescriptor> collectInjectDependencies(ArrayList<PluginDescriptor> plugins) {
        var ret = new ArrayList<InjectionDescriptor>();

        plugins.stream().forEachOrdered(plugin -> ret.addAll(collectInjectMethods(plugin)));

        return ret;
    }

    private static ArrayList<InjectionDescriptor> collectInjectMethods(PluginDescriptor ip) {
        return new ArrayList<InjectionDescriptor>();

        // collectInjectConfigurationMethods(ip.pluginName, ip.pluginType,
        // ip.pluginClassInfo, ip.pluingInstance, ip.confs);

        // collectInjectPluginsRegistryMethods(ip.pluginName, ip.pluginType,
        // ip.pluginClassInfo, ip.pluingInstance);

        // collectInjectConfigurationAndPluginsRegistryMethods(ip.pluginName,
        // ip.pluginType, ip.pluginClassInfo,
        // ip.pluingInstance, ip.confs);
    }

    // private static void collectInjectConfigurationMethods(String pluginName, String pluginType,
    //         ClassInfo pluginClassInfo, Object pluingInstance, Map confs)
    //         throws InstantiationException, IllegalAccessException, InvocationTargetException {

    //     // finds @InjectConfiguration methods
    //     var mil = pluginClassInfo.getDeclaredMethodInfo();

    //     for (var mi : mil) {
    //         if (mi.hasAnnotation(InjectConfiguration.class.getName())
    //                 && !mi.hasAnnotation(InjectPluginsRegistry.class.getName())) {
    //             var ai = mi.getAnnotationInfo(InjectConfiguration.class.getName());

    //             // check configuration scope
    //             var allConfScope = ai.getParameterValues().stream()
    //                     .anyMatch(p -> "scope".equals(p.getName())
    //                             && (ConfigurationScope.class.getName() + "." + ConfigurationScope.ALL.name())
    //                                     .equals(p.getValue().toString()));

    //             // var scopedConf = (Map) (allConfScope ? Bootstrapper.getConfiguration().toMap()
    //             //         : confs != null ? confs.get(pluginName) : null);

    //             // if (scopedConf == null) {
    //             //     LOGGER.warn(
    //             //             "{} {} defines method {} with @InjectConfiguration " + "but no configuration found for it",
    //             //             pluginType, pluginName, mi.getName());
    //             // }

    //             Map scopedConf = null;

    //             // try to inovke @InjectConfiguration method
    //             try {
    //                 pluginClassInfo.loadClass(false).getDeclaredMethod(mi.getName(), Map.class).invoke(pluingInstance,
    //                         scopedConf);
    //             } catch (NoSuchMethodException nme) {
    //                 throw new ConfigurationException(pluginType + " " + pluginName
    //                         + " has an invalid method with @InjectConfiguration. " + "Method signature must be "
    //                         + mi.getName() + "(Map<String, Object> configuration)");
    //             }
    //         }
    //     }
    // }

    // private static void collectInjectPluginsRegistryMethods(String pluginName, String pluginType,
    //         ClassInfo pluginClassInfo, Object pluingInstance)
    //         throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {

    //     // finds @InjectPluginRegistry methods
    //     var mil = pluginClassInfo.getDeclaredMethodInfo();

    //     for (var mi : mil) {
    //         if (mi.hasAnnotation(InjectPluginsRegistry.class.getName())
    //                 && !mi.hasAnnotation(InjectConfiguration.class.getName())) {
    //             // try to inovke @InjectPluginRegistry method
    //             try {
    //                 pluginClassInfo.loadClass(false).getDeclaredMethod(mi.getName(), PluginsRegistry.class)
    //                         .invoke(pluingInstance, PluginsRegistryImpl.getInstance());
    //             } catch (NoSuchMethodException nme) {
    //                 throw new ConfigurationException(
    //                         pluginType + " " + pluginName + " has an invalid method with @InjectPluginsRegistry. "
    //                                 + "Method signature must be " + mi.getName() + "(PluginsRegistry pluginsRegistry)");
    //             }
    //         }
    //     }
    // }

    // private static void collectInjectConfigurationAndPluginsRegistryMethods(String pluginName, String pluginType,
    //         ClassInfo pluginClassInfo, Object pluingInstance, Map confs)
    //         throws ConfigurationException, InstantiationException, IllegalAccessException, InvocationTargetException {

    //     // finds @InjectConfiguration methods
    //     var mil = pluginClassInfo.getDeclaredMethodInfo();

    //     for (var mi : mil) {
    //         if (mi.hasAnnotation(InjectConfiguration.class.getName())
    //                 && mi.hasAnnotation(InjectPluginsRegistry.class.getName())) {
    //             var ai = mi.getAnnotationInfo(InjectConfiguration.class.getName());

    //             // check configuration scope
    //             var allConfScope = ai.getParameterValues().stream()
    //                     .anyMatch(p -> "scope".equals(p.getName())
    //                             && (ConfigurationScope.class.getName() + "." + ConfigurationScope.ALL.name())
    //                                     .equals(p.getValue().toString()));

    //             // var scopedConf = (Map) (allConfScope ? Bootstrapper.getConfiguration().toMap()
    //             //         : confs != null ? confs.get(pluginName) : null);

    //             // if (scopedConf == null) {
    //             //     LOGGER.warn(
    //             //             "{} {} defines method {} with @InjectConfiguration " + "but no configuration found for it",
    //             //             pluginType, pluginName, mi.getName());
    //             // }

    //             Map scopedConf = null;

    //             // try to inovke @InjectConfiguration method
    //             try {
    //                 pluginClassInfo.loadClass(false).getDeclaredMethod(mi.getName(), Map.class, PluginsRegistry.class)
    //                         .invoke(pluingInstance, scopedConf, PluginsRegistryImpl.getInstance());
    //             } catch (NoSuchMethodException nme) {
    //                 throw new ConfigurationException(
    //                         pluginType + " " + pluginName + " has an invalid method with @InjectConfiguration"
    //                                 + " and @InjectPluginsRegistry." + " Method signature must be " + mi.getName()
    //                                 + "(Map<String, Object> configuration," + " PluginsRegistry pluginsRegistry)");
    //             }
    //         }
    //     }
    // }

    private static Throwable getRootException(Throwable t) {
        if (t.getCause() != null) {
            return getRootException(t.getCause());
        } else {
            return t;
        }
    }

    // @SuppressWarnings("unchecked")
    // private static <T extends Object> T annotationParam(ClassInfo ci, String param) {
    //     var annotationInfo = ci.getAnnotationInfo(REGISTER_PLUGIN_CLASS_NAME);
    //     var annotationParamVals = annotationInfo.getParameterValues();

    //     return (T) annotationParamVals.getValue(param);
    // }

    static class PluginDescriptor {
        private final String clazz;

        PluginDescriptor(String clazz) {
            this.clazz = clazz;
        }

        @Override
        public String toString() {
            return "{ clazz:" + this.clazz + " }";
        }
    }

    static class InjectionDescriptor {
        private final String clazz;
        private final String method;
        private final String injection;

        InjectionDescriptor(String clazz, String method, String injection) {
            this.clazz = clazz;
            this.method = method;
            this.injection = injection;
        }
    }
}
