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

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;

/**
 * Specialized Interceptor interface for services implementing ByteArrayService.
 * 
 * This interface provides a type-safe way to intercept byte array-based requests and responses
 * in the RESTHeart framework. It extends the generic Interceptor interface with specific
 * parameterization for ByteArrayRequest and ByteArrayResponse types.
 * 
 * Interceptors implementing this interface can process raw byte array data before it is
 * handled by the service or before the response is sent back to the client. This is
 * particularly useful for binary data processing, file uploads, and other scenarios
 * where raw byte manipulation is required.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Interceptor
 * @see ByteArrayRequest
 * @see ByteArrayResponse
 * @see ByteArrayService
 */
public interface ByteArrayInterceptor extends Interceptor<ByteArrayRequest, ByteArrayResponse> {

}
