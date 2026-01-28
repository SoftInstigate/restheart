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

import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.exchange.ByteArrayProxyResponse;

/**
 * Specialized Interceptor interface for proxy requests and responses.
 * 
 * This interface provides a type-safe way to intercept requests and responses
 * that are being proxied to external services through RESTHeart. It extends
 * the generic Interceptor interface with specific parameterization for
 * ByteArrayProxyRequest and ByteArrayProxyResponse types.
 * 
 * Interceptors implementing this interface can process requests before they
 * are forwarded to the target service and responses before they are sent
 * back to the client. This is useful for adding headers, logging, authentication,
 * content transformation, or other cross-cutting concerns for proxied requests.
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Interceptor
 * @see ByteArrayProxyRequest
 * @see ByteArrayProxyResponse
 */
public interface ProxyInterceptor extends Interceptor<ByteArrayProxyRequest, ByteArrayProxyResponse> {

}
