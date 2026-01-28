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

import com.google.common.reflect.TypeToken;
import java.lang.reflect.Type;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;

/**
 * Interface for resolving request and response types at runtime in RESTHeart plugins.
 * <p>
 * ExchangeTypeResolver provides type information about the generic request and response
 * types used by plugins, particularly interceptors and services. This interface uses
 * reflection to determine the actual concrete types of the generic parameters R and S
 * at runtime, enabling the RESTHeart framework to perform type checking and proper
 * request/response handling.
 * </p>
 * <p>
 * This interface is primarily used by:
 * <ul>
 *   <li><strong>Interceptors</strong> - To determine which request/response types they can handle</li>
 *   <li><strong>Framework Components</strong> - For type safety and compatibility checking</li>
 *   <li><strong>Plugin Registry</strong> - To match plugins with appropriate exchange types</li>
 * </ul>
 * </p>
 * <p>
 * The type resolution is essential for RESTHeart's plugin system because it allows:
 * <ul>
 *   <li>Type-safe plugin registration and execution</li>
 *   <li>Automatic matching of interceptors to services based on types</li>
 *   <li>Runtime validation of plugin compatibility</li>
 *   <li>Proper request and response object initialization</li>
 * </ul>
 * </p>
 * <p>
 * Example usage in an interceptor:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "jsonInterceptor",
 *     description = "Interceptor for JSON requests/responses",
 *     interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH
 * )
 * public class JsonInterceptor implements Interceptor&lt;JsonRequest, JsonResponse&gt; {
 *     // The ExchangeTypeResolver methods automatically provide type information
 *     // for JsonRequest and JsonResponse at runtime
 *     
 *     &#64;Override
 *     public void handle(JsonRequest request, JsonResponse response) throws Exception {
 *         // Handle JSON-specific processing
 *     }
 *     
 *     &#64;Override
 *     public boolean resolve(JsonRequest request, JsonResponse response) {
 *         // Determine if this interceptor should handle the request
 *         return request.isContentTypeJson();
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Type Resolution Process:</strong>
 * <ol>
 *   <li>Plugin class is analyzed at registration time</li>
 *   <li>Generic type parameters are extracted using reflection</li>
 *   <li>Type information is stored for runtime compatibility checking</li>
 *   <li>Framework uses type info to determine plugin applicability</li>
 * </ol>
 * </p>
 * <p>
 * <strong>Implementation Note:</strong><br>
 * Classes implementing this interface typically don't need to override the default
 * methods as they use reflection with TypeToken to automatically determine the
 * correct types. The default implementations handle the complexity of generic
 * type resolution automatically.
 * </p>
 *
 * @param <R> the request type that this plugin can handle, must extend Request
 * @param <S> the response type that this plugin can handle, must extend Response
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Interceptor
 * @see org.restheart.exchange.Request
 * @see org.restheart.exchange.Response
 * @see com.google.common.reflect.TypeToken
 */
public interface ExchangeTypeResolver<R extends Request<?>, S extends Response<?>> {
    /**
     * Returns the parameterized Type of the request handled by this plugin.
     * <p>
     * This method uses reflection via Guava's TypeToken to determine the actual
     * concrete type of the generic parameter R at runtime. The returned Type
     * includes full generic information, enabling the RESTHeart framework to
     * perform accurate type checking and compatibility verification.
     * </p>
     * <p>
     * The request type information is used by the framework to:
     * <ul>
     *   <li>Determine which interceptors can handle specific service requests</li>
     *   <li>Validate plugin compatibility during registration</li>
     *   <li>Initialize appropriate request objects for the plugin</li>
     *   <li>Perform type-safe plugin invocation</li>
     * </ul>
     * </p>
     * <p>
     * For example, if a plugin is parameterized as {@code Interceptor<JsonRequest, JsonResponse>},
     * this method will return the Type object representing {@code JsonRequest} with all
     * its generic information preserved.
     * </p>
     * <p>
     * <strong>Implementation Note:</strong><br>
     * This default implementation uses TypeToken reflection and should not need to
     * be overridden in most cases. The reflection mechanism automatically handles
     * complex generic types and inheritance hierarchies.
     * </p>
     *
     * @return the parameterized Type of the generic request parameter R
     */
    default Type requestType() {
        return new TypeToken<R>(getClass()) {
			private static final long serialVersionUID = 8363463867743712134L;
        }.getType();
    }

    /**
     * Returns the parameterized Type of the response handled by this plugin.
     * <p>
     * This method uses reflection via Guava's TypeToken to determine the actual
     * concrete type of the generic parameter S at runtime. The returned Type
     * includes full generic information, enabling the RESTHeart framework to
     * perform accurate type checking and compatibility verification.
     * </p>
     * <p>
     * The response type information is used by the framework to:
     * <ul>
     *   <li>Determine which interceptors can handle specific service responses</li>
     *   <li>Validate plugin compatibility during registration</li>
     *   <li>Initialize appropriate response objects for the plugin</li>
     *   <li>Perform type-safe plugin invocation</li>
     * </ul>
     * </p>
     * <p>
     * For example, if a plugin is parameterized as {@code Interceptor<JsonRequest, JsonResponse>},
     * this method will return the Type object representing {@code JsonResponse} with all
     * its generic information preserved.
     * </p>
     * <p>
     * <strong>Type Matching:</strong><br>
     * The framework uses this type information to match interceptors with services.
     * An interceptor can only be applied to a service if their request and response
     * types are compatible (either exact matches or compatible inheritance relationships).
     * </p>
     * <p>
     * <strong>Implementation Note:</strong><br>
     * This default implementation uses TypeToken reflection and should not need to
     * be overridden in most cases. The reflection mechanism automatically handles
     * complex generic types and inheritance hierarchies.
     * </p>
     *
     * @return the parameterized Type of the generic response parameter S
     */
    default Type responseType() {
        return new TypeToken<S>(getClass()) {
            private static final long serialVersionUID = -2478049152751095135L;
        }.getType();
    }
}
