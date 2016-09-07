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
import org.bson.BsonDocument;
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
 * handler that applies the transformers defined in the collection properties to
 * the request
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestTransformerMetadataHandler
        extends AbstractTransformerMetadataHandler {
    static final Logger LOGGER
            = LoggerFactory.getLogger(RequestTransformerMetadataHandler.class);

    /**
     * Creates a new instance of RequestTransformerMetadataHandler
     *
     * @param next
     */
    public RequestTransformerMetadataHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    boolean canCollRepresentationTransformersAppy(RequestContext context) {
        return ((context.getMethod() == RequestContext.METHOD.PUT
                || context.getMethod() == RequestContext.METHOD.PATCH
                || context.getMethod() == RequestContext.METHOD.POST)
                && (context.getType() == RequestContext.TYPE.DOCUMENT
                || context.getType() == RequestContext.TYPE.COLLECTION
                || context.getType() == RequestContext.TYPE.FILE
                || context.getType() == RequestContext.TYPE.FILES_BUCKET
                || context.getType() == RequestContext.TYPE.SCHEMA_STORE
                || context.getType() == RequestContext.TYPE.SCHEMA)
                && context.getCollectionProps() != null
                && context.getCollectionProps()
                .containsKey(
                        RepresentationTransformer.RTS_ELEMENT_NAME));
    }

    @Override
    boolean canDBRepresentationTransformersAppy(RequestContext context) {
        return ((context.getMethod() == RequestContext.METHOD.PUT
                || context.getMethod() == RequestContext.METHOD.PATCH)
                && (context.getType() == RequestContext.TYPE.DB
                || context.getType() == RequestContext.TYPE.COLLECTION
                || context.getType() == RequestContext.TYPE.FILES_BUCKET
                || context.getType() == RequestContext.TYPE.SCHEMA_STORE)
                && context.getDbProps() != null
                && context.getDbProps()
                .containsKey(
                        RepresentationTransformer.RTS_ELEMENT_NAME));
    }

    @Override
    void enforceDbRepresentationTransformLogic(
            HttpServerExchange exchange, 
            RequestContext context) 
            throws InvalidMetadataException {
        List<RepresentationTransformer> dbRts
                = RepresentationTransformer.getFromJson(context.getDbProps());

        RequestContext.TYPE requestType = context.getType(); // DB, COLLECTION or FILES_BUCKET

        for (RepresentationTransformer rt : dbRts) {
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

            if (rt.getPhase() == RepresentationTransformer.PHASE.REQUEST) {
                if (rt.getScope() == RepresentationTransformer.SCOPE.THIS 
                        && requestType == RequestContext.TYPE.DB) {
                    t.transform(
                            exchange,
                            context,
                            context.getContent().asDocument(),
                            rt.getArgs());
                } else {
                    t.transform(
                            exchange,
                            context,
                            context.getContent().asDocument(),
                            rt.getArgs());
                }
            }
        }
    }

    @Override
    void enforceCollRepresentationTransformLogic(
            HttpServerExchange exchange,
            RequestContext context)
            throws InvalidMetadataException {
        List<RepresentationTransformer> collRts = RepresentationTransformer
                .getFromJson(context.getCollectionProps());

        // DOCUMENT, FILE, COLLECTION or FILES_BUCKET
        RequestContext.TYPE requestType = context.getType();

        // PUT, PATCH or POST
        RequestContext.METHOD requestMethod = context.getMethod();

        for (RepresentationTransformer rt : collRts) {
            if (rt.getPhase() == RepresentationTransformer.PHASE.REQUEST) {
                Transformer t = (Transformer) NamedSingletonsFactory
                        .getInstance()
                        .get("transformers", rt.getName());

                if (t == null) {
                    throw new IllegalArgumentException("cannot find singleton "
                            + rt.getName()
                            + " in singleton group transformers");
                }

                BsonDocument content = context.getContent().asDocument();

                // content can be an array for bulk POST
                if (content == null) {
                    appyTransformationOnObject(
                            t,
                            exchange,
                            context,
                            requestMethod,
                            requestType,
                            rt.getScope(),
                            new BsonDocument(),
                            rt.getArgs());
                } else if (content.isDocument()) {
                    appyTransformationOnObject(
                            t,
                            exchange,
                            context,
                            requestMethod,
                            requestType,
                            rt.getScope(),
                            content.asDocument(),
                            rt.getArgs());
                } else {
                    BsonArray arrayContent = content.asArray();

                    arrayContent.stream().forEach(obj -> {
                        if (obj.isDocument()) {
                            appyTransformationOnObject(
                                    t,
                                    exchange,
                                    context,
                                    requestMethod,
                                    requestType,
                                    rt.getScope(),
                                    obj.asDocument(),
                                    rt.getArgs());
                        } else {
                            LOGGER.warn("an element of content array "
                                    + "is not an object");
                        }
                    });
                }
            }
        }
    }

    private void appyTransformationOnObject(Transformer t,
            HttpServerExchange exchange,
            RequestContext context,
            RequestContext.METHOD requestMethod,
            RequestContext.TYPE requestType,
            RepresentationTransformer.SCOPE scope,
            BsonDocument data,
            BsonValue args) {
        if ((requestMethod == RequestContext.METHOD.PUT
                || requestMethod == RequestContext.METHOD.PATCH)
                && scope == RepresentationTransformer.SCOPE.THIS
                && requestType == RequestContext.TYPE.COLLECTION) {
            t.transform(exchange, context, data, args);
        } else if ((requestMethod == RequestContext.METHOD.PUT
                || requestMethod == RequestContext.METHOD.PATCH)
                && scope == RepresentationTransformer.SCOPE.CHILDREN
                && (requestType == RequestContext.TYPE.DOCUMENT
                || requestType == RequestContext.TYPE.FILE)) {
            t.transform(exchange, context, data, args);
        } else if (requestMethod == RequestContext.METHOD.POST
                && scope == RepresentationTransformer.SCOPE.CHILDREN
                && requestType == RequestContext.TYPE.COLLECTION) {
            t.transform(exchange, context, data, args);
        } else if (requestMethod == RequestContext.METHOD.POST
                && scope == RepresentationTransformer.SCOPE.CHILDREN
                && requestType == RequestContext.TYPE.FILES_BUCKET) {
            t.transform(exchange, context, data, args);
        }
    }
}
