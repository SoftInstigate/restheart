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
package org.restheart.exchange;

import java.io.IOException;

import org.restheart.utils.ChannelReader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * ServiceRequest implementation for handling GraphQL queries and operations.
 * <p>
 * This class provides specialized handling for GraphQL requests, supporting both
 * standard JSON format (application/json) and GraphQL-specific format (application/graphql).
 * It parses GraphQL queries, operation names, and variables from the request body and
 * provides convenient access methods for GraphQL-specific functionality.
 * </p>
 * <p>
 * The class supports the GraphQL HTTP specification, handling:
 * <ul>
 *   <li>GraphQL queries in both JSON and raw GraphQL format</li>
 *   <li>Operation names for selecting specific operations from multi-operation documents</li>
 *   <li>Variables for parameterized queries</li>
 *   <li>OPTIONS method for CORS preflight requests</li>
 * </ul>
 * </p>
 * <p>
 * Example JSON request format:
 * <pre>
 * {
 *   "query": "query GetUser($id: ID!) { user(id: $id) { name email } }",
 *   "variables": { "id": "123" },
 *   "operationName": "GetUser"
 * }
 * </pre>
 * </p>
 * <p>
 * Example raw GraphQL request (Content-Type: application/graphql):
 * <pre>
 * query GetUser($id: ID!) {
 *   user(id: $id) {
 *     name
 *     email
 *   }
 * }
 * </pre>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GraphQLRequest extends ServiceRequest<JsonElement> {

    /** Content type for raw GraphQL queries. */
    private static final String GRAPHQL_CONTENT_TYPE = "application/graphql";
    
    /** JSON field name for the GraphQL query string. */
    private static final String QUERY_FIELD = "query";
    
    /** JSON field name for the operation name. */
    private static final String OPERATION_NAME_FIELD = "operationName";
    
    /** JSON field name for query variables. */
    private static final String VARIABLES_FIELD = "variables";

    /** The URI of the GraphQL application endpoint. */
    private final String appUri;


    /**
     * Constructs a new GraphQLRequest wrapping the given HTTP exchange.
     * <p>
     * This constructor is private and should only be called by the factory method.
     * Use {@link #init(HttpServerExchange, String)} to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     * @param appUri the URI of the GraphQL application endpoint
     */
    private GraphQLRequest(HttpServerExchange exchange, String appUri) {
        super(exchange);
        this.appUri = appUri;
    }

    /**
     * Factory method to create and validate a GraphQLRequest.
     * <p>
     * This method creates a new GraphQLRequest instance and validates that the HTTP method
     * is either POST (for GraphQL operations) or OPTIONS (for CORS preflight requests).
     * GraphQL typically uses POST for all operations, including queries.
     * </p>
     *
     * @param exchange the HTTP server exchange containing the GraphQL request
     * @param appUri the URI of the GraphQL application endpoint
     * @return a new GraphQLRequest instance
     * @throws IOException if there is an error reading the request
     * @throws JsonSyntaxException if the JSON content is malformed
     * @throws BadRequestException if the HTTP method is not allowed (returns 405 Method Not Allowed)
     */
    public static GraphQLRequest init(HttpServerExchange exchange, String appUri) throws IOException, JsonSyntaxException, BadRequestException {
        var method = exchange.getRequestMethod();

        if (!method.equalToString("OPTIONS") && !method.equalToString("POST")) {
            throw new BadRequestException("Method not allowed", 405);
        }

        return new GraphQLRequest(exchange, appUri);
    }

    /**
     * Factory method to retrieve or create a GraphQLRequest from an existing exchange.
     * <p>
     * This method retrieves an existing GraphQLRequest instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the GraphQLRequest associated with the exchange
     */
    public static GraphQLRequest of(HttpServerExchange exchange) {
        return of(exchange, GraphQLRequest.class);
    }

    /**
     * Parses the GraphQL content from the request body.
     * <p>
     * This method handles both GraphQL content types:
     * <ul>
     *   <li><strong>application/graphql</strong>: Raw GraphQL query string</li>
     *   <li><strong>application/json</strong>: JSON object with query, variables, and operationName</li>
     * </ul>
     * </p>
     * <p>
     * For raw GraphQL content, the query string is wrapped in a JSON object with a "query" field.
     * For JSON content, the entire JSON object is parsed and validated.
     * OPTIONS requests are allowed without content validation for CORS support.
     * </p>
     *
     * @return the parsed GraphQL content as a JsonElement, or null for OPTIONS requests
     * @throws IOException if there is an error reading the request body
     * @throws BadRequestException if the content type is not supported or the content is malformed
     */
    @Override
    public JsonElement parseContent() throws IOException, BadRequestException {
        var exchange = getExchange();
        var rawBody = ChannelReader.readString(exchange);

        if (isContentTypeGraphQL(exchange)) {
            return _parseContentGraphQL(rawBody);
        } else if (isContentTypeJson(exchange)) {
            return _parseContentJson(rawBody);
        } else if (!exchange.getRequestMethod().equalToString("OPTIONS")) {
            var error = "Bad request: " + Headers.CONTENT_TYPE + " must be either " + GRAPHQL_CONTENT_TYPE + " or application/json";
            throw new BadRequestException(error);
        }

        return null;
    }

    /**
     * Parses JSON content containing GraphQL query information.
     * <p>
     * Expects a JSON object that may contain "query", "variables", and "operationName" fields
     * according to the GraphQL over HTTP specification.
     * </p>
     *
     * @param rawBody the raw JSON string from the request body
     * @return the parsed JSON content as a JsonElement
     * @throws JsonSyntaxException if the JSON is malformed
     * @throws BadRequestException if the request is invalid
     */
    private JsonElement _parseContentJson(String rawBody) throws JsonSyntaxException, BadRequestException {
        return JsonParser.parseString(rawBody);
    }

    /**
     * Parses raw GraphQL content and wraps it in a JSON structure.
     * <p>
     * Converts a raw GraphQL query string into a JSON object with a "query" field,
     * making it compatible with the standard JSON-based GraphQL processing.
     * </p>
     *
     * @param rawBody the raw GraphQL query string from the request body
     * @return a JsonObject containing the query wrapped in the "query" field
     */
    private JsonElement _parseContentGraphQL(String rawBody) {
        var jsonObject = new JsonObject();
        jsonObject.addProperty(QUERY_FIELD, rawBody);

        return jsonObject;
    }

    /**
     * Extracts the GraphQL query string from the request content.
     * <p>
     * Returns the "query" field from the parsed JSON content, which contains
     * the GraphQL query, mutation, or subscription to be executed.
     * </p>
     *
     * @return the GraphQL query string, or null if not present or invalid
     */
    public String getQuery() {
        var _content = this.getContent();
        if (_content != null
            && _content.isJsonObject()
            && _content.getAsJsonObject().has(QUERY_FIELD)
            && _content.getAsJsonObject().get(QUERY_FIELD).isJsonPrimitive()
            && _content.getAsJsonObject().getAsJsonPrimitive(QUERY_FIELD).isString()) {
            return _content.getAsJsonObject().getAsJsonPrimitive(QUERY_FIELD).getAsString();
        } else {
            return null;
        }
    }

    /**
     * Extracts the operation name from the request content.
     * <p>
     * The operation name is used to select a specific operation when the query
     * document contains multiple named operations. This is optional and may be
     * null for single-operation documents or anonymous operations.
     * </p>
     *
     * @return the operation name string, or null if not present or invalid
     */
    public String getOperationName() {
        var _content = this.getContent();
        if (_content != null
            && _content.isJsonObject()
            && _content.getAsJsonObject().has(OPERATION_NAME_FIELD)
            && _content.getAsJsonObject().get(OPERATION_NAME_FIELD).isJsonPrimitive()
            && _content.getAsJsonObject().getAsJsonPrimitive(OPERATION_NAME_FIELD).isString()) {
            return _content.getAsJsonObject().getAsJsonPrimitive(OPERATION_NAME_FIELD).getAsString();
        } else {
            return null;
        }
    }

    /**
     * Extracts the variables object from the request content.
     * <p>
     * Variables provide values for parameterized GraphQL queries. They should
     * be provided as a JSON object where keys correspond to variable names
     * defined in the GraphQL query.
     * </p>
     * <p>
     * Example variables object:
     * <pre>
     * {
     *   "userId": "123",
     *   "includeProfile": true,
     *   "limit": 10
     * }
     * </pre>
     * </p>
     *
     * @return the variables as a JsonObject, or null if not present or invalid
     */
    public JsonObject getVariables() {
        var _content = this.getContent();
        if (_content != null
            && _content.isJsonObject()
            && _content.getAsJsonObject().has(VARIABLES_FIELD)
            && _content.getAsJsonObject().get(VARIABLES_FIELD).isJsonObject()) {
            return _content.getAsJsonObject().getAsJsonObject(VARIABLES_FIELD);
        } else {
            return null;
        }
    }

    /**
     * Returns the URI of the GraphQL application endpoint.
     * <p>
     * This URI identifies the specific GraphQL application or schema that should
     * handle the request. It's typically set during request initialization based
     * on the request path or configuration.
     * </p>
     *
     * @return the GraphQL application URI
     */
    public String getGraphQLAppURI() {
       return this.appUri;
    }

    /**
     * Checks if the request contains variables.
     * <p>
     * This method provides a quick way to determine if the GraphQL request
     * includes variable definitions without retrieving the actual variables object.
     * </p>
     *
     * @return true if variables are present and valid, false otherwise
     */
    public boolean hasVariables() {
        var _content = this.getContent();

        return _content != null
            &&  _content.isJsonObject()
            && _content.getAsJsonObject().has(VARIABLES_FIELD)
            && _content.getAsJsonObject().get(VARIABLES_FIELD).isJsonObject();
    }

    /**
     * Checks if the request has GraphQL content type.
     * <p>
     * Validates that the Content-Type header is "application/graphql" or starts with
     * "application/graphql" (allowing for additional parameters like charset).
     * </p>
     *
     * @param exchange the HTTP server exchange to check
     * @return true if the content type is GraphQL, false otherwise
     */
    private static boolean isContentTypeGraphQL(HttpServerExchange exchange){
        var contentType = getContentType(exchange);
        return GRAPHQL_CONTENT_TYPE.equals(contentType) || (contentType != null && contentType.startsWith(GRAPHQL_CONTENT_TYPE));
    }
}
