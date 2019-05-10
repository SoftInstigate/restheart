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
import org.restheart.plugins.GlobalTransformer;
import org.restheart.metadata.TransformerMetadata;
import org.restheart.metadata.TransformerMetadata.PHASE;
import org.restheart.plugins.PluginsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * handler that applies the transformers defined in the collection properties to
 * the request
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestTransformerHandler
        extends TransformerHandler {

    static final Logger LOGGER
            = LoggerFactory.getLogger(RequestTransformerHandler.class);

    /**
     * Creates a new instance of RequestTransformerMetadataHandler
     *
     * @param next
     */
    public RequestTransformerHandler(PipedHttpHandler next) {
        super(next);
    }

    @Override
    boolean doesGlobalTransformerAppy(GlobalTransformer gt,
            HttpServerExchange exchange,
            RequestContext context) {
        return gt.getPhase() == PHASE.REQUEST
                && gt.resolve(exchange, context);
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
                        .containsKey(TransformerMetadata.RTS_ELEMENT_NAME));
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
                        .containsKey(TransformerMetadata.RTS_ELEMENT_NAME));
    }

    @Override
    void applyGlobalTransformers(HttpServerExchange exchange, RequestContext context) {
        // execute global request tranformers
        getGlobalTransformers().stream()
                .filter(gt -> doesGlobalTransformerAppy(gt, exchange, context))
                .forEachOrdered(gt -> {
                    if (context.getContent() == null
                            || context.getContent().isDocument()) {
                        gt.transform(
                                exchange,
                                context,
                                context.getContent());
                    } else if (context.getContent().isArray()) {
                        context.getContent().asArray().forEach(doc -> {
                            gt.transform(
                                    exchange,
                                    context,
                                    doc);
                        });
                    }
                });
    }

    @Override
    void applyTransformLogic(
            HttpServerExchange exchange,
            RequestContext context,
            List<TransformerMetadata> rts)
            throws InvalidMetadataException {

        // execute request tranformers
        rts.stream().filter((rt)
                -> (rt.getPhase() == TransformerMetadata.PHASE.REQUEST))
                .forEachOrdered((TransformerMetadata rt) -> {
                    try {
                        var tr = PluginsRegistry.getInstance()
                                .getTransformer(rt.getName());
                        var t = tr.getInstance();
                        var confArgs = tr.getConfArgsAsBsonDocument();

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
                    } catch (IllegalArgumentException iae) {
                        String err = "Cannot find '"
                                + rt.getName()
                                + "' in singleton group 'transformers'";
                        LOGGER.warn(err);
                        context.addWarning(err);
                    } catch (Throwable t) {
                        String err = "Error executing transformer '"
                                + rt.getName() 
                                + "': " 
                                + t.getMessage();
                        LOGGER.warn(err);
                        context.addWarning(err);
                    }
                });
    }
}
