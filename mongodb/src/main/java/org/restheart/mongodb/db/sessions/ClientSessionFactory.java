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
package org.restheart.mongodb.db.sessions;

import com.mongodb.ClientSessionOptions;
import com.mongodb.MongoClient;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.session.ServerSession;
import io.undertow.server.HttpServerExchange;
import java.util.UUID;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.UuidRepresentation;
import static org.bson.assertions.Assertions.notNull;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.UuidCodec;
import static org.restheart.exchange.ExchangeKeys.CLIENT_SESSION_KEY;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ClientSessionFactory {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(ClientSessionFactory.class);
    
    /**
     *
     * @return
     */
    public static ClientSessionFactory getInstance() {
        return ClientSessionFactoryHolder.INSTANCE;
    }

    private static class ClientSessionFactoryHolder {
        private static final ClientSessionFactory INSTANCE
                = new ClientSessionFactory();
    }
    
    /**
     *
     */
    protected ClientSessionFactory() {
    }

    /**
     *
     */
    protected MongoClient mClient = MongoClientSingleton
            .getInstance()
            .getClient();

    /**
     *
     * @param sid
     * @return
     */
    public ServerSession createServerSession(UUID sid) {
        return new ServerSessionImpl(createServerSessionIdentifier(sid));
    }

    private BsonBinary createServerSessionIdentifier(UUID sid) {
        UuidCodec uuidCodec = new UuidCodec(UuidRepresentation.STANDARD);
        BsonDocument holder = new BsonDocument();
        BsonDocumentWriter bsonDocumentWriter = new BsonDocumentWriter(holder);
        bsonDocumentWriter.writeStartDocument();
        bsonDocumentWriter.writeName("id");
        uuidCodec.encode(bsonDocumentWriter,
                sid,
                EncoderContext.builder().build());
        bsonDocumentWriter.writeEndDocument();
        return holder.getBinary("id");
    }

    /**
     *
     * @param exchange
     * @return
     * @throws IllegalArgumentException
     */
    public ClientSessionImpl getClientSession(HttpServerExchange exchange)
            throws IllegalArgumentException {
        String _sid = exchange.getQueryParameters()
                .get(CLIENT_SESSION_KEY).getFirst();

        UUID sid;

        try {
            sid = UUID.fromString(_sid);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Invalid session id");
        }

        var cs = getClientSession(sid);

        LOGGER.debug("Request is executed in session {}", _sid);

        return cs;
    }

    /**
     *
     * @param sid
     * @return
     */
    public ClientSessionImpl getClientSession(UUID sid) {
        var options = Sid.getSessionOptions(sid);

        ClientSessionOptions cso = ClientSessionOptions
                .builder()
                .causallyConsistent(options.isCausallyConsistent())
                .build();

        return createClientSession(sid,
                cso,
                mClient.getReadConcern(),
                mClient.getWriteConcern(),
                mClient.getReadPreference());
    }

    ClientSessionImpl createClientSession(
            UUID sid,
            final ClientSessionOptions options,
            final ReadConcern readConcern,
            final WriteConcern writeConcern,
            final ReadPreference readPreference) {
        notNull("readConcern", readConcern);
        notNull("writeConcern", writeConcern);
        notNull("readPreference", readPreference);

        ClientSessionOptions mergedOptions = ClientSessionOptions
                .builder(options)
                .causallyConsistent(true)
                .build();

        ClientSessionImpl cs = new ClientSessionImpl(
                new SimpleServerSessionPool(SessionsUtils.getCluster(), sid),
                mClient,
                mergedOptions,
                SessionsUtils.getMongoClientDelegate());

        return cs;
    }
}
