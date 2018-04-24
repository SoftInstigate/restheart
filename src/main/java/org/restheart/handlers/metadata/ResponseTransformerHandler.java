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
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.metadata.NamedSingletonsFactory;
import org.restheart.metadata.transformers.GlobalTransformer;
import org.restheart.metadata.transformers.RequestTransformer;
import org.restheart.metadata.transformers.RequestTransformer.PHASE;
import org.restheart.metadata.transformers.RequestTransformer.SCOPE;
import org.restheart.metadata.transformers.Transformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * handler that applies the transformers defined in the collection properties to
 * the response
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseTransformerHandler
        extends TransformerHandler {

    static final Logger LOGGER
            = LoggerFactory.getLogger(ResponseTransformerHandler.class);

    /**
     * Creates a new instance of ResponseTransformerMetadataHandler
     *
     */
    public ResponseTransformerHandler() {
        super(null);
    }

    /**
     * Creates a new instance of ResponseTransformerMetadataHandler
     *
     * @param next
     */
    public ResponseTransformerHandler(PipedHttpHandler next) {
        super(next);
    }
    
    @Override
    boolean doesGlobalTransformerAppy(GlobalTransformer gt, 
            HttpServerExchange exchange, 
            RequestContext context) {
        return gt.getPhase() == PHASE.RESPONSE &&
                gt.resolve(exchange, context);
    }

    @Override
    boolean doesCollTransformerAppy(RequestContext context) {
        // must also apply if global transformers are present
        // also         "error applying transformer: missing 'rts' element; it must be an array"

        
        return (!context.isInError()
                && (context.isDocument()
                || context.isBulkDocuments()
                || context.isCollection()
                || context.isAggregation()
                || context.isFile()
                || context.isFilesBucket()
                || context.isIndex()
                || context.isCollectionIndexes()
                || context.isSchemaStore()
                || context.isSchema())
                && context.getCollectionProps() != null
                && context.getCollectionProps()
                        .containsKey(RequestTransformer.RTS_ELEMENT_NAME));
    }

    @Override
    boolean doesDBTransformerAppy(RequestContext context) {
        return (!context.isInError()
                && (context.isDb()
                || context.isDocument()
                || context.isBulkDocuments()
                || context.isCollection()
                || context.isAggregation()
                || context.isFile()
                || context.isFilesBucket()
                || context.isIndex()
                || context.isCollectionIndexes()
                || context.isSchemaStore()
                || context.isSchema())
                && context.getDbProps() != null
                && context.getDbProps()
                        .containsKey(RequestTransformer.RTS_ELEMENT_NAME));
    }
    
    @Override
    void applyDbTransformer(
            HttpServerExchange exchange,
            RequestContext context)
            throws InvalidMetadataException {
        List<RequestTransformer> dbRts
                = RequestTransformer
                        .getFromJson(context.getDbProps());

        applyTransformLogic(exchange, context, dbRts);
    }

    @Override
    void applyCollRTransformer(
            HttpServerExchange exchange,
            RequestContext context)
            throws InvalidMetadataException {
        List<RequestTransformer> collRts
                = RequestTransformer
                        .getFromJson(context.getCollectionProps());

        applyTransformLogic(exchange, context, collRts);
    }

    private void applyTransformLogic(
            HttpServerExchange exchange,
            RequestContext context,
            List<RequestTransformer> rts)
            throws InvalidMetadataException {
        // execute request transformers
        rts.stream()
                .filter(rt -> rt.getPhase() == PHASE.RESPONSE)
                .forEachOrdered(rt -> {
                    NamedSingletonsFactory nsf = NamedSingletonsFactory
                            .getInstance();

                    Transformer t = (Transformer) nsf
                            .get("transformers", rt.getName());

                    BsonDocument confArgs
                            = nsf.getArgs("transformers", rt.getName());

                    if (t == null) {
                        throw new IllegalArgumentException("cannot find singleton "
                                + rt.getName()
                                + " in singleton group transformers");
                    }

                    if (rt.getScope() == SCOPE.THIS) {
                        t.transform(
                                exchange,
                                context,
                                context.getResponseContent(),
                                rt.getArgs(),
                                confArgs);
                    } else if (context.getResponseContent() != null
                            && context.getResponseContent().isDocument()
                            && context.getResponseContent()
                                    .asDocument()
                                    .containsKey("_embedded")) {
                        applyChildrenTransformLogic(exchange,
                                context,
                                t,
                                rt.getArgs(),
                                confArgs);
                    } else if (context.isDocument()) {
                        t.transform(
                                exchange,
                                context,
                                context.getResponseContent(),
                                rt.getArgs(),
                                confArgs);
                    }
                });
    }
}
