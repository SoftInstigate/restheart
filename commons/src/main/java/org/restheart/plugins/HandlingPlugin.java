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

import org.restheart.exchange.Request;
import org.restheart.exchange.Response;

/**
 * Parent interface for RESTHeart plugins that handle HTTP requests and generate responses.
 * <p>
 * HandlingPlugin serves as the base interface for plugins that actively process HTTP
 * requests and produce responses. It combines the core Plugin functionality with
 * ExchangeTypeResolver capabilities to provide a foundation for request-handling
 * plugin implementations.
 * </p>
 * <p>
 * This interface is implemented by:
 * <ul>
 *   <li><strong>Services</strong> - Plugins that provide custom web service endpoints</li>
 *   <li><strong>Proxies</strong> - Plugins that proxy requests to external services</li>
 * </ul>
 * </p>
 * <p>
 * HandlingPlugin provides the type-safe foundation for request processing by:
 * <ul>
 *   <li>Defining generic request and response types that the plugin can handle</li>
 *   <li>Providing runtime type resolution through ExchangeTypeResolver</li>
 *   <li>Establishing the contract for request/response processing</li>
 *   <li>Enabling type-safe plugin registration and invocation</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Type Parameters:</strong><br>
 * The generic parameters R and S define the specific request and response types
 * that the plugin can handle. This provides compile-time type safety and enables
 * the RESTHeart framework to perform runtime type checking and proper request
 * routing.
 * </p>
 * <p>
 * Example implementations:
 * <pre>
 * // JSON service handling JSON requests and responses
 * public class JsonService implements Service&lt;JsonRequest, JsonResponse&gt; {
 *     // Implementation handles JSON-specific request/response processing
 * }
 * 
 * // Binary service handling byte array data
 * public class BinaryService implements Service&lt;ByteArrayRequest, ByteArrayResponse&gt; {
 *     // Implementation handles binary data processing
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Framework Integration:</strong><br>
 * The RESTHeart framework uses the type information provided by HandlingPlugin to:
 * <ul>
 *   <li>Route requests to appropriate plugins based on content type</li>
 *   <li>Initialize correct request/response objects for each plugin</li>
 *   <li>Apply compatible interceptors based on type matching</li>
 *   <li>Perform type validation during plugin registration</li>
 * </ul>
 * </p>
 *
 * @param <R> the request type that this plugin can handle, must extend Request
 * @param <S> the response type that this plugin can handle, must extend Response
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Service
 * @see Plugin
 * @see ExchangeTypeResolver
 * @see org.restheart.exchange.Request
 * @see org.restheart.exchange.Response
 */
public interface HandlingPlugin<R extends Request<?>, S extends Response<?>>
        extends Plugin, ExchangeTypeResolver<R, S> {
}
