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
package org.restheart.handlers.sessions;

import org.restheart.db.sessions.ClientSessionFactory;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientException;
import com.mongodb.client.ClientSession;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.UUID;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.restheart.db.Database;
import org.restheart.db.DatabaseImpl;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.representation.Resource;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.representation.RepUtils;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * creates a session with a started transaction
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PostSessionHandler extends PipedHttpHandler {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(PostSessionHandler.class);

    private static MongoClient MCLIENT = MongoDBClientSingleton
            .getInstance().getClient();

    /**
     * Creates a new instance of PostTxnsHandler
     */
    public PostSessionHandler() {
        super();
    }

    public PostSessionHandler(PipedHttpHandler next) {
        super(next, new DatabaseImpl());
    }

    public PostSessionHandler(PipedHttpHandler next, Database dbsDAO) {
        super(next, dbsDAO);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        if (context.isInError()) {
            next(exchange, context);
            return;
        }

        try {
            String sid = UUID.randomUUID().toString();

            exchange.getResponseHeaders()
                    .add(HttpString.tryFromString("Location"),
                            RepUtils.getReferenceLink(
                                    context,
                                    URLUtils.getRemappedRequestURL(exchange),
                                    new BsonString(sid)));

            context.setResponseContentType(Resource.HAL_JSON_MEDIA_TYPE);
            context.setResponseStatusCode(HttpStatus.SC_NO_CONTENT);
        } catch (MongoClientException mce) {
            LOGGER.error("Error {}",
                    mce.getMessage());

            // TODO check if server supports sessions
            if (!MongoDBClientSingleton.isReplicaSet()) {
                ResponseHelper.endExchangeWithMessage(exchange,
                        context,
                        HttpStatus.SC_BAD_GATEWAY,
                        mce.getMessage());
            } else {
                throw mce;
            }
        }

        next(exchange, context);
    }
}
