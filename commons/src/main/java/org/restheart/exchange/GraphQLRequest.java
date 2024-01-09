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
package org.restheart.exchange;

import java.io.IOException;

import org.restheart.utils.ChannelReader;
import org.restheart.utils.HttpStatus;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

public class GraphQLRequest extends ServiceRequest<JsonElement> {

    private static final String GRAPHQL_CONTENT_TYPE = "application/graphql";
    private static final String QUERY_FIELD = "query";
    private static final String OPERATION_NAME_FIELD = "operationName";
    private static final String VARIABLES_FIELD = "variables";

    private final String appUri;


    private GraphQLRequest(HttpServerExchange exchange, String appUri) {
        super(exchange);
        this.appUri = appUri;
    }

    public static GraphQLRequest init(HttpServerExchange exchange, String appUri) throws IOException, JsonSyntaxException, BadRequestException {
        var ret = new GraphQLRequest(exchange, appUri);

        var method = exchange.getRequestMethod();

        if (!method.equalToString("OPTIONS") && !method.equalToString("POST")) {
            throw new BadRequestException("Method not allowed", 405);
        }

        if (isContentTypeGraphQL(exchange)) {
            ret.injectContentGraphQL();
        } else if (isContentTypeJson(exchange)) {
            ret.injectContentJson();
        } else if (!method.equalToString("OPTIONS")) {
            throw new BadRequestException("Bad request: " + Headers.CONTENT_TYPE + " must be either " + GRAPHQL_CONTENT_TYPE + " or application/json", HttpStatus.SC_BAD_REQUEST);
        }

        return ret;
    }

    public static GraphQLRequest of(HttpServerExchange exchange) {
        return of(exchange, GraphQLRequest.class);
    }

    private void injectContentJson() throws IOException, JsonSyntaxException, BadRequestException {
        var body = ChannelReader.readString(wrapped);
        var json = JsonParser.parseString(body);

        // json must contain the query field and is must be a string
        if (json.isJsonObject() && json.getAsJsonObject().has(QUERY_FIELD)) {
            if (json.getAsJsonObject().get(QUERY_FIELD).isJsonPrimitive()) {
                if (!json.getAsJsonObject().get(QUERY_FIELD).getAsJsonPrimitive().isString()) {
                    throw new BadRequestException("query field must be a string", HttpStatus.SC_BAD_REQUEST);
                }
            } else {
                throw new BadRequestException("query field must be a string", HttpStatus.SC_BAD_REQUEST);
            }
        } else {
            throw new BadRequestException("missing query field", HttpStatus.SC_BAD_REQUEST);
        }

        setContent(json);
    }

    private void injectContentGraphQL() throws IOException {
        var body = ChannelReader.readString(wrapped);
        var jsonObject = new JsonObject();
        jsonObject.addProperty(QUERY_FIELD, body);

        setContent(jsonObject);
    }

    public String getQuery() {
        if (this.getContent().isJsonObject()
            && this.getContent().getAsJsonObject().has(QUERY_FIELD)
            && this.getContent().getAsJsonObject().get(QUERY_FIELD).isJsonPrimitive()
            && this.getContent().getAsJsonObject().getAsJsonPrimitive(QUERY_FIELD).isString()) {
            return this.getContent().getAsJsonObject().getAsJsonPrimitive(QUERY_FIELD).getAsString();
        } else {
            return null;
        }
    }

    public String getOperationName() {
        if (this.getContent().isJsonObject()
            && this.getContent().getAsJsonObject().has(OPERATION_NAME_FIELD)
            && this.getContent().getAsJsonObject().get(OPERATION_NAME_FIELD).isJsonPrimitive()
            && this.getContent().getAsJsonObject().getAsJsonPrimitive(OPERATION_NAME_FIELD).isString()) {
            return this.getContent().getAsJsonObject().getAsJsonPrimitive(OPERATION_NAME_FIELD).getAsString();
        } else {
            return null;
        }
    }

    public JsonObject getVariables() {
        if (this.getContent().isJsonObject()
            && this.getContent().getAsJsonObject().has(VARIABLES_FIELD)
            && this.getContent().getAsJsonObject().get(VARIABLES_FIELD).isJsonObject()) {
            return this.getContent().getAsJsonObject().getAsJsonObject(VARIABLES_FIELD);
        } else {
            return null;
        }
    }

    public String getGraphQLAppURI() {
       return this.appUri;
    }

    public boolean hasVariables() {
        return this.getContent().isJsonObject()
            && this.getContent().getAsJsonObject().has(VARIABLES_FIELD)
            && this.getContent().getAsJsonObject().get(VARIABLES_FIELD).isJsonObject();
    }

    private static boolean isContentTypeGraphQL(HttpServerExchange exchange){
        var contentType = getContentType(exchange);
        return GRAPHQL_CONTENT_TYPE.equals(contentType) || (contentType != null && contentType.startsWith(GRAPHQL_CONTENT_TYPE));
    }
}
