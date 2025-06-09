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
 * Base interface for plugins that handle HTTP requests and produce responses.
 * 
 * <p>HandlingPlugin is the parent interface for all request-handling plugins in RESTHeart,
 * including {@link Service} and Proxy plugins. It combines the core {@link Plugin} interface
 * with {@link ExchangeTypeResolver} to provide type-safe request and response handling.</p>
 * 
 * <h2>Purpose</h2>
 * <p>This interface establishes the foundation for plugins that:</p>
 * <ul>
 *   <li>Process incoming HTTP requests</li>
 *   <li>Generate HTTP responses</li>
 *   <li>Participate in the request handling pipeline</li>
 *   <li>Declare their specific request/response types</li>
 * </ul>
 * 
 * <h2>Type Parameters</h2>
 * <p>HandlingPlugin uses generic type parameters to ensure type safety:</p>
 * <ul>
 *   <li><strong>R</strong> - The request type (e.g., JsonRequest, BsonRequest, StringRequest)</li>
 *   <li><strong>S</strong> - The response type (e.g., JsonResponse, BsonResponse, StringResponse)</li>
 * </ul>
 * 
 * <h2>Implementation Hierarchy</h2>
 * <p>Plugins typically don't implement HandlingPlugin directly. Instead, they implement
 * one of its sub-interfaces:</p>
 * <ul>
 *   <li>{@link Service} - For creating REST API endpoints</li>
 *   <li>Proxy - For proxying requests to other services</li>
 * </ul>
 * 
 * <h2>Type Resolution</h2>
 * <p>Through {@link ExchangeTypeResolver}, implementing classes automatically provide
 * runtime type information about their request and response types. This enables the
 * framework to:</p>
 * <ul>
 *   <li>Route requests to appropriate handlers</li>
 *   <li>Initialize proper request/response objects</li>
 *   <li>Perform type checking and validation</li>
 * </ul>
 * 
 * <h2>Example</h2>
 * <pre>{@code
 * // Service implementation (indirect use of HandlingPlugin)
 * @RegisterPlugin(name = "users", description = "User management service")
 * public class UserService implements JsonService {
 *     @Override
 *     public void handle(JsonRequest request, JsonResponse response) {
 *         // HandlingPlugin provides the type foundation
 *         // JsonService extends HandlingPlugin<JsonRequest, JsonResponse>
 *     }
 * }
 * 
 * // Custom service with specific types
 * public abstract class CustomService 
 *         implements HandlingPlugin<CustomRequest, CustomResponse> {
 *     // Direct implementation for specialized handling
 * }
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>Implementations must be thread-safe as multiple threads may invoke handling
 * methods concurrently. The framework creates a single instance of each plugin
 * that handles all requests.</p>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @param <R> the request type, must extend {@link Request}
 * @param <S> the response type, must extend {@link Response}
 * @see Plugin
 * @see ExchangeTypeResolver
 * @see Service
 * @see Request
 * @see Response
 */
public interface HandlingPlugin<R extends Request<?>, S extends Response<?>>
        extends Plugin, ExchangeTypeResolver<R, S> {
}
