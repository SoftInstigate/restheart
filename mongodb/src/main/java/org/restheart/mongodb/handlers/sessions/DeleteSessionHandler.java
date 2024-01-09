/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import io.undertow.server.HttpServerExchange;
import java.util.UUID;
import org.bson.BsonBinary;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.mongodb.RHMongoClients;
import org.restheart.utils.HttpStatus;
import static org.restheart.utils.BsonUtils.document;
import static org.restheart.utils.BsonUtils.array;

import com.mongodb.client.MongoClient;

/**
 *
 * kills a session
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteSessionHandler extends PipelinedHandler {
    private static final MongoClient mclient = RHMongoClients.mclient();

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

        final UUID sid;

        try {
            sid = UUID.fromString(request.getPathParam("/_sessions/{sid}", "sid"));
        } catch (IllegalArgumentException iae) {
            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "Invalid session id");
            next(exchange);
            return;
        }

        try {
            var killCmd = document().put("killSessions", array().add(document().put("id", new BsonBinary(sid)))).get();
            mclient.getDatabase("admin").runCommand(killCmd);
        } catch(Throwable t) {
            response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error killing session");
            next(exchange);
            return;
        }

        next(exchange);
    }
}
