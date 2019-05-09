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
import io.github.classgraph.ScanResult;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.bson.BsonDocument;
import org.restheart.Bootstrapper;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.restheart.plugins.init.Initializer;
import org.restheart.plugins.init.RegisterInitializer;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PluginsRegistry {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(PluginsRegistry.class);

    private final Set<PluginRecord<Initializer>> initializers = new LinkedHashSet<>();

    private final Map<String, BsonDocument> confs = consumeConfiguration();

    private final ScanResult scanResult = new ClassGraph()
            .enableAnnotationInfo()
            .scan();

    private PluginsRegistry() {
        findInitializers();
    }

    public static PluginsRegistry getInstance() {
        return ExtensionsRegistryHolder.INSTANCE;
    }

    private static class ExtensionsRegistryHolder {
        private static final PluginsRegistry INSTANCE = new PluginsRegistry();
    }

    /**
     *
     * @return the initializers sorted by priority as map(name -> instance)
     */
    public Set<PluginRecord<Initializer>> getInitializers() {
        return initializers;
    }

    private Map<String, BsonDocument> consumeConfiguration() {
        Map<String, Map<String, Object>> pluginsArgs = Bootstrapper
                .getConfiguration()
                .getPluginsArgs();

        Map<String, BsonDocument> confs = new HashMap<>();

        pluginsArgs.forEach((name, params) -> {
            BsonDocument args;
            if (params instanceof Map) {
                args = JsonUtils.toBsonDocument((Map) params);
            } else {
                args = new BsonDocument();
            }

            confs.put(name, args);
        });

        return confs;
    }

    /**
     * runs the initializers defined via @Initializer annotation
     */
    @SuppressWarnings("unchecked")
    private void findInitializers() {
        String annotationClassName = RegisterInitializer.class.getName();

        try (this.scanResult) {
            var cil = scanResult
                    .getClassesWithAnnotation(annotationClassName);

            // sort @Initializers by priority
            cil.sort(new Comparator<ClassInfo>() {
                @Override
                public int compare(ClassInfo ci1, ClassInfo ci2) {
                    int p1 = annotationParam(ci1, annotationClassName, "priority");

                    int p2 = annotationParam(ci2, annotationClassName, "priority");

                    return Integer.compare(p1, p2);
                }
            });

            for (var ci : cil) {
                Object i;

                try {
                    i = ci.loadClass(false)
                            .getConstructor()
                            .newInstance();

                    String name = annotationParam(ci, annotationClassName, "name");
                    String description = annotationParam(ci, annotationClassName, "description");

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

    private static <T extends Object> T annotationParam(ClassInfo ci,
            String annotationClassName,
            String param) {
        var annotationInfo = ci.getAnnotationInfo(annotationClassName);
        var annotationParamVals = annotationInfo.getParameterValues();

        // The Route annotation has a parameter named "path"
        return (T) annotationParamVals.getValue(param);
    }
}
