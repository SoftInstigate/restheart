/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
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
     * file to pass confArgs
     *
     * @return the name of the plugin
     */
    String name();

    /**
     * Describes the plugin
     *
     * @return the description of the plugin
     */
    String description();

    /**
     * Set the order of execution (less is higher priority)
     *
     * @return the execution priority (less is higher priority)
     */
    int priority() default 10;

    /**
     * Set to true to enable the plugin by default.Otherwise it can be enabled
     * setting the configuration argument 'enabled'
     *
     * @return true if enabled by default
     */
    boolean enabledByDefault() default true;

    /**
     * Only used by Services
     *
     * Sets the default URI of the Service. If not specified the Service default
     * URI is /&lt;name&gt;
     *
     * @return the URI of the Service
     */
    String defaultURI() default "";

    /**
     * Only used by Interceptors
     *
     * @return the intercept point of the Interceptor
     */
    InterceptPoint interceptPoint() default InterceptPoint.REQUEST_AFTER_AUTH;

    /**
     * Only used by Initializers
     *
     * @return the init point of the Intitialier
     */
    InitPoint initPoint() default InitPoint.AFTER_STARTUP;

    /**
     * Only used by Interceptors of proxied resources (the content is always
     * available to Interceptor of Services)
     *
     * Set it to true to make available the content of the request (if
     * interceptPoint is REQUEST_BEFORE_AUTH or REQUEST_AFTER_AUTH) or of the
     * response (if interceptPoint is RESPONSE or RESPONSE_ASYNC)
     *
     * @return true if the Interceptor requires the content
     */
    boolean requiresContent() default false;
}
