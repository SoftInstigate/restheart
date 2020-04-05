/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.exchange;

import io.undertow.server.HttpServerExchange;
import java.io.IOException;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.json.JsonMode;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.handlers.exchange.ByteArrayResponse;
import org.restheart.handlers.exchange.ExchangeKeys.REPRESENTATION_FORMAT;
import org.restheart.mongodb.representation.Resource;
import org.restheart.utils.JsonUtils;

/**
 * Injects the response content to ByteArrayRequest buffer from BsonRequest
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseContentInjector extends PipelinedHandler {
    /**
     */
    public ResponseContentInjector() {
        super(null);
    }

    /**
     * @param next
     */
    public ResponseContentInjector(PipelinedHandler next) {
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

        addWarnings(request, response);

        var pr = ByteArrayResponse.wrap(exchange);
        
        if (request.getJsonMode() == JsonMode.SHELL) {
            pr.setContentType(Resource.JAVACRIPT_MEDIA_TYPE);
        } else if (request.getRepresentationFormat() == REPRESENTATION_FORMAT.HAL) {
            pr.setContentType(Resource.HAL_JSON_MEDIA_TYPE);
        } else {
            pr.setContentType(Resource.JSON_MEDIA_TYPE);
        }
        
        // This makes the content availabe to ByteArrayResponse
        // core's ResponseSender uses ByteArrayResponse 
        // to send the content to the client
        if (response.getContent() != null) {
            try {
                ByteArrayResponse.wrap(exchange)
                        .writeContent(JsonUtils.toJson(response.getContent(),
                                BsonRequest.wrap(exchange).getJsonMode())
                                .getBytes());
            } catch (IOException ioe) {
                //LOGGER.error("Error writing request content", ioe);
            }
        }

        if (!exchange.isResponseStarted()) {
            exchange.setStatusCode(response.getStatusCode());
        }

        next(exchange);
    }

    private void addWarnings(BsonRequest request, BsonResponse response) {
        var responseContent = response.getContent();
        
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
            
            response.setContent(responseContent);
        }
    }
    
    private BsonDocument getWarningDoc(String warning) {
        Resource nrep = new Resource("#warnings");
        nrep.addProperty("message", new BsonString(warning));
        return nrep.asBsonDocument();
    }
}
