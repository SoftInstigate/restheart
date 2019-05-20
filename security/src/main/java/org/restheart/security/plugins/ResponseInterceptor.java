/*
 * RESTHeart Security
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
package org.restheart.security.plugins;

/**
 *
 * A response interceptor can snoop and modify the response from the proxied
 * resource before sending it to the client. Can be setup by an Initializer
 * using PluginsRegistry.getResponseInterceptors().add()
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public interface ResponseInterceptor extends Interceptor {

    /**
     *
     * @return true if the Interceptor requiers the response from the proxied
     * backend before sending it to the client
     */
    default boolean requiresResponseContent() {
        return false;
    }
}
