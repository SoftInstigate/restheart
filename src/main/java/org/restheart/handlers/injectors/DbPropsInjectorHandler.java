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

/**
 *
 * this handler injects the db properties in the RequestContext this handler is
 * also responsible of sending NOT_FOUND in case of requests involving not
 * existing dbs (that are not PUT)
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DbPropsInjectorHandler extends PipedHttpHandler {

    /**
     * Creates a new instance of DbPropsInjectorHandler
     *
     * @param next
     */
    public DbPropsInjectorHandler(PipedHttpHandler next) {
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
        if (context.getDBName() != null) {
            DBObject dbProps = null;

            if (!LocalCachesSingleton.isEnabled()) {
                dbProps = getDatabase().getDatabaseProperties(context.getDBName(), true);

                if (dbProps != null) {
                    dbProps.put("_db-props-cached", false);
                } else if (!(context.getType() == RequestContext.TYPE.DB
                        && context.getMethod() == RequestContext.METHOD.PUT)
                        && context.getType() != RequestContext.TYPE.ROOT) {
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "db does not exist");
                    return;
                }
            } else {
                dbProps = LocalCachesSingleton.getInstance().getDBProps(context.getDBName());
            }

            if (dbProps == null
                    && !(context.getType() == RequestContext.TYPE.DB
                    && context.getMethod() == RequestContext.METHOD.PUT)
                    && context.getType() != RequestContext.TYPE.ROOT) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "db does not exist");
                return;
            }

            context.setDbProps(dbProps);
        }

        getNext().handleRequest(exchange, context);
    }
}
