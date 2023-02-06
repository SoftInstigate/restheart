/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
import com.mongodb.client.MongoClient;
import com.mongodb.session.ServerSession;
import io.undertow.server.HttpServerExchange;
import java.util.UUID;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.UuidRepresentation;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.UuidCodec;
import org.restheart.mongodb.RHMongoClients;

import static org.restheart.exchange.ExchangeKeys.CLIENT_SESSION_KEY;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ClientSessionFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSessionFactory.class);

    /**
     *
     * @return
     */
    public static ClientSessionFactory getInstance() {
        return ClientSessionFactoryHolder.INSTANCE;
    }

    private static class ClientSessionFactoryHolder {
        private static final ClientSessionFactory INSTANCE = new ClientSessionFactory();
    }

    protected MongoClient mClient = RHMongoClients.mclient();

    /**
     *
     * @param sid
     * @return
     */
    public ServerSession createServerSession(UUID sid) {
        return new ServerSessionImpl(createServerSessionIdentifier(sid));
    }

    private BsonBinary createServerSessionIdentifier(UUID sid) {
        var uuidCodec = new UuidCodec(UuidRepresentation.STANDARD);
        var holder = new BsonDocument();
        var bsonDocumentWriter = new BsonDocumentWriter(holder);
        bsonDocumentWriter.writeStartDocument();
        bsonDocumentWriter.writeName("id");
        uuidCodec.encode(bsonDocumentWriter, sid, EncoderContext.builder().build());
        bsonDocumentWriter.writeEndDocument();
        return holder.getBinary("id");
    }

    /**
     *
     * @param exchange
     * @return
     * @throws IllegalArgumentException
     */
    public ClientSessionImpl getClientSession(HttpServerExchange exchange) throws IllegalArgumentException {
        var _sid = exchange.getQueryParameters().get(CLIENT_SESSION_KEY).getFirst();

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

        var cso = ClientSessionOptions
            .builder()
            .causallyConsistent(options.isCausallyConsistent())
            .build();

        return createClientSession(sid, cso);
    }

    ClientSessionImpl createClientSession(final UUID sid, final ClientSessionOptions options) {
        var mergedOptions = ClientSessionOptions
            .builder(options)
            .causallyConsistent(true)
            .build();

        return new ClientSessionImpl(new SimpleServerSessionPool(SessionsUtils.getCluster(), sid), mClient, mergedOptions);
    }
}
