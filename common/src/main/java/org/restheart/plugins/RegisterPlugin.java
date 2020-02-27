/*
 * RESTHeart Common
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.plugins;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation to register a Plugin
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterPlugin {
    /**
     * Defines the name of the plugin. The name can be used in the configuration
 file to pass confArgs
     * @return the name of the plugin
     */
    String name();

    /**
     * Describes the plugin
     * @return the description of the plugin
     */
    String description();

    /**
     * Set the order of execution (less is higher priority)
     * @return the execution priority (less is higher priority)
     */
    int priority() default 10;

    /**
     * Set to true to enable the plugin by default.Otherwise it can be enabled
 setting the configuration argument 'enabled'
     *
     * @return true if enabled by default
     */
    boolean enabledByDefault() default true;
}
