/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;

/**
 * Interceptor interface for handling JSON data exchanges.
 * 
 * <p>JsonInterceptor is a specialized interceptor that processes requests and responses
 * containing JSON data. It extends the generic {@link Interceptor} interface with
 * {@link JsonRequest} and {@link JsonResponse} type parameters.</p>
 * 
 * <p>This interceptor is ideal for:</p>
 * <ul>
 *   <li>Validating JSON schema and structure</li>
 *   <li>Transforming JSON data (adding, removing, or modifying fields)</li>
 *   <li>Implementing business logic for JSON APIs</li>
 *   <li>Adding security checks for JSON content</li>
 *   <li>Logging and monitoring JSON requests/responses</li>
 *   <li>Implementing JSON-specific cross-cutting concerns</li>
 * </ul>
 * 
 * <h2>Example Implementation</h2>
 * <pre>{@code
 * @RegisterPlugin(
 *     name = "json-validator",
 *     description = "Validates JSON against schema",
 *     interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH
 * )
 * public class JsonSchemaValidator implements JsonInterceptor {
 *     @Override
 *     public void handle(JsonRequest request, JsonResponse response) {
 *         JsonElement content = request.getContent();
 *         
 *         if (content == null || !content.isJsonObject()) {
 *             throw new InterceptorException("Invalid JSON", 400);
 *         }
 *         
 *         JsonObject json = content.getAsJsonObject();
 *         
 *         // Validate required fields
 *         if (!json.has("id") || !json.has("name")) {
 *             response.setStatusCode(400);
 *             response.setContent(JsonParser.parseString(
 *                 "{\"error\": \"Missing required fields\"}"
 *             ));
 *         }
 *     }
 *     
 *     @Override
 *     public boolean resolve(JsonRequest request, JsonResponse response) {
 *         // Apply only to POST requests to /api/users
 *         return request.isPost() && request.getPath().equals("/api/users");
 *     }
 * }
 * }</pre>
 * 
 * <h2>Working with JSON</h2>
 * <p>The JsonInterceptor works with Google's Gson library for JSON manipulation:</p>
 * <ul>
 *   <li>Access request JSON via {@code request.getContent()}</li>
 *   <li>Modify response JSON via {@code response.setContent(JsonElement)}</li>
 *   <li>Use {@link com.google.gson.JsonParser} for parsing strings</li>
 *   <li>Use {@link com.google.gson.JsonObject} and {@link com.google.gson.JsonArray} for manipulation</li>
 * </ul>
 * 
 * @see Interceptor
 * @see JsonRequest
 * @see JsonResponse
 * @see InterceptPoint
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface JsonInterceptor extends Interceptor<JsonRequest, JsonResponse> {
}
