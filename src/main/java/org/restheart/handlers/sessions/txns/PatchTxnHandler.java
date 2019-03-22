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
package org.restheart.handlers.sessions.txns;

import org.restheart.db.sessions.ClientSessionFactory;
import org.restheart.db.sessions.ClientSessionImpl;
import com.mongodb.MongoClient;
import com.mongodb.MongoCommandException;
import static com.mongodb.client.model.Filters.eq;
import io.undertow.server.HttpServerExchange;
import java.util.Arrays;
import java.util.UUID;
import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.UuidRepresentation;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.UuidCodec;
import org.restheart.db.Database;
import org.restheart.db.DatabaseImpl;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.representation.Resource;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import static org.restheart.db.sessions.Txn.CMD.ABORT;
import static org.restheart.db.sessions.Txn.CMD.COMMIT;
import static org.restheart.db.sessions.Txn.CMD.START;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.restheart.db.sessions.Txn;

/**
 *
 * commits the transaction of the session
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PatchTxnHandler extends PipedHttpHandler {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(PatchTxnHandler.class);

    private static MongoClient MCLIENT = MongoDBClientSingleton
            .getInstance().getClient();

    /**
     * Creates a new instance of PatchTxnHandler
     */
    public PatchTxnHandler() {
        super();
    }

    public PatchTxnHandler(PipedHttpHandler next) {
        super(next, new DatabaseImpl());
    }

    public PatchTxnHandler(PipedHttpHandler next, Database dbsDAO) {
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

        String _sid = context.getCollectionName();
        
        UUID sid;

        try {
            sid = UUID.fromString(_sid);
        } catch (IllegalArgumentException iae) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_NOT_ACCEPTABLE,
                    "Invalid session id");
            next(exchange, context);
            return;
        }
        
        ClientSessionImpl cs = ClientSessionFactory.getClientSession(sid);

        Txn.CMD command;

        if (context.getContent() != null
                && context.getContent().isDocument()
                && context.getContent().asDocument().containsKey("txn")
                && context.getContent().asDocument().get("txn").isString()) {

            var txn = context.getContent().asDocument().get("txn").asString();

            try {
                command = Txn.CMD.valueOf(txn.getValue());
            } catch (IllegalArgumentException iae) {
                ResponseHelper.endExchangeWithMessage(exchange,
                        context,
                        HttpStatus.SC_BAD_REQUEST,
                        "request does not contain valid txn property. "
                        + "Allowed valures are "
                        + Arrays.toString(Txn.CMD.values()));
                next(exchange, context);
                return;
            }
        } else {
            ResponseHelper.endExchangeWithMessage(exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    "request does not contain mandatory txn property");
            next(exchange, context);
            return;
        }

        try {
            switch (command) {
                case START:
                    cs.startTransaction();

                    // propagate transaction to server
                    // this avoids error 'Given transaction number X does not match any in-progress transactions.'
                    MCLIENT
                            .getDatabase("db")
                            .getCollection("coll")
                            .find(cs)
                            .limit(1)
                            .projection(eq("_id", 1))
                            .first();
                    break;
                case COMMIT:
                    cs.commitTransaction();
                    break;
                case ABORT:
                    cs.abortTransaction();
                    break;
            }

            context.setResponseContentType(Resource.HAL_JSON_MEDIA_TYPE);
            context.setResponseStatusCode(HttpStatus.SC_OK);
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
            } else if (mce.getErrorCode() == 251) {
                ResponseHelper.endExchangeWithMessage(exchange,
                        context,
                        HttpStatus.SC_NOT_ACCEPTABLE,
                        mce.getErrorCodeName() + ", " + mce.getErrorMessage());
            } else {
                throw mce;
            }
        }

        next(exchange, context);
    }

    public static BsonDocument endSession(String sid) {
        // { killSessions: [ { id : <UUID> }, ... ] } )

        var sids = new BsonArray();

        sids.add(new BsonDocument("id", createServerSessionIdentifier(sid)));

        BsonDocument killSessionsCmd = new BsonDocument(
                "endSessions",
                sids);

        return MCLIENT
                .getDatabase("admin")
                .runCommand(killSessionsCmd, BsonDocument.class);
    }

    private static BsonBinary createServerSessionIdentifier(String serverSessionUUID) {
        UuidCodec uuidCodec = new UuidCodec(UuidRepresentation.STANDARD);
        BsonDocument holder = new BsonDocument();
        BsonDocumentWriter bsonDocumentWriter = new BsonDocumentWriter(holder);
        bsonDocumentWriter.writeStartDocument();
        bsonDocumentWriter.writeName("id");
        uuidCodec.encode(bsonDocumentWriter, UUID.fromString(serverSessionUUID),
                EncoderContext.builder().build());
        bsonDocumentWriter.writeEndDocument();
        return holder.getBinary("id");
    }
}
