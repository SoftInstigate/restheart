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
package org.restheart.handlers.txns;

import org.restheart.db.sessions.XClientSessionFactory;
import org.restheart.db.sessions.XClientSession;
import com.mongodb.MongoCommandException;
import io.undertow.server.HttpServerExchange;
import org.restheart.db.Database;
import org.restheart.db.DatabaseImpl;
import org.restheart.representation.Resource;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * aborts transaction of the session
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteTxnHandler extends PipedHttpHandler {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(DeleteTxnHandler.class); 

    /**
     * Creates a new instance of DeleteTxnHandler
     */
    public DeleteTxnHandler() {
        super();
    }

    public DeleteTxnHandler(PipedHttpHandler next) {
        super(next, new DatabaseImpl());
    }

    public DeleteTxnHandler(PipedHttpHandler next, Database dbsDAO) {
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

        String sid = context.getCollectionName();

        LOGGER.debug("server session id {}", sid);

        XClientSession cs = XClientSessionFactory.getClientSession(sid);
        
        if (!cs.hasActiveTransaction()) {
            // this avoids sending the startTransaction msg
            cs.setMessageSentInCurrentTransaction(true);
            cs.startTransaction();
        }

        if (cs == null) {
            LOGGER.debug("session not found {}", sid);

            context.setResponseStatusCode(HttpStatus.SC_NOT_FOUND);
        } else {

            try {
                cs.abortTransaction();
                cs.close();

                context.setResponseContentType(Resource.HAL_JSON_MEDIA_TYPE);
                context.setResponseStatusCode(HttpStatus.SC_NO_CONTENT);
            } catch (MongoCommandException mce) {
                LOGGER.error("Error {} {}, {}",
                        mce.getErrorCode(),
                        mce.getErrorCodeName(),
                        mce.getErrorMessage());

                if (mce.getErrorCode() == 20) {
                    ResponseHelper.endExchangeWithMessage(exchange,
                            context,
                            HttpStatus.SC_BAD_GATEWAY,
                            mce.getErrorCodeName() + ", " + mce.getErrorMessage());
                } else {
                    throw mce;
                }
            }
        }

        next(exchange, context);
    }
}
