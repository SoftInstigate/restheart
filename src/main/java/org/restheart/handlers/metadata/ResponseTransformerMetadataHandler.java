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
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonValue;
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
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseTransformerMetadataHandler
        extends AbstractTransformerMetadataHandler {

    static final Logger LOGGER
            = LoggerFactory.getLogger(ResponseTransformerMetadataHandler.class);

    /**
     * Creates a new instance of ResponseTransformerMetadataHandler
     *
     */
    public ResponseTransformerMetadataHandler() {
        super(null);
    }

    /**
     * Creates a new instance of ResponseTransformerMetadataHandler
     *
     * @param next
     */
    public ResponseTransformerMetadataHandler(PipedHttpHandler next) {
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
                .containsKey(RepresentationTransformer.RTS_ELEMENT_NAME));
    }

    @Override
    boolean canDBRepresentationTransformersAppy(RequestContext context) {
        return (context.getMethod() == RequestContext.METHOD.GET
                && (context.getType() == RequestContext.TYPE.DB
                || context.getType() == RequestContext.TYPE.COLLECTION
                || context.getType() == RequestContext.TYPE.SCHEMA_STORE)
                && context.getDbProps()
                .containsKey(RepresentationTransformer.RTS_ELEMENT_NAME));
    }

    @Override
    void enforceDbRepresentationTransformLogic(
            HttpServerExchange exchange,
            RequestContext context)
            throws InvalidMetadataException {
        List<RepresentationTransformer> dbRts
                = RepresentationTransformer
                .getFromJson(context.getDbProps());

        RequestContext.TYPE requestType = context.getType(); // DB or COLLECTION

        for (RepresentationTransformer rt : dbRts) {
            if (rt.getPhase() == RepresentationTransformer.PHASE.RESPONSE) {
                Transformer t = (Transformer) NamedSingletonsFactory
                        .getInstance()
                        .get("transformers", rt.getName());

                if (t == null) {
                    throw new IllegalArgumentException("cannot find singleton "
                            + rt.getName()
                            + " in singleton group transformers");
                }

                if (rt.getScope() == RepresentationTransformer.SCOPE.THIS
                        && requestType == RequestContext.TYPE.DB) {
                    t.transform(
                            exchange,
                            context,
                            context.getResponseContent(),
                            rt.getArgs());
                } else if (rt.getScope()
                        == RepresentationTransformer.SCOPE.CHILDREN
                        && requestType == RequestContext.TYPE.COLLECTION) {
                    if (context
                            .getResponseContent()
                            .isDocument()
                            && context
                            .getResponseContent()
                            .asDocument()
                            .containsKey("_embedded")) {
                        BsonValue _embedded = context
                                .getResponseContent()
                                .asDocument()
                                .get("_embedded");

                        if (_embedded.isDocument()
                                && _embedded
                                .asDocument()
                                .containsKey("_embedded")) {
                            // execute the logic on children documents
                            BsonArray colls = _embedded.asDocument()
                                    .get("rh:coll")
                                    .asArray();

                            if (colls != null) {
                                colls.getValues().stream().forEach(
                                        (coll) -> {
                                            t.transform(
                                                    exchange,
                                                    context,
                                                    coll.asDocument(),
                                                    rt.getArgs());
                                        });
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    void enforceCollRepresentationTransformLogic(
            HttpServerExchange exchange,
            RequestContext context)
            throws InvalidMetadataException {
        List<RepresentationTransformer> dbRts = RepresentationTransformer
                .getFromJson(context.getCollectionProps());

        RequestContext.TYPE requestType = context.getType(); // DOCUMENT or COLLECTION

        for (RepresentationTransformer rt : dbRts) {
            if (rt.getPhase() == RepresentationTransformer.PHASE.RESPONSE) {
                Transformer t;

                try {
                    t = (Transformer) NamedSingletonsFactory
                            .getInstance()
                            .get("transformers", rt.getName());
                } catch (IllegalArgumentException ex) {
                    context.addWarning("error applying transformer: "
                            + ex.getMessage());
                    return;
                }

                if (t == null) {
                    throw new IllegalArgumentException("cannot find singleton "
                            + rt.getName()
                            + " in singleton group transformers");
                }

                if (rt.getScope() == RepresentationTransformer.SCOPE.THIS
                        && requestType == RequestContext.TYPE.COLLECTION) {
                    // evaluate the script on collection
                    t.transform(
                            exchange,
                            context,
                            context.getResponseContent(),
                            rt.getArgs());
                } else if (rt.getScope() == RepresentationTransformer.SCOPE.CHILDREN
                        && requestType == RequestContext.TYPE.COLLECTION) {
                    if (context
                            .getResponseContent()
                            .isDocument()
                            && context
                            .getResponseContent()
                            .asDocument()
                            .containsKey("_embedded")) {
                        BsonValue _embedded = context
                                .getResponseContent()
                                .asDocument()
                                .get("_embedded");

                        if (_embedded.isDocument()
                                && _embedded
                                .asDocument()
                                .containsKey("rh:doc")) {

                            // execute the logic on children documents
                            BsonArray docs = _embedded
                                    .asDocument()
                                    .get("rh:doc")
                                    .asArray();

                            docs.getValues().stream().forEach((doc) -> {
                                t.transform(
                                        exchange,
                                        context,
                                        doc.asDocument(),
                                        rt.getArgs());
                            });
                        }

                        if (_embedded.isDocument()
                                && _embedded
                                .asDocument().
                                containsKey("rh:file")) {
                            // execute the logic on children files
                            BsonArray files = _embedded
                                    .asDocument()
                                    .get("rh:file")
                                    .asArray();

                            if (files != null) {
                                files.getValues().stream().forEach((file) -> {
                                    t.transform(
                                            exchange,
                                            context,
                                            file.asDocument(),
                                            rt.getArgs());
                                });
                            }
                        }
                    }

                } else if (rt.getScope()
                        == RepresentationTransformer.SCOPE.CHILDREN
                        && requestType == RequestContext.TYPE.DOCUMENT) {
                    t.transform(exchange,
                            context,
                            context.getResponseContent(),
                            rt.getArgs());
                }
            }
        }
    }
}
