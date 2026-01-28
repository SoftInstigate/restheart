/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;

/**
 * Specialized Interceptor interface for MongoService.
 * 
 * This interface provides a type-safe way to intercept MongoDB-related requests and responses
 * in the RESTHeart framework. It extends the generic Interceptor interface with specific
 * parameterization for MongoRequest and MongoResponse types.
 * 
 * Interceptors implementing this interface can process MongoDB operations before they are
 * handled by the MongoService or before the response is sent back to the client. This
 * enables custom validation, transformation, logging, security checks, or other
 * cross-cutting concerns specific to MongoDB operations.
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Interceptor
 * @see MongoRequest
 * @see MongoResponse
 */
public interface MongoInterceptor extends Interceptor<MongoRequest, MongoResponse> {

}
