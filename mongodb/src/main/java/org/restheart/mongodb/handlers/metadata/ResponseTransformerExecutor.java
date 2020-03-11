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
import java.util.NoSuchElementException;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.mongodb.metadata.TransformerMetadata;
import org.restheart.mongodb.utils.JsonUtils;
import org.restheart.plugins.mongodb.GlobalTransformer;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mongodb.Transformer.PHASE;
import org.restheart.plugins.mongodb.Transformer.SCOPE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * handler that applies the transformers defined in the collection properties to
 * the response
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "responseTransformerExecutor",
        description = "executes the response transformers",
        interceptPoint = InterceptPoint.RESPONSE)
public class ResponseTransformerExecutor
        extends AbstractTransformersExecutor implements Interceptor {

    static final Logger LOGGER
            = LoggerFactory.getLogger(ResponseTransformerExecutor.class);

    /**
     * Creates a new instance of ResponseTransformerMetadataHandler
     *
     * @param pluginsRegistry
     */
    @InjectPluginsRegistry
    public ResponseTransformerExecutor(PluginsRegistry pluginsRegistry) {
        super(pluginsRegistry);
    }

    @Override
    boolean doesGlobalTransformerAppy(GlobalTransformer gt,
            HttpServerExchange exchange,
            RequestContext context) {
        return gt.getPhase() == PHASE.RESPONSE
                && gt.resolve(exchange, context);
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
                        .containsKey(TransformerMetadata.RTS_ELEMENT_NAME));
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
                        .containsKey(TransformerMetadata.RTS_ELEMENT_NAME));
    }

    @Override
    void applyGlobalTransformers(HttpServerExchange exchange) {
        var context = RequestContext.wrap(exchange);

        // execture global response tranformers
        pluginsRegistry.getGlobalTransformers().stream()
                .filter(gt -> doesGlobalTransformerAppy(gt, exchange, context))
                .forEachOrdered(gt -> {
                    if (gt.getScope() == SCOPE.THIS) {
                        gt.transform(
                                exchange,
                                context,
                                context.getResponseContent());
                    } else if (context.getResponseContent() != null
                            && context.getResponseContent().isDocument()
                            && context.getResponseContent()
                                    .asDocument()
                                    .containsKey("_embedded")) {
                        applyChildrenTransformLogic(exchange,
                                context,
                                gt.getTransformer(),
                                gt.getArgs(),
                                gt.getConfArgs());
                    } else if (context.isDocument()) {
                        gt.transform(
                                exchange,
                                context,
                                context.getResponseContent());
                    }
                });
    }

    @Override
    void applyTransformLogic(
            HttpServerExchange exchange,
            List<TransformerMetadata> rts)
            throws InvalidMetadataException {
        var context = RequestContext.wrap(exchange);

        // execute request transformers
        rts.stream()
                .filter(rt -> rt.getPhase() == PHASE.RESPONSE)
                .forEachOrdered(rt -> {
                    try {
                        var _tr = pluginsRegistry
                                .getTransformers()
                                .stream()
                                .filter(t -> rt.getName().equals(t.getName()))
                                .findFirst();

                        if (_tr.isPresent()) {
                            var tr = _tr.get();
                            var t = tr.getInstance();
                            var confArgs = JsonUtils.toBsonDocument(tr.getConfArgs());

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
                        } else {
                            LOGGER.warn("Response Transformer set to apply "
                                    + "but not registered: {}", rt.getName());
                        }
                    } catch (NoSuchElementException iae) {
                        LOGGER.warn(iae.getMessage());
                        context.addWarning(iae.getMessage());
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

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return true;
    }
}
