/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.undertow.server.HttpServerExchange;
import org.restheart.utils.ChannelReader;

import java.io.IOException;
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

    public static GraphQLRequest init(HttpServerExchange exchange, String appUri) {
        var ret = new GraphQLRequest(exchange, appUri);

        try {
            if (isContentTypeGraphQL(exchange)){
                ret.injectContentGraphQL();
            } else if (isContentTypeJson(exchange)){
                ret.injectContentJson();
            } else if (!exchange.getRequestMethod().equalToString("OPTIONS")) {
                ret.setInError(true);
            }
        } catch (IOException ioe){
            ret.setInError(true);
        }

        return ret;
    }

    public static GraphQLRequest of(HttpServerExchange exchange) {
        return of(exchange, GraphQLRequest.class);
    }

    public void injectContentJson() throws IOException {
        var body = ChannelReader.readString(wrapped);
        var json = JsonParser.parseString(body);

        setContent(json);
    }

    public void injectContentGraphQL() throws IOException {
        var body = ChannelReader.readString(wrapped);
        var jsonObject = new JsonObject();
        jsonObject.addProperty(QUERY_FIELD,body);

        setContent(jsonObject);
    }

    public String getQuery(){
        if (this.getContent().isJsonObject() && this.getContent().getAsJsonObject().has(QUERY_FIELD)){
            return this.getContent().getAsJsonObject().get(QUERY_FIELD).getAsString();
        } else {
            return null;
        }
    }

    public String getOperationName(){
        if (this.getContent().isJsonObject() &&  this.getContent().getAsJsonObject().has(OPERATION_NAME_FIELD)) {
            return this.getContent().getAsJsonObject().get(OPERATION_NAME_FIELD).getAsString();
        } else {
            return null;
        }
    }

    public JsonObject getVariables(){
        if (this.getContent().isJsonObject() && this.getContent().getAsJsonObject().has(VARIABLES_FIELD)) {
            return this.getContent().getAsJsonObject().get(VARIABLES_FIELD).getAsJsonObject();
        } else {
            return null;
        }
    }

    public String getGraphQLAppURI() {
       return this.appUri;
    }

    public boolean hasVariables(){
        return this.getContent().isJsonObject() && this.getContent().getAsJsonObject().has(VARIABLES_FIELD);
    }

    private static boolean isContentTypeGraphQL(HttpServerExchange exchange){
        var contentType = getContentType(exchange);
        return GRAPHQL_CONTENT_TYPE.equals(contentType) || (contentType != null && contentType.startsWith(GRAPHQL_CONTENT_TYPE));
    }
}
