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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.hal.metadata.RepresentationTransformer;
import org.restheart.hal.metadata.singletons.NamedSingletonsFactory;
import org.restheart.hal.metadata.singletons.Transformer;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * handler that applies the transformers defined in the collection properties to
 * the response
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class ResponseTranformerMetadataHandler extends AbstractTransformerMetadataHandler {

    static final Logger LOGGER = LoggerFactory.getLogger(ResponseTranformerMetadataHandler.class);

    /**
     * Creates a new instance of ResponseTranformerMetadataHandler
     *
     */
    public ResponseTranformerMetadataHandler() {
        super(null);
    }
    
    /**
     * Creates a new instance of ResponseTranformerMetadataHandler
     *
     * @param next
     */
    public ResponseTranformerMetadataHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    boolean canCollRepresentationTransformersAppy(RequestContext context) {
        return (context.getMethod() == RequestContext.METHOD.GET
                && (context.getType() == RequestContext.TYPE.DOCUMENT
                || context.getType() == RequestContext.TYPE.COLLECTION
                || context.getType() == RequestContext.TYPE.SCHEMA_STORE
                || context.getType() == RequestContext.TYPE.SCHEMA)
                && context.getCollectionProps()
                .containsField(RepresentationTransformer.RTS_ELEMENT_NAME));
    }

    @Override
    boolean canDBRepresentationTransformersAppy(RequestContext context) {
        return (context.getMethod() == RequestContext.METHOD.GET
                && (context.getType() == RequestContext.TYPE.DB
                || context.getType() == RequestContext.TYPE.COLLECTION
                || context.getType() == RequestContext.TYPE.SCHEMA_STORE)
                && context.getDbProps()
                .containsField(RepresentationTransformer.RTS_ELEMENT_NAME));
    }

    @Override
    void enforceDbRepresentationTransformLogic(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException {
        List<RepresentationTransformer> dbRts = RepresentationTransformer.getFromJson(context.getDbProps());

        RequestContext.TYPE requestType = context.getType(); // DB or COLLECTION

        for (RepresentationTransformer rt : dbRts) {
            if (rt.getPhase() == RepresentationTransformer.PHASE.RESPONSE) {
                Transformer t = (Transformer) NamedSingletonsFactory.getInstance().get("transformers", rt.getName());

                if (t == null) {
                    throw new IllegalArgumentException("cannot find singleton " + rt.getName() + " in singleton group transformers");
                }

                if (rt.getScope() == RepresentationTransformer.SCOPE.THIS && requestType == RequestContext.TYPE.DB) {
                    t.tranform(exchange, context, context.getResponseContent(), rt.getArgs());
                } else if (rt.getScope() == RepresentationTransformer.SCOPE.CHILDREN && requestType == RequestContext.TYPE.COLLECTION) {
                    BasicDBObject _embedded = (BasicDBObject) context.getResponseContent().get("_embedded");

                    // evaluate the script on children collection
                    BasicDBList colls = (BasicDBList) _embedded.get("rh:coll");

                    if (colls != null) {
                        colls.keySet().stream().map((k) -> (DBObject) colls.get(k)).forEach((coll) -> {
                            t.tranform(exchange, context, coll, rt.getArgs());
                        });
                    }
                }
            }
        }
    }

    @Override
    void enforceCollRepresentationTransformLogic(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException {
        List<RepresentationTransformer> dbRts = RepresentationTransformer.getFromJson(context.getCollectionProps());

        RequestContext.TYPE requestType = context.getType(); // DOCUMENT or COLLECTION

        for (RepresentationTransformer rt : dbRts) {
            if (rt.getPhase() == RepresentationTransformer.PHASE.RESPONSE) {
                Transformer t;

                try {
                    t = (Transformer) NamedSingletonsFactory.getInstance().get("transformers", rt.getName());
                } catch (IllegalArgumentException ex) {
                    context.addWarning("error applying transformer: " + ex.getMessage());
                    return;
                }

                if (t == null) {
                    throw new IllegalArgumentException("cannot find singleton " + rt.getName() + " in singleton group transformers");
                }

                if (rt.getScope() == RepresentationTransformer.SCOPE.THIS && requestType == RequestContext.TYPE.COLLECTION) {
                    // evaluate the script on collection
                    t.tranform(exchange, context, context.getResponseContent(), rt.getArgs());
                } else if (rt.getScope() == RepresentationTransformer.SCOPE.CHILDREN && requestType == RequestContext.TYPE.COLLECTION) {
                    BasicDBObject _embedded = (BasicDBObject) context.getResponseContent().get("_embedded");

                    if (_embedded != null) {
                        // execute the logic on children documents
                        BasicDBList docs = (BasicDBList) _embedded.get("rh:doc");

                        if (docs != null) {
                            docs.keySet().stream().map((k) -> (DBObject) docs.get(k)).forEach((doc) -> {
                                t.tranform(exchange, context, doc, rt.getArgs());
                            });
                        }
                        // execute the logic on children files
                        BasicDBList files = (BasicDBList) _embedded.get("rh:file");

                        if (files != null) {
                            files.keySet().stream().map((k) -> (DBObject) files.get(k)).forEach((file) -> {
                                t.tranform(exchange, context, file, rt.getArgs());
                            });
                        }
                    }
                    
                } else if (rt.getScope() == RepresentationTransformer.SCOPE.CHILDREN && requestType == RequestContext.TYPE.DOCUMENT) {
                    t.tranform(exchange, context, context.getResponseContent(), rt.getArgs());
                }
            }
        }
    }
}
