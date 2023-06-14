/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins;

import java.util.Map;

import org.restheart.configuration.ConfigurationException;
import org.restheart.utils.PluginUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface ConfigurablePlugin extends Plugin {
    /**
     *
     * @param <V> return value
     * @param args
     * @param argKey
     * @return the string arg value of argKey from args
     * @throws ConfigurationException
     */
    @SuppressWarnings("unchecked")
    public static <V extends Object> V argValue(final Map<String, ?> args, final String argKey) throws ConfigurationException {
        if (args == null || !args.containsKey(argKey)) {
            throw new ConfigurationException("Required configuration argument '" + argKey + "' non found");
        } else {
            return (V) args.get(argKey);
        }
    }

    public static <V extends Object> V argValueOrDefault(final Map<String, ?> args, final String argKey, V value) throws ConfigurationException {
        if (args == null || !args.containsKey(argKey)) {
            return value;
        } else {
            return argValue(args, argKey);
        }
    }

    @SuppressWarnings("unchecked")
    default public <V extends Object> V arg(final Map<String, ?> args, final String argKey) throws ConfigurationException {
        if (args == null || !args.containsKey(argKey)) {
            throw new ConfigurationException("The plugin " + PluginUtils.name(this) + " requires the missing configuration argument '" + argKey + "'");
        } else {
            return (V) args.get(argKey);
        }
    }

    default public <V extends Object> V argOrDefault(final Map<String, ?> args, final String argKey, V value) throws ConfigurationException {
        if (args == null || !args.containsKey(argKey)) {
            return value;
        } else {
            return arg(args, argKey);
        }
    }
}
