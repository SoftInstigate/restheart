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
package org.restheart.extensions;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.bson.BsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ExtensionsRegistry {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(ExtensionsRegistry.class);

    private final Map<String, BsonValue> confArgs = new HashMap<>();
    
    private final Set<Consumer> initializers = new LinkedHashSet<>();
    

    private final ScanResult scanResult = new ClassGraph()
            .enableAnnotationInfo()
            .scan();

    private ExtensionsRegistry() {
        findInitializers();
    }

    public static ExtensionsRegistry getInstance() {
        return ExtensionsRegistryHolder.INSTANCE;
    }

    private static class ExtensionsRegistryHolder {
        private static final ExtensionsRegistry INSTANCE = new ExtensionsRegistry();
    }
    
    /**
     *
     * @return the initializers sorted by priority
     */
    public Set<Consumer> getInitializers() {
        return initializers;
    }
    
    /**
     *
     * @return the initializers sorted by priority
     */
    public BsonValue getConfArgs(String name) {
        return confArgs.get(name);
    }

    /**
     * runs the initializers defined via @Initializer annotation
     */
    private void findInitializers() {
        String annotationClass = "org.restheart.extensions.Initializer";

        try (this.scanResult) {

            var cil = scanResult
                    .getClassesWithAnnotation(annotationClass);

            // sort @Initializers by priority
            cil.sort(new Comparator<ClassInfo>() {
                @Override
                public int compare(ClassInfo ci1, ClassInfo ci2) {
                    var ai1 = (org.restheart.extensions.Initializer) ci1
                            .getAnnotationInfo(annotationClass)
                            .loadClassAndInstantiate();

                    var ai2 = (org.restheart.extensions.Initializer) ci2
                            .getAnnotationInfo(annotationClass)
                            .loadClassAndInstantiate();

                    return Integer.compare(ai1.priority(), ai2.priority());
                }
            });

            for (var ci : cil) {
                Object i;

                try {
                    i = ci.loadClass(false)
                            .getConstructor()
                            .newInstance();

                    if (i instanceof Consumer) {
                        this.initializers.add(((Consumer) i));
                    } else {
                        LOGGER.error("Extension class {} annotated with "
                                + "@Initializer must implement interface Runnable",
                                ci.getName());
                    }

                } catch (InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException
                        | NoSuchMethodException t) {
                    LOGGER.error("Error instantiating initializer {}",
                            ci.getName(),
                            t);
                }
            }
        }
    }
}
