/*
 * uIAM - the IAM for microservices
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
package io.uiam.plugins.interceptors;

/**
 * An request interceptor can snoop and modify requests before proxying them.
 * Can be setup by an Initializer using
 * PluginsRegistry.getRequestInterceptors().add()
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public interface PluggableRequestInterceptor extends PluggableInterceptor {
    /**
     *
     * @return true if the Interceptor requires to access the request content
     */
    default boolean requiresContent() {
        return false;
    }
}
