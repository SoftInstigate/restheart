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
package org.restheart.plugins.services;

import org.restheart.plugins.Service;
import io.undertow.server.HttpServerExchange;
import java.util.Deque;
import java.util.Map;
import org.restheart.Bootstrapper;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.injectors.LocalCachesSingleton;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "cacheInvalidator",
        description = "Invalidates the db and collection metadata cache")
public class CacheInvalidator extends Service {

    /**
     *
     * @param confArgs arguments optionally specified in the configuration file
     */
    public CacheInvalidator(Map<String, Object> confArgs) {
        super(confArgs);
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
            next(exchange, context);
            return;
        }

        if (context.isOptions()) {
            handleOptions(exchange, context);
        } else if (context.isPost()) {
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

                context.setResponseStatusCode(HttpStatus.SC_OK);
            }
        } else {
            context.setResponseStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
        
        next(exchange, context);
    }

    @Override
    public String defaultUri() {
        return "/ic";
    }
}
