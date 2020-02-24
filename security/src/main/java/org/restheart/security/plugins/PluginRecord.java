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
package org.restheart.security.plugins;

import java.util.Map;
import org.restheart.security.ConfigurationKeys;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PluginRecord<T extends Plugin> {
    private final String name;
    private final String description;
    private final boolean enabledByDefault;
    private final String className;
    private final T instance;
    private final Map<String, Object> confArgs;

    public PluginRecord(String name,
            String description,
            boolean enabledByDefault,
            String className,
            T instance,
            Map<String, Object> confArgs) {
        this.name = name;
        this.description = description;
        this.enabledByDefault = enabledByDefault;
        this.instance = instance;
        this.className = className;
        this.confArgs = confArgs;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return the className
     */
    public String getClassName() {
        return className;
    }

    /**
     * @return the disabled
     */
    public boolean isEnabled() {
        return isEnabled(enabledByDefault, getConfArgs());
    }
    
    /**
     * @param enabledByDefault
     * @param confArgs
     * @return the disabled
     */
    public static boolean isEnabled(boolean enabledByDefault, 
            Map<String, Object> confArgs) {
        return confArgs == null
                ? enabledByDefault
                : confArgs.containsKey(ConfigurationKeys.PLUGIN_ENABLED_KEY)
                && confArgs.get(ConfigurationKeys.PLUGIN_ENABLED_KEY) instanceof Boolean
                ? (Boolean) confArgs.get(ConfigurationKeys.PLUGIN_ENABLED_KEY)
                : enabledByDefault;
    }

    /**
     * @return the confArgs
     */
    public Map<String, Object> getConfArgs() {
        return confArgs;
    }

    /**
     * @return the instance
     */
    public T getInstance() {
        return instance;
    }
}
