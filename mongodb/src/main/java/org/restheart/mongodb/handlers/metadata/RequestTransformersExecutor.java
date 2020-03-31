/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.handlers.metadata;

import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.BsonResponse;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.mongodb.metadata.TransformerMetadata;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mongodb.GlobalTransformer;
import org.restheart.plugins.mongodb.Transformer.PHASE;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the request transformers defined in the collection properties to the
 * request; it also applies the global tranformers
 *
 * It implements Initializer only to be able get pluginsRegistry via
 * InjectPluginsRegistry annotation
 *
 * It is added to the pipeline by RequestDispatcherHandler
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "requestTransformersExecutor",
        description = "executes the request transformers")
public class RequestTransformersExecutor
        extends AbstractTransformersExecutor implements Initializer {

    static final Logger LOGGER
            = LoggerFactory.getLogger(RequestTransformersExecutor.class);

    @InjectPluginsRegistry
    public void setPluginsRegistry(PluginsRegistry pluginsRegistry) {
        AbstractTransformersExecutor.pluginsRegistry = pluginsRegistry;
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
    void applyGlobalTransformers(HttpServerExchange exchange) {
        var request = BsonRequest.wrap(exchange);
        var context = RequestContext.wrap(exchange);

        // execute global request tranformers
        pluginsRegistry.getGlobalTransformers().stream()
                .filter(gt -> doesGlobalTransformerAppy(gt, exchange, context))
                .forEachOrdered(gt -> {
                    if (request.getContent() == null
                            || request.getContent().isDocument()) {
                        gt.transform(
                                exchange,
                                context,
                                request.getContent());
                    } else if (request.getContent().isArray()) {
                        request.getContent().asArray().forEach(doc -> {
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
            List<TransformerMetadata> rts)
            throws InvalidMetadataException {
        var request = BsonRequest.wrap(exchange);
        var response = BsonResponse.wrap(exchange);
        var context = RequestContext.wrap(exchange);

        // execute request tranformers
        rts.stream().filter((rt)
                -> (rt.getPhase() == PHASE.REQUEST))
                .forEachOrdered((TransformerMetadata rt) -> {
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

                            BsonValue requestContent = request.getContent() == null
                                    ? new BsonDocument()
                                    : request.getContent();

                            if (requestContent.isDocument()) {
                                t.transform(
                                        exchange,
                                        context,
                                        requestContent,
                                        rt.getArgs(),
                                        confArgs);
                            } else if (request.isPost()
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
                        } else {
                            LOGGER.warn("Request Transformer set to apply "
                                    + "but not registered: {}", rt.getName());
                        }
                    } catch (IllegalArgumentException iae) {
                        String err = "Cannot find '"
                                + rt.getName()
                                + "' in singleton group 'transformers'";
                        LOGGER.warn(err);
                        response.addWarning(err);
                    } catch (Throwable t) {
                        String err = "Error executing transformer '"
                                + rt.getName()
                                + "': "
                                + t.getMessage();
                        LOGGER.warn(err);
                        response.addWarning(err);
                    }
                });
    }

    /**
     * does nothing, implements Initializer only to get pluginsRegistry
     */
    @Override
    public void init() {
    }
}
