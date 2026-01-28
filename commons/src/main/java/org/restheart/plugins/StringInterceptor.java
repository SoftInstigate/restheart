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

import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;

/**
 * Specialized Interceptor interface for services implementing StringService.
 * 
 * This interface provides a type-safe way to intercept string-based requests and responses
 * in the RESTHeart framework. It extends the generic Interceptor interface with specific
 * parameterization for StringRequest and StringResponse types.
 * 
 * Interceptors implementing this interface can process string content before it is
 * handled by the service or before the response is sent back to the client.
 * This is particularly useful for text processing, content validation, transformation,
 * logging, or applying business rules to string-based payloads.
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Interceptor
 * @see StringRequest
 * @see StringResponse
 * @see StringService
 */
public interface StringInterceptor extends Interceptor<StringRequest, StringResponse> {

}
