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
package org.restheart.handlers.metadata;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.restheart.hal.metadata.InvalidMetadataException;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.hal.metadata.RepresentationTransformer;
import org.restheart.hal.metadata.singletons.Transformer;
import org.restheart.handlers.RequestContext;
import org.restheart.hal.metadata.singletons.NamedSingletonsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RequestTransformerMetadataHandler extends AbstractTransformerHandler {
    static final Logger LOGGER = LoggerFactory.getLogger(RequestTransformerMetadataHandler.class);

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
        return ((context.getMethod() == RequestContext.METHOD.PUT || context.getMethod() == RequestContext.METHOD.PATCH || context.getMethod() == RequestContext.METHOD.POST)
                && (context.getType() == RequestContext.TYPE.DOCUMENT || context.getType() == RequestContext.TYPE.COLLECTION || 
                context.getType() == RequestContext.TYPE.FILE || context.getType() == RequestContext.TYPE.FILES_BUCKET)
                && context.getCollectionProps() != null
                && context.getCollectionProps().containsField(RepresentationTransformer.RTS_ELEMENT_NAME));
    }

    @Override
    boolean canDBRepresentationTransformersAppy(RequestContext context) {
        return ((context.getMethod() == RequestContext.METHOD.PUT || context.getMethod() == RequestContext.METHOD.PATCH)
                && (context.getType() == RequestContext.TYPE.DB || context.getType() == RequestContext.TYPE.COLLECTION || context.getType() == RequestContext.TYPE.FILES_BUCKET)
                && context.getDbProps() != null
                && context.getDbProps().containsField(RepresentationTransformer.RTS_ELEMENT_NAME));
    }

    @Override
    void enforceDbRepresentationTransformLogic(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException {
        List<RepresentationTransformer> dbRts = RepresentationTransformer.getFromJson(context.getDbProps());

        RequestContext.TYPE requestType = context.getType(); // DB, COLLECTION or FILES_BUCKET

        for (RepresentationTransformer rt : dbRts) {
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

            if (rt.getPhase() == RepresentationTransformer.PHASE.REQUEST) {
                if (rt.getScope() == RepresentationTransformer.SCOPE.THIS && requestType == RequestContext.TYPE.DB) {
                    t.tranform(exchange, context, context.getContent(), rt.getArgs());
                } else {
                    t.tranform(exchange, context, context.getContent(), rt.getArgs());
                }
            }
        }
    }

    @Override
    void enforceCollRepresentationTransformLogic(HttpServerExchange exchange, RequestContext context) throws InvalidMetadataException {
        List<RepresentationTransformer> collRts = RepresentationTransformer.getFromJson(context.getCollectionProps());

        RequestContext.TYPE requestType = context.getType(); // DOCUMENT, FILE, COLLECTION or FILES_BUCKET
        RequestContext.METHOD requestMethod = context.getMethod(); // PUT, PATCH or POST

        for (RepresentationTransformer rt : collRts) {
            if (rt.getPhase() == RepresentationTransformer.PHASE.REQUEST) {
                Transformer t = (Transformer) NamedSingletonsFactory.getInstance().get("transformers", rt.getName());

                if (t == null) {
                    throw new IllegalArgumentException("cannot find singleton " + rt.getName() + " in singleton group transformers");
                }

                if ((requestMethod == RequestContext.METHOD.PUT || requestMethod == RequestContext.METHOD.PATCH) && rt.getScope() == RepresentationTransformer.SCOPE.THIS && requestType == RequestContext.TYPE.COLLECTION) {
                    t.tranform(exchange, context, context.getContent(), rt.getArgs());
                } else if ((requestMethod == RequestContext.METHOD.PUT || requestMethod == RequestContext.METHOD.PATCH) && rt.getScope() == RepresentationTransformer.SCOPE.CHILDREN && (requestType == RequestContext.TYPE.DOCUMENT || requestType == RequestContext.TYPE.FILE)) {
                    t.tranform(exchange, context, context.getContent(), rt.getArgs());
                } else if (requestMethod == RequestContext.METHOD.POST && rt.getScope() == RepresentationTransformer.SCOPE.CHILDREN && requestType == RequestContext.TYPE.COLLECTION) {
                    t.tranform(exchange, context, context.getContent(), rt.getArgs());
                } else if (requestMethod == RequestContext.METHOD.POST && rt.getScope() == RepresentationTransformer.SCOPE.CHILDREN && requestType == RequestContext.TYPE.FILES_BUCKET) {
                    t.tranform(exchange, context, context.getContent(), rt.getArgs());
                }
            }
        }
    }
}
