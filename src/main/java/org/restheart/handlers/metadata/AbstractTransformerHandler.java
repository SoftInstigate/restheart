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
package org.restheart.handlers.metadata;

import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class AbstractTransformerHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of RequestScriptMetadataHandler
     *
     * @param next
     */
    public AbstractTransformerHandler(PipedHttpHandler next) {
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
        if (doesCollTransformerAppy(context)) {
            try {
                applyCollRTransformer(exchange, context);
            } catch (Throwable e) {
                context.addWarning("error applying transformer: " + e.getMessage());
            } 
        }

        if (doesDBTransformerAppy(context)) {
            try {
                applyDbTransformer(exchange, context);
            } catch (Throwable e) {
                context.addWarning("error applying transformer: " + e.getMessage());
            }
        }

        next(exchange, context);
    }

    abstract boolean doesCollTransformerAppy(RequestContext context);

    abstract boolean doesDBTransformerAppy(RequestContext context);

    abstract void applyDbTransformer(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException;

    abstract void applyCollRTransformer(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException;
}
