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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import static org.restheart.plugins.ConfigurationScope.OWN;

/**
 * Plugin annotation that sets a method to get the configuration's properties as
 * a {@literal Map<String, Object>}
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InjectConfiguration {

    /**
     * Describes the plugin
     *
     * @return the description of the plugin
     */
    ConfigurationScope scope() default OWN;

    /**
     * Describes the plugin
     *
     * @return the description of the plugin
     */
    boolean requiresPluginRegistry() default false;
}
