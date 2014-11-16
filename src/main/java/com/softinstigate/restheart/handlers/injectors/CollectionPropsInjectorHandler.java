/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
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
package com.softinstigate.restheart.handlers.injectors;

import com.mongodb.DBObject;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;

/**
 * this handler injects the collection properties in the RequestContext this
 * handler is also responsible of sending NOT_FOUND in case of requests
 * involving not existing collections (that are not PUT)
 *
 * @author Andrea Di Cesare
 */
public class CollectionPropsInjectorHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of MetadataInjecterHandler
     *
     * @param next
     */
    public CollectionPropsInjectorHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.getDBName() != null && context.getCollectionName() != null) {
            DBObject collProps;

            if (!LocalCachesSingleton.isEnabled()) {
                collProps = CollectionDAO.getCollectionProps(context.getDBName(), context.getCollectionName());

                if (collProps != null) {
                    collProps.put("_collection-props-cached", false);
                } else if (!(context.getType() == RequestContext.TYPE.COLLECTION && context.getMethod() == RequestContext.METHOD.PUT)
                        && context.getType() != RequestContext.TYPE.ROOT
                        && context.getType() != RequestContext.TYPE.DB) {
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "collection does not exist");
                    return;
                }
            } else {
                collProps = LocalCachesSingleton.getInstance().getCollectionProps(context.getDBName(), context.getCollectionName());
            }

            if (collProps == null
                    && !(context.getType() == RequestContext.TYPE.COLLECTION && context.getMethod() == RequestContext.METHOD.PUT)
                    && context.getType() != RequestContext.TYPE.ROOT
                    && context.getType() != RequestContext.TYPE.DB) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "collection does not exist");
                return;
            }

            context.setCollectionProps(collProps);
        }

        next.handleRequest(exchange, context);
    }
}
