/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.restheart.plugins.security.Authorizer;

/**
 * Annotation to register a Plugin
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
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
     * Set the order of execution (less is higher priority). Default value is 10
     *
     * @return the execution priority (less is higher priority)
     */
    int priority() default 10;

    /**
     * Only used by Services
     *
     * Set to true to execute the service only if authentication and authorization
     * succeed. The value can be overridden setting the configuration argument
     * 'secure'
     *
     * @return true if secured
     */
    boolean secure() default false;

    /**
     * Set to true to enable the plugin by default. Otherwise it can be enabled
     * setting the configuration argument 'enabled'
     *
     * @return true if enabled by default
     */
    boolean enabledByDefault() default true;

    /**
     * Only used by Services
     *
     * Sets the default URI of the Service. If not specified the Service default URI
     * is /&lt;name&gt;
     *
     * @return the URI of the Service
     */
    String defaultURI() default "";

    /**
     * Only used by Services
     *
     * Sets the URI match policy of the Service.
     *
     * @return the URI match policy of the Service.
     */
    MATCH_POLICY uriMatchPolicy() default MATCH_POLICY.PREFIX;

    public enum MATCH_POLICY {
        EXACT, PREFIX
    };

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

    /**
     * Set to true to avoid interceptors to be executed on requests handled by this
     * plugin. Interceptor with interceptPoint=BEFORE_AUTH
     *
     * @return an array containing the InterceptPoints of the Interceptors to not
     *         execute
     */
    InterceptPoint[] dontIntercept() default {};

    /**
     * Only used by Authorizers
     *
     * A request is allowed when no VETOER denies it and any ALLOWER allows it
     *
     * @return the authorizer type
     */
    Authorizer.TYPE authorizerType() default Authorizer.TYPE.ALLOWER;

    /**
     * Set to true to have the service dispached to the working thread pool, false
     * to be executed directly by the IO thread
     *
     * @return true if the service executes blocking calls
     */
    boolean blocking() default true;
}
