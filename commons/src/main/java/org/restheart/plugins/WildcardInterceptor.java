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

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;

/**
 * A special interceptor that intercepts requests handled by any Service.
 * 
 * This interface provides a way to create interceptors that apply to all services
 * in the RESTHeart framework, regardless of their specific request/response types.
 * It extends the generic Interceptor interface with parameterization for the base
 * ServiceRequest and ServiceResponse types.
 * 
 * Wildcard interceptors are useful for implementing cross-cutting concerns that
 * should apply to all services, such as:
 * - Global logging and monitoring
 * - Universal security checks
 * - Request/response transformation that applies to all services
 * - Performance metrics collection
 * - Global error handling
 * 
 * Unlike specific interceptors (like BsonInterceptor or JsonInterceptor) that
 * only apply to services of matching types, wildcard interceptors are invoked
 * for requests to any service in the system.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Interceptor
 * @see ServiceRequest
 * @see ServiceResponse
 * @see Service
 */
public interface WildcardInterceptor extends Interceptor<ServiceRequest<?>, ServiceResponse<?>> {

}
