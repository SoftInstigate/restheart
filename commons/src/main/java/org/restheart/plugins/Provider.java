/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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

import java.lang.reflect.Type;

import org.restheart.utils.PluginUtils;

import com.google.common.reflect.TypeToken;

/**
 * Base interface for dependency providers
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Provider<T> extends ConfigurablePlugin {
    /**
     * @param caller the PluginRecord of the plugin that requires this dependency
     * @return the provided object
     */
    public T get(PluginRecord<?> caller);

    /**
     *
     * @return the name of the provider
     */
    public default String name() {
        return PluginUtils.name(this);
    }

    /**
     *
     * @return the Type of the generic parameter T
     */
    default Type type() {
        return new TypeToken<T>(getClass()) {
			private static final long serialVersionUID = 1363463867743712234L;
        }.getType();
    }

    /**
     *
     * @return the Class of the generic parameter T
     */
    default Class<? super T> rawType() {
        return new TypeToken<T>(getClass()) {
			private static final long serialVersionUID = 1363463867743712234L;
        }.getRawType();
    }
}
