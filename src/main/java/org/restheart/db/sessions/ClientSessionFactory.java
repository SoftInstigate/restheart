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
package org.restheart.db.sessions;

import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoClient;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.session.ServerSession;
import java.util.UUID;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.UuidRepresentation;
import static org.bson.assertions.Assertions.notNull;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.UuidCodec;
import org.restheart.db.MongoDBClientSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ClientSessionFactory {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(ClientSessionFactory.class);

    private static MongoClient MCLIENT = MongoDBClientSingleton
            .getInstance()
            .getClient();

    public static ServerSession createServerSession(String sid) {
        return new ServerSessionImpl(createServerSessionIdentifier(sid));
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

    public static ClientSessionImpl getClientSession(String sid) {
        ClientSessionOptions cso = ClientSessionOptions
                .builder()
                .build();

        return createClientSession(
                sid,
                cso,
                MCLIENT.getReadConcern(),
                MCLIENT.getWriteConcern(),
                MCLIENT.getReadPreference());
    }

    public static ClientSessionImpl createClientSession(
            String sid,
            final ClientSessionOptions options,
            final ReadConcern readConcern,
            final WriteConcern writeConcern,
            final ReadPreference readPreference) {
        notNull("readConcern", readConcern);
        notNull("writeConcern", writeConcern);
        notNull("readPreference", readPreference);

        // TODO allow request to specify session and txn options
        ClientSessionOptions mergedOptions = ClientSessionOptions
                .builder(options)
                .causallyConsistent(true)
                .defaultTransactionOptions(
                        TransactionOptions.merge(
                                options.getDefaultTransactionOptions(),
                                TransactionOptions.builder()
                                        .readConcern(readConcern)
                                        .writeConcern(writeConcern)
                                        .readPreference(readPreference)
                                        .build()))
                .build();

        ClientSessionImpl cs = new ClientSessionImpl(
                new SimpleServerSessionPool(SessionsUtils.getCluster(), sid),
                MCLIENT,
                mergedOptions,
                SessionsUtils.getMongoClientDelegate());

        return cs;

    }
}

final class ServerSessionImpl implements ServerSession {
    interface Clock {
        long millis();
    }

    private Clock clock = new Clock() {
        @Override
        public long millis() {
            return System.currentTimeMillis();
        }
    };

    private final BsonDocument identifier;
    private final long transactionNumber = 1;
    private volatile long lastUsedAtMillis = clock.millis();
    private volatile boolean closed;

    ServerSessionImpl(final BsonBinary identifier) {
        this.identifier = new BsonDocument("id", identifier);
    }

    void close() {
        closed = true;
    }

    long getLastUsedAtMillis() {
        return lastUsedAtMillis;
    }

    @Override
    public long getTransactionNumber() {
        return transactionNumber;
    }

    @Override
    public BsonDocument getIdentifier() {
        lastUsedAtMillis = clock.millis();
        return identifier;
    }

    @Override
    public long advanceTransactionNumber() {
        return transactionNumber;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
