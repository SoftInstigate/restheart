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
package org.restheart.handlers.applicationlogic;

import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import java.util.Deque;
import java.util.Map;
import org.restheart.Bootstrapper;
import org.restheart.handlers.injectors.LocalCachesSingleton;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CacheInvalidator extends ApplicationLogicHandler {
    /**
     *
     * @param next
     * @param args
     */
    public CacheInvalidator(PipedHttpHandler next, Map<String, Object> args) {
        super(next, args);
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
        if (!Bootstrapper.getConfiguration().isLocalCacheEnabled()) {
            ResponseHelper.endExchangeWithMessage(
                    exchange, 
                    context,
                    HttpStatus.SC_NOT_MODIFIED, 
                    "caching is off");
            exchange.endExchange();
            return;
        }

        if (context.getMethod() == METHOD.POST) {
            Deque<String> _db = exchange.getQueryParameters().get("db");
            Deque<String> _coll = exchange.getQueryParameters().get("coll");

            if (_db == null || _db.getFirst() == null) {
                ResponseHelper.endExchangeWithMessage(
                        exchange, 
                        context,
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

                exchange.setStatusCode(HttpStatus.SC_OK);
                exchange.endExchange();
            }
        } else {
            exchange.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }
}
