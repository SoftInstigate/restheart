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
package org.restheart.mongodb.handlers.sessions;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientException;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.UUID;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.mongodb.db.sessions.SessionOptions;
import org.restheart.mongodb.db.sessions.Sid;
import org.restheart.mongodb.utils.URLUtils;
import org.restheart.representation.RepresentationUtils;
import org.restheart.representation.Resource;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * creates a session with a started transaction
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PostSessionHandler extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(PostSessionHandler.class);

    private static MongoClient MCLIENT = MongoClientSingleton
            .getInstance().getClient();

    /**
     * Creates a new instance of PostTxnsHandler
     */
    public PostSessionHandler() {
        super();
    }

    /**
     *
     * @param next
     */
    public PostSessionHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = MongoRequest.of(exchange);
        var response = MongoResponse.of(exchange);

        if (request.isInError()) {
            next(exchange);
            return;
        }

        try {
            UUID sid = Sid.randomUUID(options(request));

            exchange.getResponseHeaders()
                    .add(HttpString.tryFromString("Location"),
                            RepresentationUtils.getReferenceLink(
                                    URLUtils.getRemappedRequestURL(exchange),
                                    new BsonString(sid.toString())));

            response.setContentType(Resource.HAL_JSON_MEDIA_TYPE);
            response.setStatusCode(HttpStatus.SC_CREATED);
        } catch (MongoClientException mce) {
            LOGGER.error("Error {}",
                    mce.getMessage());

            // TODO check if server supports sessions
            if (!MongoClientSingleton.getInstance().isReplicaSet()) {
                response.setIError(
                        HttpStatus.SC_BAD_GATEWAY,
                        mce.getMessage());
            } else {
                throw mce;
            }
        }

        next(exchange);
    }

    private SessionOptions options(MongoRequest request) {
        final BsonValue _content = request.getContent();

        // must be an object
        if (!_content.isDocument()) {
            return new SessionOptions();
        }

        BsonDocument content = _content.asDocument();

        BsonValue _ops = content.get("ops");

        // must be an object, optional
        if (_ops != null
                && _ops.isDocument()
                && _ops.asDocument()
                        .containsKey(SessionOptions.CAUSALLY_CONSISTENT_PROP)
                && _ops.asDocument().get(SessionOptions.CAUSALLY_CONSISTENT_PROP)
                        .isBoolean()) {

            return new SessionOptions(_ops.asDocument()
                    .get(SessionOptions.CAUSALLY_CONSISTENT_PROP)
                    .asBoolean()
                    .getValue());
        } else {
            return new SessionOptions();
        }
    }
}
