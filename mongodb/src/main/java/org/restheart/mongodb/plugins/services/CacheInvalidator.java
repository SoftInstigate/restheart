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
package org.restheart.mongodb.plugins.services;

import io.undertow.server.HttpServerExchange;
import java.util.Deque;
import org.restheart.mongodb.Bootstrapper;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.mongodb.Configuration;
import org.restheart.mongodb.handlers.injectors.LocalCachesSingleton;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.utils.HttpStatus;
import org.restheart.mongodb.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "cacheInvalidator",
        description = "Invalidates the db and collection metadata cache",
        defaultURI = "/ic"
)
public class CacheInvalidator implements Service {

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handle(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);

        if (!Configuration.get().isLocalCacheEnabled()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_NOT_MODIFIED,
                    "caching is off");
            return;
        }

        if (request.isOptions()) {
            handleOptions(exchange);
        } else if (request.isPost()) {
            Deque<String> _db = exchange.getQueryParameters().get("db");
            Deque<String> _coll = exchange.getQueryParameters().get("coll");

            if (_db == null || _db.getFirst() == null) {
                ResponseHelper.endExchangeWithMessage(
                        exchange,
                        HttpStatus.SC_BAD_REQUEST,
                        "the db query paramter is mandatory");
            } else {
                String db = _db.getFirst();

                if (_coll == null || _coll.getFirst() == null) {
                    LocalCachesSingleton.getInstance().invalidateDb(db);
                } else {
                    String coll = _coll.getFirst();

                    LocalCachesSingleton.getInstance()
                            .invalidateCollection(db, coll);
                }

                response.setStatusCode(HttpStatus.SC_OK);
            }
        } else {
            response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }
}
