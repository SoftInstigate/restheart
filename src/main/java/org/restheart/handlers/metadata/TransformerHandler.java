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
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.metadata.transformers.GlobalTransformer;
import org.restheart.metadata.transformers.Transformer;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class TransformerHandler extends PipedHttpHandler {
    private static final List<GlobalTransformer> globalTransformers
            = new ArrayList<>();
    
    /**
     * Creates a new instance of AbstractTransformerHandler
     *
     * @param next
     */
    public TransformerHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, 
            RequestContext context) throws Exception {
        applyGlobalTransformers(exchange, context);
        
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
    
    /**
     * @return the globalTransformers
     */
    public static List<GlobalTransformer> getGlobalTransformers() {
        return globalTransformers;
    }

    abstract boolean doesGlobalTransformerAppy(GlobalTransformer gt, 
            HttpServerExchange exchange, 
            RequestContext context);
    
    abstract boolean doesCollTransformerAppy(RequestContext context);

    abstract boolean doesDBTransformerAppy(RequestContext context);

    abstract void applyGlobalTransformers(HttpServerExchange exchange, RequestContext context);
    
    abstract void applyDbTransformer(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException;

    abstract void applyCollRTransformer(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException;
    
    protected void applyChildrenTransformLogic(
            HttpServerExchange exchange,
            RequestContext context,
            Transformer t,
            BsonValue args,
            BsonValue confArgs) {
        BsonValue _embedded = context
                .getResponseContent()
                .asDocument()
                .get("_embedded");

        // execute the logic on children documents
        BsonDocument embedded = _embedded.asDocument();

        embedded
                .keySet()
                .stream()
                .filter(key -> "rh:doc".equals(key)
                || "rh:file".equals(key)
                || "rh:bucket".equals(key)
                || "rh:db".equals(key)
                || "rh:coll".equals(key)
                || "rh:index".equals(key)
                || "rh:result".equals(key)
                || "rh:schema".equals(key))
                .filter(key -> embedded.get(key).isArray())
                .forEachOrdered(key -> {

                    BsonArray children = embedded
                            .get(key)
                            .asArray();

                    if (children != null) {
                        children.getValues().stream()
                                .forEach((child) -> {
                                    t.transform(
                                            exchange,
                                            context,
                                            child,
                                            args,
                                            confArgs);
                                });
                    }
                });
    }
}
