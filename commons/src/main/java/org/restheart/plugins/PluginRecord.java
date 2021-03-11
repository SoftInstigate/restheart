/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <T> generic type
 */
public class PluginRecord<T extends Plugin> {
    private final String name;
    private final String description;
    private final boolean secure;
    private final boolean enabledByDefault;
    private final String className;
    private final T instance;
    private final Map<String, Object> confArgs;

    /**
     * The key to enable plugins
     */
    public static final String PLUGIN_ENABLED_KEY = "enabled";

    public static final String PLUGIN_SECURE_KEY = "secured";

    public PluginRecord(String name,
            String description,
            final boolean secure,
            boolean enabledByDefault,
            String className,
            T instance,
            Map<String, Object> confArgs) {
        this.name = name;
        this.description = description;
        this.secure = secure;
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
     * @return true if secure taking into accout the configuration overrides
     */
    public boolean isSecure() {
        return isSecure(secure, getConfArgs());
    }

    /**
     * @return true if enabled taking into accout the configuration overrides
     */
    public boolean isEnabled() {
        return isEnabled(enabledByDefault, getConfArgs());
    }

    /**
     * @param enabledByDefault
     * @param confArgs
     * @return true if enabled taking into accout the configuration overrides
     */
    public static boolean isEnabled(boolean enabledByDefault, Map<String, Object> confArgs) {
        return confArgs == null
                ? enabledByDefault
                : confArgs.containsKey(PLUGIN_ENABLED_KEY)
                && confArgs.get(PLUGIN_ENABLED_KEY) != null
                && confArgs.get(PLUGIN_ENABLED_KEY) instanceof Boolean
                ? (Boolean) confArgs.get(PLUGIN_ENABLED_KEY)
                : enabledByDefault;
    }

    /**
     * @param secure
     * @param confArgs
     * @return true if secure taking into accout the configuration overrides
     */
    public static boolean isSecure(boolean secure, Map<String, Object> confArgs) {
        return confArgs == null
                ? secure
                : confArgs.containsKey(PLUGIN_SECURE_KEY)
                && confArgs.get(PLUGIN_SECURE_KEY) != null
                && confArgs.get(PLUGIN_SECURE_KEY) instanceof Boolean
                ? (Boolean) confArgs.get(PLUGIN_SECURE_KEY)
                : secure;
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
