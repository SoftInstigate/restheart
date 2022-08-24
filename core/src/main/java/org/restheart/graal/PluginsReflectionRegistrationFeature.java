/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
/** WIP
 * Automate reflect configuratio currently done via GenerateGraalvmReflectConfig
 *
 */

package org.restheart.graal;

import com.oracle.svm.core.annotate.AutomaticFeature;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectMongoClient;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsScanner;

@AutomaticFeature
/**
 * Automates reflection configuration of plugins for native-image builds
 *
 * @see https://www.graalvm.org/22.2/reference-manual/native-image/dynamic-features/Reflection/#configuration-with-features
 */
public class PluginsReflectionRegistrationFeature implements Feature {
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        PluginsScanner.allPluginsClassNames().stream()
                .map(this::clazz)
                .filter(c -> c != null)
                .forEach(this::registerAll);
    }

    private Class<?> clazz(String clazzName) {
        try {
            return Class.forName(clazzName, false, this.getClass().getClassLoader());
        } catch (ClassNotFoundException cfe) {
            return null;
        }
    }

    /**
     * register the plugin for runtime reflection
     *
     * @param clazz
     */
    private void registerAll(Class<?> clazz) {
        RuntimeReflection.register(clazz);
        RuntimeReflection.registerForReflectiveInstantiation(clazz);
        RuntimeReflection.register(annotated(clazz.getDeclaredFields()));
        RuntimeReflection.register(annotated(clazz.getDeclaredMethods()));
    }

    /**
     * selects fields annotated with @Inject
     *
     * @param fields
     * @return an array of fileds that are annotated with @Inject
     */
    private Field[] annotated(Field... fields) {
        var list = Arrays.stream(fields)
                .filter((f -> f.getAnnotation(Inject.class) != null))
                .collect(Collectors.toList());

        return list.toArray(new Field[list.size()]);
    }

    /**
     * selects methods annotated
     * with @OnInit, @InjectMongoClient, @InjectConfiguration, @InjectPluginsRegistry
     *
     * @param fields
     * @return an array of methods that are annotated
     *         with @OnInit, @InjectMongoClient, @InjectConfiguration, @InjectPluginsRegistry
     */
    private Method[] annotated(Method... methods) {
        var list = Arrays.stream(methods)
                .filter(m -> m.getAnnotation(OnInit.class) != null
                        || m.getAnnotation(InjectMongoClient.class) != null
                        || m.getAnnotation(InjectConfiguration.class) != null
                        || m.getAnnotation(InjectPluginsRegistry.class) != null)
                .collect(Collectors.toList());

        return list.toArray(new Method[list.size()]);
    }
}
