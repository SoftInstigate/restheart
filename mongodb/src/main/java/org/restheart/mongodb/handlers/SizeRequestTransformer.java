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
package org.restheart.mongodb.handlers;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;

/**
 * For count request (/_size) sets the pagesize to 0 to avoid retrieving data
 * and sets response content to just contain the _size property
 *
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SizeRequestTransformer extends PipelinedHandler {
    private final boolean phase;

    /**
     *
     * @param phase true for request phase, false for response
     */
    public SizeRequestTransformer(boolean phase) {
        this.phase = phase;
    }

    /**
     *
     * @param exchange
     */
    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        // for request phase
        if (phase) {
            // this avoids the query to be executed, just count
            MongoRequest.of(exchange).setPagesize(0);
        } else {
            var response = MongoResponse.of(exchange);

            // for response phase
            if (response.getCount() >= 0) {
                response.setContent(new BsonDocument("_size", 
                        new BsonInt64(response.getCount())));
            }
        }

        next(exchange);
    }
}
