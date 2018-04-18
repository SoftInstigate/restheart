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
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.metadata.NamedSingletonsFactory;
import org.restheart.metadata.transformers.RepresentationTransformer;
import org.restheart.metadata.transformers.Transformer;
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
    boolean doesCollTransformerAppy(RequestContext context) {
        return (!context.isInError()
                && (context.isDocument()
                || context.isBulkDocuments()
                || context.isCollection()
                || context.isCollectionSize()
                || context.isAggregation()
                || context.isFile()
                || context.isFilesBucket()
                || context.isFilesBucketSize()
                || context.isIndex()
                || context.isCollectionIndexes()
                || context.isSchemaStore()
                || context.isSchemaStoreSize()
                || context.isSchema())
                && context.getCollectionProps() != null
                && context.getCollectionProps()
                        .containsKey(RepresentationTransformer.RTS_ELEMENT_NAME));
    }

    @Override
    boolean doesDBTransformerAppy(RequestContext context) {
        return (!context.isInError()
                && (context.isDb()
                || context.isDbSize()
                || context.isDocument()
                || context.isBulkDocuments()
                || context.isCollection()
                || context.isCollectionSize()
                || context.isAggregation()
                || context.isFile()
                || context.isFilesBucket()
                || context.isFilesBucketSize()
                || context.isIndex()
                || context.isCollectionIndexes()
                || context.isSchemaStore()
                || context.isSchemaStoreSize()
                || context.isSchema())
                && context.getDbProps() != null
                && context.getDbProps()
                        .containsKey(RepresentationTransformer.RTS_ELEMENT_NAME));
    }

    @Override
    void applyDbTransformer(
            HttpServerExchange exchange,
            RequestContext context)
            throws InvalidMetadataException {
        List<RepresentationTransformer> dbRts
                = RepresentationTransformer
                        .getFromJson(context.getDbProps());

        applyTransformLogic(exchange, context, dbRts);
    }

    @Override
    void applyCollRTransformer(
            HttpServerExchange exchange,
            RequestContext context)
            throws InvalidMetadataException {
        List<RepresentationTransformer> collRts
                = RepresentationTransformer
                        .getFromJson(context.getCollectionProps());

        applyTransformLogic(exchange, context, collRts);
    }

    private void applyTransformLogic(
            HttpServerExchange exchange,
            RequestContext context,
            List<RepresentationTransformer> rts)
            throws InvalidMetadataException {
        rts.stream().filter((rt)
                -> (rt.getPhase() == RepresentationTransformer.PHASE.REQUEST))
                .forEachOrdered((rt) -> {
                    NamedSingletonsFactory nsf = NamedSingletonsFactory
                            .getInstance();
                    Transformer t = (Transformer) nsf
                            .get("transformers", rt.getName());

                    BsonDocument confArgs
                            = nsf.getArgs("transformers", rt.getName());

                    if (t == null) {
                        throw new IllegalArgumentException(
                                "cannot find singleton "
                                + rt.getName()
                                + " in singleton group transformers");
                    }

                    BsonValue requestContent = context.getContent() == null
                            ? new BsonDocument()
                            : context.getContent();

                    if (requestContent.isDocument()) {
                        t.transform(
                                exchange,
                                context,
                                requestContent,
                                rt.getArgs(),
                                confArgs);
                    } else if (context.isPost()
                            && requestContent.isArray()) {
                        requestContent.asArray().stream().forEachOrdered(
                                (doc) -> {
                                    t.transform(
                                            exchange,
                                            context,
                                            doc,
                                            rt.getArgs(),
                                            confArgs);
                                });
                    }
                });
    }
}
