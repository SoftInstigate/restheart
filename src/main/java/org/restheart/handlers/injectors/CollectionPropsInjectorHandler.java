/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.handlers.injectors;

import com.mongodb.DBObject;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;
import org.restheart.db.Database;
import org.restheart.db.DbsDAO;

/**
 * this handler injects the collection properties in the RequestContext
 * it is also responsible of sending NOT_FOUND in case of requests
 * involving not existing collections (that are not PUT)
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class CollectionPropsInjectorHandler extends PipedHttpHandler {

    private final Database dbsDAO;

    /**
     * Creates a new instance of MetadataInjecterHandler
     *
     * @param next
     */
    public CollectionPropsInjectorHandler(PipedHttpHandler next) {
        this(next, new DbsDAO());
    }

    public CollectionPropsInjectorHandler(PipedHttpHandler next, DbsDAO dbsDAO) {
        super(next);
        this.dbsDAO = dbsDAO;
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
                collProps = dbsDAO.getCollectionProperties(context.getDBName(), context.getCollectionName());
                if (collProps != null) {
                    collProps.put("_collection-props-cached", false);
                } else if (!(context.getType() == RequestContext.TYPE.COLLECTION
                        && context.getMethod() == RequestContext.METHOD.PUT)
                        && context.getType() != RequestContext.TYPE.ROOT
                        && context.getType() != RequestContext.TYPE.DB) {
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND,
                            "collection does not exist");
                    return;
                }
            } else {
                collProps = LocalCachesSingleton.getInstance()
                        .getCollectionProps(context.getDBName(), context.getCollectionName());
            }

            if (collProps == null
                    && !(context.getType() == RequestContext.TYPE.COLLECTION
                    && context.getMethod() == RequestContext.METHOD.PUT)
                    && context.getType() != RequestContext.TYPE.ROOT
                    && context.getType() != RequestContext.TYPE.DB) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND,
                        "collection does not exist");
                return;
            }

            context.setCollectionProps(collProps);
        }

        next.handleRequest(exchange, context);
    }
}
