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
package org.restheart.mongodb.handlers.metadata;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.mongodb.metadata.TransformerMetadata;
import org.restheart.plugins.GlobalTransformer;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.Transformer;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class AbstractTransformersExecutor {
    protected final PluginsRegistry pluginsRegistry;
    
    /**
     * Creates a new instance of TransformerHandler
     *
     * @param pluginsRegistry
     */
    public AbstractTransformersExecutor(PluginsRegistry pluginsRegistry) {
        this.pluginsRegistry = pluginsRegistry;
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    public void handle(HttpServerExchange exchange) throws Exception {
        var context = RequestContext.wrap(exchange);
        
        applyGlobalTransformers(exchange);

        if (doesCollTransformerAppy(context)) {
            try {
                applyCollRTransformer(exchange);
            } catch (InvalidMetadataException e) {
                context.addWarning("Error applying transformer: " + e.getMessage());
            }
        }

        if (doesDBTransformerAppy(context)) {
            try {
                applyDbTransformer(exchange);
            } catch (InvalidMetadataException e) {
                context.addWarning("Error applying transformer: " + e.getMessage());
            }
        }
    }

    abstract boolean doesGlobalTransformerAppy(GlobalTransformer gt,
            HttpServerExchange exchange,
            RequestContext context);

    abstract boolean doesCollTransformerAppy(RequestContext context);

    abstract boolean doesDBTransformerAppy(RequestContext context);

    abstract void applyGlobalTransformers(HttpServerExchange exchange);

    abstract void applyTransformLogic(HttpServerExchange exchange, List<TransformerMetadata> dbRts) throws InvalidMetadataException;

    void applyDbTransformer(HttpServerExchange exchange)
            throws InvalidMetadataException {
        var context = RequestContext.wrap(exchange);
        
        List<TransformerMetadata> dbRts
                = TransformerMetadata
                        .getFromJson(context.getDbProps());

        applyTransformLogic(exchange, dbRts);
    }

    void applyCollRTransformer(HttpServerExchange exchange) 
            throws InvalidMetadataException {
        var request = BsonRequest.wrap(exchange);
        
        List<TransformerMetadata> collRts
                = TransformerMetadata
                        .getFromJson(request.getCollectionProps());

        applyTransformLogic(exchange, collRts);
    }

    /**
     *
     * @param exchange
     * @param context
     * @param t
     * @param args
     * @param confArgs
     */
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
