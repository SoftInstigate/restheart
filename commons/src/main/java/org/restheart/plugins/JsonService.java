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

import io.undertow.server.HttpServerExchange;

import java.util.function.Consumer;
import java.util.function.Function;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;

/**
 * Specialized Service interface for handling JSON-based HTTP requests and responses.
 * <p>
 * JsonService is a convenience interface that extends the base {@link Service} interface
 * with pre-configured JSON request and response handling. It automatically provides the
 * correct initializers and factory methods for {@link JsonRequest} and {@link JsonResponse}
 * objects, eliminating the need for implementing these boilerplate methods in JSON-based
 * services.
 * </p>
 * <p>
 * This interface is ideal for services that:
 * <ul>
 *   <li><strong>Process JSON data</strong> - Handle JSON request bodies and generate JSON responses</li>
 *   <li><strong>Implement REST APIs</strong> - Provide RESTful web services with JSON content</li>
 *   <li><strong>Handle structured data</strong> - Work with complex data structures serialized as JSON</li>
 *   <li><strong>Integrate with modern web frameworks</strong> - Support AJAX and single-page applications</li>
 * </ul>
 * </p>
 * <p>
 * Example implementation:
 * <pre>
 * &#64;RegisterPlugin(
 *     name = "userService",
 *     description = "REST API for user management",
 *     defaultURI = "/api/users",
 *     secure = true
 * )
 * public class UserService implements JsonService {
 *     &#64;Inject("mongo-client")
 *     private MongoClient mongoClient;
 *     
 *     &#64;Override
 *     public void handle(JsonRequest request, JsonResponse response) throws Exception {
 *         switch (request.getMethod()) {
 *             case GET:
 *                 handleGetUsers(request, response);
 *                 break;
 *             case POST:
 *                 handleCreateUser(request, response);
 *                 break;
 *             case PUT:
 *                 handleUpdateUser(request, response);
 *                 break;
 *             case DELETE:
 *                 handleDeleteUser(request, response);
 *                 break;
 *             default:
 *                 response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
 *         }
 *     }
 *     
 *     private void handleGetUsers(JsonRequest request, JsonResponse response) {
 *         JsonArray users = getUsersFromDatabase();
 *         response.setContent(users);
 *         response.setStatusCode(HttpStatus.SC_OK);
 *     }
 *     
 *     private void handleCreateUser(JsonRequest request, JsonResponse response) {
 *         JsonObject userData = request.getContent().asJsonObject();
 *         JsonObject createdUser = createUserInDatabase(userData);
 *         response.setContent(createdUser);
 *         response.setStatusCode(HttpStatus.SC_CREATED);
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Content Type Handling:</strong><br>
 * JsonService automatically handles JSON content type negotiation:
 * <ul>
 *   <li>Accepts requests with Content-Type: application/json</li>
 *   <li>Parses JSON request bodies into JsonElement objects</li>
 *   <li>Sets response Content-Type to application/json</li>
 *   <li>Serializes response content as JSON</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Request/Response Objects:</strong><br>
 * The interface provides automatic initialization of:
 * <ul>
 *   <li>{@link JsonRequest} - Provides access to parsed JSON request content</li>
 *   <li>{@link JsonResponse} - Enables setting JSON response content and headers</li>
 * </ul>
 * These objects handle JSON parsing/serialization transparently and provide
 * convenient methods for working with JSON data structures.
 * </p>
 * <p>
 * <strong>Error Handling:</strong><br>
 * JsonService implementations should handle common JSON-related errors:
 * <ul>
 *   <li>Invalid JSON format in request bodies</li>
 *   <li>Missing required JSON fields</li>
 *   <li>Type conversion errors</li>
 *   <li>Content-Type mismatches</li>
 * </ul>
 * The JsonRequest and JsonResponse objects provide appropriate error responses
 * for these scenarios.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see Service
 * @see JsonRequest
 * @see JsonResponse
 * @see RegisterPlugin
 */
public interface JsonService extends Service<JsonRequest, JsonResponse> {
    /**
     * Returns the initializer function for JsonRequest objects.
     * <p>
     * This method provides the Consumer function that initializes JsonRequest
     * objects for incoming HTTP exchanges. The initializer sets up the request
     * object with JSON parsing capabilities and attaches it to the exchange
     * for later retrieval by the service implementation.
     * </p>
     * <p>
     * The JsonRequest initializer:
     * <ul>
     *   <li>Creates a new JsonRequest instance for the exchange</li>
     *   <li>Configures JSON content parsing</li>
     *   <li>Sets up content type validation for JSON requests</li>
     *   <li>Attaches the request object to the exchange context</li>
     * </ul>
     * </p>
     *
     * @return Consumer function that initializes JsonRequest objects
     */
    @Override
    default Consumer<HttpServerExchange> requestInitializer() {
        return e -> JsonRequest.init(e);
    }

    /**
     * Returns the initializer function for JsonResponse objects.
     * <p>
     * This method provides the Consumer function that initializes JsonResponse
     * objects for outgoing HTTP exchanges. The initializer sets up the response
     * object with JSON serialization capabilities and attaches it to the exchange
     * for later use by the service implementation.
     * </p>
     * <p>
     * The JsonResponse initializer:
     * <ul>
     *   <li>Creates a new JsonResponse instance for the exchange</li>
     *   <li>Configures JSON content serialization</li>
     *   <li>Sets default Content-Type header to application/json</li>
     *   <li>Attaches the response object to the exchange context</li>
     * </ul>
     * </p>
     *
     * @return Consumer function that initializes JsonResponse objects
     */
    @Override
    default Consumer<HttpServerExchange> responseInitializer() {
        return e -> JsonResponse.init(e);
    }

    /**
     * Returns the factory function for retrieving JsonRequest objects from exchanges.
     * <p>
     * This method provides the Function that retrieves the JsonRequest object
     * that was previously attached to an HTTP exchange during the initialization
     * phase. The service implementation uses this function to access the parsed
     * JSON request data.
     * </p>
     * <p>
     * The JsonRequest factory:
     * <ul>
     *   <li>Retrieves the JsonRequest instance from the exchange context</li>
     *   <li>Provides access to parsed JSON request content</li>
     *   <li>Enables type-safe access to request data and metadata</li>
     *   <li>Supports lazy parsing for optimal performance</li>
     * </ul>
     * </p>
     *
     * @return Function that retrieves JsonRequest objects from exchanges
     */
    @Override
    default Function<HttpServerExchange, JsonRequest> request() {
        return e -> JsonRequest.of(e);
    }

    /**
     * Returns the factory function for retrieving JsonResponse objects from exchanges.
     * <p>
     * This method provides the Function that retrieves the JsonResponse object
     * that was previously attached to an HTTP exchange during the initialization
     * phase. The service implementation uses this function to set JSON response
     * content and configure response headers.
     * </p>
     * <p>
     * The JsonResponse factory:
     * <ul>
     *   <li>Retrieves the JsonResponse instance from the exchange context</li>
     *   <li>Provides methods for setting JSON response content</li>
     *   <li>Enables response header configuration</li>
     *   <li>Supports automatic JSON serialization of response data</li>
     * </ul>
     * </p>
     *
     * @return Function that retrieves JsonResponse objects from exchanges
     */
    @Override
    default Function<HttpServerExchange, JsonResponse> response() {
        return e -> JsonResponse.of(e);
    }
}
