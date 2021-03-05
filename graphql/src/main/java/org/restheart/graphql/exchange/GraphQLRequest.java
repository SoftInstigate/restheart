/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2021 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.graphql.exchange;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.undertow.server.HttpServerExchange;
import org.restheart.exchange.ServiceRequest;
import org.restheart.graphql.models.GraphQLApp;
import org.restheart.utils.ChannelReader;

import java.io.IOException;
public class GraphQLRequest extends ServiceRequest<JsonElement> {

    private static final String GRAPHQL_CONTENT_TYPE = "application/graphql";
    private static final String QUERY_FIELD = "query";
    private static final String OPERATION_NAME_FIELD = "operationName";
    private static final String VARIABLES_FIELD = "variables";

    private final String appUri;
    private final GraphQLApp appDefinition;


    private GraphQLRequest(HttpServerExchange exchange, String appUri, GraphQLApp appDefinition) {
        super(exchange);
        this.appUri = appUri;
        this.appDefinition = appDefinition;
    }

    public static GraphQLRequest init(HttpServerExchange exchange, String appUri, GraphQLApp appDefinition) {
        var ret = new GraphQLRequest(exchange, appUri, appDefinition);

        try{

            if (isContentTypeGraphQL(exchange)){
                ret.injectContentGraphQL();
            }
            else if (isContentTypeJson(exchange)){
                ret.injectContentJson();
            }
            else ret.setInError(true);

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
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(QUERY_FIELD,body);

        setContent(jsonObject);

    }

    public String getQuery(){
        if (this.getContent().getAsJsonObject().has(QUERY_FIELD)){
            return this.getContent().getAsJsonObject().get(QUERY_FIELD).getAsString();
        }
        else return null;
    }

    public String getOperationName(){
        if (this.getContent().getAsJsonObject().has(OPERATION_NAME_FIELD)) {
            return this.getContent().getAsJsonObject().get(OPERATION_NAME_FIELD).getAsString();
        }
        else return null;
    }

    public JsonObject getVariables(){
        if (this.getContent().getAsJsonObject().has(VARIABLES_FIELD)) {
            return this.getContent().getAsJsonObject().get(VARIABLES_FIELD).getAsJsonObject();
        }
        else return null;
    }

    public String getGraphQLAppURI(){
       return this.appUri;
    }

    public GraphQLApp getAppDefinition(){
        return this.appDefinition;
    }

    public boolean hasVariables(){
        return this.getContent().getAsJsonObject().has(VARIABLES_FIELD);
    }

    private static boolean isContentTypeGraphQL(HttpServerExchange exchange){

        String contentType = getContentType(exchange);

        return GRAPHQL_CONTENT_TYPE.equals(contentType) || (contentType != null
                && contentType.startsWith(GRAPHQL_CONTENT_TYPE));

    }

}
