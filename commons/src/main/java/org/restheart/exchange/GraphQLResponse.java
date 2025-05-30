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
package org.restheart.exchange;

import io.undertow.server.HttpServerExchange;

/**
 * ServiceResponse implementation for handling GraphQL query results.
 * <p>
 * This class provides specialized response handling for GraphQL operations,
 * extending MongoResponse to leverage MongoDB-specific functionality while
 * setting the appropriate content type for GraphQL responses. It automatically
 * configures the response with the standard GraphQL response content type.
 * </p>
 * <p>
 * GraphQLResponse follows the GraphQL over HTTP specification for response formatting,
 * ensuring compatibility with GraphQL clients and tools. The response content type
 * is set to "application/graphql-response+json" to indicate that the response contains
 * GraphQL result data in JSON format.
 * </p>
 * <p>
 * The class inherits all MongoDB-specific functionality from MongoResponse while
 * providing GraphQL-specific response formatting. This includes support for:
 * <ul>
 *   <li>GraphQL result data formatting</li>
 *   <li>Error handling according to GraphQL specification</li>
 *   <li>Proper content type headers for GraphQL responses</li>
 *   <li>Integration with RESTHeart's MongoDB backend</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GraphQLResponse extends MongoResponse {
    /** The standard content type for GraphQL responses according to the GraphQL over HTTP specification. */
    public static final String GRAPHQL_RESPONSE_CONTENT_TYPE = "application/graphql-response+json";

    /**
     * Constructs a new GraphQLResponse wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. It automatically sets the response content type to the
     * standard GraphQL response content type. Use {@link #init(HttpServerExchange)}
     * or {@link #of(HttpServerExchange)} to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected GraphQLResponse(HttpServerExchange exchange) {
        super(exchange);
        // set response content type as application/graphql-response+json
        setContentType(GRAPHQL_RESPONSE_CONTENT_TYPE);
    }

    /**
     * Factory method to create a new GraphQLResponse instance.
     * <p>
     * This method creates a fresh GraphQLResponse instance for the given exchange
     * with the content type automatically set to the GraphQL response format.
     * The response will be configured to handle GraphQL result data efficiently.
     * </p>
     *
     * @param exchange the HTTP server exchange for the GraphQL response
     * @return a new GraphQLResponse instance
     */
    public static GraphQLResponse init(HttpServerExchange exchange) {
        return new GraphQLResponse(exchange);
    }

    /**
     * Factory method to retrieve or create a GraphQLResponse from an existing exchange.
     * <p>
     * This method retrieves an existing GraphQLResponse instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * This allows for efficient reuse of response objects within the same request
     * processing pipeline.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the GraphQLResponse associated with the exchange
     */
    public static GraphQLResponse of(HttpServerExchange exchange) {
        return GraphQLResponse.of(exchange, GraphQLResponse.class);
    }
}
