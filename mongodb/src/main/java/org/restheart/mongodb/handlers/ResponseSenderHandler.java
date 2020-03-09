/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.mongodb.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonMode;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.mongodb.representation.Resource;
import org.restheart.mongodb.representation.Resource.REPRESENTATION_FORMAT;
import org.restheart.mongodb.utils.JsonUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseSenderHandler extends PipelinedHandler {
    /**
     */
    public ResponseSenderHandler() {
        super(null);
    }

    /**
     * @param next
     */
    public ResponseSenderHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        BsonValue responseContent = response.getContent();

        if (response.getWarnings() != null
                && !response.getWarnings().isEmpty()) {
            if (responseContent == null) {
                responseContent = new BsonDocument();
            }

            BsonArray warnings = new BsonArray();

            // add warnings, in hal o json format
            if (responseContent.isDocument()) {
                if (Resource.isStandardRep(request)
                        || Resource.isSHALRep(request)) {
                    response.setContentType(Resource.JSON_MEDIA_TYPE);

                    responseContent.asDocument().append("_warnings", warnings);
                    response.getWarnings().forEach(
                            w -> warnings.add(new BsonString(w)));

                } else {
                    response.setContentType(Resource.HAL_JSON_MEDIA_TYPE);

                    BsonDocument _embedded;
                    if (responseContent.asDocument().get("_embedded") == null) {
                        _embedded = new BsonDocument();
                        responseContent.asDocument().append("_embedded",
                                _embedded);
                    } else {
                        _embedded = responseContent
                                .asDocument()
                                .get("_embedded")
                                .asDocument();
                    }

                    _embedded
                            .append("rh:warnings", warnings);

                    response.getWarnings().forEach(
                            w -> warnings.add(getWarningDoc(w)));
                }
            }
        }

        if (request.getJsonMode() == JsonMode.SHELL) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                    Resource.JAVACRIPT_MEDIA_TYPE);
        } else if (request.getRepresentationFormat() == REPRESENTATION_FORMAT.HAL) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                    Resource.HAL_JSON_MEDIA_TYPE);
        } else {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE,
                    Resource.JSON_MEDIA_TYPE);
        }

        if (!exchange.isResponseStarted()) {
            exchange.setStatusCode(response.getStatusCode());
        }

        if (responseContent != null) {
            exchange.getResponseSender().send(
                    JsonUtils.toJson(responseContent, request.getJsonMode()));
        }

        exchange.endExchange();

        next(exchange);
    }

    private BsonDocument getWarningDoc(String warning) {
        Resource nrep = new Resource("#warnings");
        nrep.addProperty("message", new BsonString(warning));
        return nrep.asBsonDocument();
    }
}
