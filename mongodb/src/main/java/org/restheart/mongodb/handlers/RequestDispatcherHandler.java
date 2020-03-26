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
package org.restheart.mongodb.handlers;

import io.undertow.server.HttpServerExchange;
import java.util.HashMap;
import java.util.Map;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.AbstractExchange.METHOD;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.ExchangeKeys.TYPE;
import org.restheart.mongodb.handlers.aggregation.AggregationTransformer;
import org.restheart.mongodb.handlers.aggregation.GetAggregationHandler;
import org.restheart.mongodb.handlers.bulk.BulkDeleteDocumentsHandler;
import org.restheart.mongodb.handlers.bulk.BulkPatchDocumentsHandler;
import org.restheart.mongodb.handlers.bulk.BulkPostCollectionHandler;
import org.restheart.mongodb.handlers.collection.DeleteCollectionHandler;
import org.restheart.mongodb.handlers.collection.GetCollectionHandler;
import org.restheart.mongodb.handlers.collection.PatchCollectionHandler;
import org.restheart.mongodb.handlers.collection.PostCollectionHandler;
import org.restheart.mongodb.handlers.collection.PutCollectionHandler;
import org.restheart.mongodb.handlers.database.DeleteDBHandler;
import org.restheart.mongodb.handlers.database.GetDBHandler;
import org.restheart.mongodb.handlers.database.PatchDBHandler;
import org.restheart.mongodb.handlers.database.PutDBHandler;
import org.restheart.mongodb.handlers.document.DeleteDocumentHandler;
import org.restheart.mongodb.handlers.document.GetDocumentHandler;
import org.restheart.mongodb.handlers.document.PatchDocumentHandler;
import org.restheart.mongodb.handlers.document.PutDocumentHandler;
import org.restheart.mongodb.handlers.files.DeleteBucketHandler;
import org.restheart.mongodb.handlers.files.DeleteFileHandler;
import org.restheart.mongodb.handlers.files.FileMetadataHandler;
import org.restheart.mongodb.handlers.files.GetFileBinaryHandler;
import org.restheart.mongodb.handlers.files.GetFileHandler;
import org.restheart.mongodb.handlers.files.PostBucketHandler;
import org.restheart.mongodb.handlers.files.PutBucketHandler;
import org.restheart.mongodb.handlers.files.PutFileHandler;
import org.restheart.mongodb.handlers.indexes.DeleteIndexHandler;
import org.restheart.mongodb.handlers.indexes.GetIndexesHandler;
import org.restheart.mongodb.handlers.indexes.PutIndexHandler;
import org.restheart.mongodb.handlers.injectors.ResponseContentInjector;
import org.restheart.mongodb.handlers.metadata.AfterWriteCheckersExecutor;
import org.restheart.mongodb.handlers.metadata.BeforeWriteCheckersExecutor;
import org.restheart.mongodb.handlers.metadata.CheckersListHandler;
import org.restheart.mongodb.handlers.metadata.RequestTransformersExecutor;
import org.restheart.mongodb.handlers.metadata.ResponseTransformersExecutor;
import org.restheart.mongodb.handlers.metadata.TransformersListHandler;
import org.restheart.mongodb.handlers.metrics.MetricsHandler;
import org.restheart.mongodb.handlers.root.GetRootHandler;
import org.restheart.mongodb.handlers.schema.JsonMetaSchemaChecker;
import org.restheart.mongodb.handlers.schema.JsonSchemaTransformer;
import org.restheart.mongodb.handlers.sessions.PostSessionHandler;
import org.restheart.mongodb.handlers.transformers.MetaRequestTransformer;
import org.restheart.mongodb.handlers.transformers.RepresentationTransformer;
import org.restheart.mongodb.handlers.transformers.SizeRequestTransformer;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.plugins.mongodb.Transformer.PHASE;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestDispatcherHandler extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestDispatcherHandler.class);

    /**
     * the default response tranformer handlers chain
     */
    public static PipelinedHandler DEFAULT_RESP_TRANFORMERS
            = PipelinedHandler.pipe(
                    new ResponseTransformersExecutor(),
                    new RepresentationTransformer(),
                    new ResponseContentInjector());

    /**
     *
     * @return
     */
    public static RequestDispatcherHandler getInstance() {
        return RequestDispatcherHandlerHolder.INSTANCE;
    }

    private final Map<TYPE, Map<METHOD, PipelinedHandler>> handlersMultimap;
    private final ResponseContentInjector responseSenderHandler
            = new ResponseContentInjector(null);

    /**
     * Creates a new instance of RequestDispacherHandler
     */
    private RequestDispatcherHandler() {
        this(true);
    }

    /**
     * Used for testing. By passing a <code>false</code> parameter then handlers
     * are not initialized and you can put your own (e.g. mocks)
     *
     * @param initialize if false then do not initialize the handlersMultimap
     */
    RequestDispatcherHandler(boolean initialize) {
        super(null);
        this.handlersMultimap = new HashMap<>();
        if (initialize) {
            defaultInit();
        }
    }

    /**
     * Handle the request, delegating to the proper PipelinedHandler
     *
     * @param exchange the HttpServerExchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);

        if (request.getType() == TYPE.INVALID) {
            LOGGER.debug(
                    "This is a bad request: returning a <{}> HTTP code",
                    HttpStatus.SC_BAD_REQUEST);
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_BAD_REQUEST,
                    "bad request");
            responseSenderHandler.handleRequest(exchange);
            return;
        }

        if (request.getMethod() == METHOD.OTHER) {
            LOGGER.debug(
                    "This method is not allowed: returning a <{}> HTTP code",
                    HttpStatus.SC_METHOD_NOT_ALLOWED);
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_METHOD_NOT_ALLOWED,
                    "method " + request.getMethod().name() + " not allowed");
            responseSenderHandler.handleRequest(exchange);
            return;
        }

        if (request.isReservedResource()) {
            LOGGER.debug(
                    "The resource is reserved: returning a <{}> HTTP code",
                    HttpStatus.SC_FORBIDDEN);
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_FORBIDDEN,
                    "reserved resource");
            responseSenderHandler.handleRequest(exchange);
            return;
        }

        final PipelinedHandler httpHandler
                = getPipedHttpHandler(request.getType(), request.getMethod());

        if (httpHandler != null) {
            before(exchange);
            httpHandler.handleRequest(exchange);
            after(exchange);
        } else {
            LOGGER.error(
                    "Can't find PipelinedHandler({}, {})",
                    request.getType(), request.getMethod());
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    HttpStatus.SC_METHOD_NOT_ALLOWED,
                    "method " + request.getMethod().name() + " not allowed");
            responseSenderHandler.handleRequest(exchange);
        }
    }

    /**
     * Given a type and method, return the appropriate PipelinedHandler which
     * can handle this request
     *
     * @param type
     * @param method
     * @return the PipelinedHandler
     */
    public PipelinedHandler getPipedHttpHandler(TYPE type, METHOD method) {
        Map<METHOD, PipelinedHandler> methodsMap = handlersMultimap.get(type);
        return methodsMap != null ? methodsMap.get(method) : null;
    }

    /**
     * Given a type and method, put in a PipelinedHandler
     *
     * @param type the DB type
     * @param method the HTTP method
     * @param handler the PipelinedHandler
     */
    public void putHandler(TYPE type, METHOD method, PipelinedHandler handler) {
        Map<METHOD, PipelinedHandler> methodsMap = handlersMultimap.get(type);
        if (methodsMap == null) {
            methodsMap = new HashMap<>();
            handlersMultimap.put(type, methodsMap);
        }
        methodsMap.put(method, handler);
    }

    /**
     * Put into handlersMultimap all the default combinations of types, methods
     * and PipelinedHandler objects
     */
    private void defaultInit() {
        LOGGER.trace("Initialize default HTTP handlers:");

        // *** ROOT handlers
        putHandler(TYPE.ROOT, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetRootHandler(),
                        new RepresentationTransformer(),
                        new ResponseContentInjector()));

        putHandler(TYPE.ROOT_SIZE, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new SizeRequestTransformer(true),
                        new GetRootHandler(),
                        new SizeRequestTransformer(false),
                        new ResponseContentInjector()));

        // *** DB handlers
        putHandler(TYPE.DB, METHOD.GET,
                PipelinedHandler.pipe(new RequestTransformersExecutor(),
                        new GetDBHandler(),
                        new AggregationTransformer(false),
                        new RepresentationTransformer(),
                        new ResponseTransformersExecutor(),
                        new ResponseContentInjector()
                ));

        putHandler(TYPE.DB_SIZE, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new SizeRequestTransformer(true),
                        new GetDBHandler(),
                        new SizeRequestTransformer(false),
                        new ResponseContentInjector()));

        putHandler(TYPE.DB_META, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetDocumentHandler(),
                        new AggregationTransformer(false),
                        new MetaRequestTransformer(),
                        new ResponseContentInjector()));

        putHandler(TYPE.DB, METHOD.PUT,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new PutDBHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.DB, METHOD.DELETE,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new DeleteDBHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.DB, METHOD.PATCH,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new PatchDBHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        // *** COLLECTION handlers
        putHandler(TYPE.COLLECTION, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetCollectionHandler(),
                        new RepresentationTransformer(),
                        new AggregationTransformer(false),
                        new ResponseContentInjector()
                ));

        putHandler(TYPE.COLLECTION_SIZE, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new SizeRequestTransformer(true),
                        new GetCollectionHandler(),
                        new SizeRequestTransformer(false),
                        new ResponseContentInjector()));

        putHandler(TYPE.COLLECTION_META, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetDocumentHandler(),
                        new AggregationTransformer(false),
                        new MetaRequestTransformer(),
                        new ResponseContentInjector()
                ));

        putHandler(TYPE.COLLECTION, METHOD.POST,
                new NormalOrBulkDispatcherHandler(
                        PipelinedHandler.pipe(
                                new RequestTransformersExecutor(),
                                new BeforeWriteCheckersExecutor(),
                                new PostCollectionHandler(),
                                new AfterWriteCheckersExecutor(),
                                DEFAULT_RESP_TRANFORMERS
                        ),
                        PipelinedHandler.pipe(
                                new RequestTransformersExecutor(),
                                new BeforeWriteCheckersExecutor(),
                                new BulkPostCollectionHandler(),
                                DEFAULT_RESP_TRANFORMERS)
                ));

        putHandler(TYPE.COLLECTION, METHOD.PUT,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new AggregationTransformer(true),
                        new PutCollectionHandler(),
                        DEFAULT_RESP_TRANFORMERS));

        putHandler(TYPE.COLLECTION, METHOD.DELETE,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new DeleteCollectionHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.COLLECTION, METHOD.PATCH,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new AggregationTransformer(true),
                        new PatchCollectionHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        // *** DOCUMENT handlers
        putHandler(TYPE.DOCUMENT, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetDocumentHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.DOCUMENT, METHOD.PUT,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new BeforeWriteCheckersExecutor(),
                        new PutDocumentHandler(),
                        new AfterWriteCheckersExecutor(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.DOCUMENT, METHOD.DELETE,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new DeleteDocumentHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.DOCUMENT, METHOD.PATCH,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new BeforeWriteCheckersExecutor(),
                        new PatchDocumentHandler(),
                        new AfterWriteCheckersExecutor(),
                        DEFAULT_RESP_TRANFORMERS));

        // *** BULK_DOCUMENTS handlers, i.e. bulk operations
        putHandler(TYPE.BULK_DOCUMENTS, METHOD.DELETE,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new BulkDeleteDocumentsHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.BULK_DOCUMENTS, METHOD.PATCH,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new BeforeWriteCheckersExecutor(),
                        new BulkPatchDocumentsHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        // *** COLLECTION_INDEXES handlers
        putHandler(TYPE.COLLECTION_INDEXES, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetIndexesHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        // *** INDEX handlers
        putHandler(TYPE.INDEX, METHOD.PUT,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new PutIndexHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.INDEX, METHOD.DELETE,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new DeleteIndexHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        // *** FILES_BUCKET and FILE handlers
        putHandler(TYPE.FILES_BUCKET, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetCollectionHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.FILES_BUCKET, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetCollectionHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.FILES_BUCKET_SIZE, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new SizeRequestTransformer(true),
                        new GetCollectionHandler(),
                        new SizeRequestTransformer(false),
                        new ResponseContentInjector()));

        putHandler(TYPE.FILES_BUCKET_META, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetDocumentHandler(),
                        new MetaRequestTransformer(),
                        new ResponseContentInjector()
                ));

        putHandler(TYPE.FILES_BUCKET, METHOD.POST,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new BeforeWriteCheckersExecutor(),
                        new PostBucketHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.FILE, METHOD.PUT,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new BeforeWriteCheckersExecutor(),
                        new PutFileHandler(),
                        new FileMetadataHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.FILES_BUCKET, METHOD.PUT,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new PutBucketHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.FILES_BUCKET, METHOD.PATCH,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new PatchCollectionHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.FILES_BUCKET, METHOD.DELETE,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new DeleteBucketHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.FILE, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetFileHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.FILE_BINARY, METHOD.GET,
                PipelinedHandler.pipe(new GetFileBinaryHandler(),
                        new ResponseContentInjector()
                ));

        putHandler(TYPE.FILE, METHOD.DELETE,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new DeleteFileHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        // PUTting or PATCHing a file involves updating the metadata in the
        // xxx.files bucket for an _id. Although the chunks are immutable we
        // can treat the metadata like a regular document.
        putHandler(TYPE.FILE, METHOD.PATCH,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new BeforeWriteCheckersExecutor(),
                        new FileMetadataHandler(),
                        new AfterWriteCheckersExecutor(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        // *** AGGREGATION handler
        putHandler(TYPE.AGGREGATION, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetAggregationHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        // *** Sessions handlers
        putHandler(TYPE.SESSIONS, METHOD.POST,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new PostSessionHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        // *** SCHEMA handlers
        putHandler(TYPE.SCHEMA_STORE, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetCollectionHandler(),
                        new TransformersListHandler(null, PHASE.RESPONSE,
                                new JsonSchemaTransformer()),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.SCHEMA_STORE_SIZE, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new SizeRequestTransformer(true),
                        new GetCollectionHandler(),
                        new SizeRequestTransformer(false),
                        new ResponseContentInjector()));

        putHandler(TYPE.SCHEMA_STORE_META, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetDocumentHandler(),
                        new MetaRequestTransformer(),
                        new ResponseContentInjector()
                ));

        putHandler(TYPE.SCHEMA_STORE, METHOD.PUT,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new PutCollectionHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.SCHEMA_STORE, METHOD.PATCH,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new PatchCollectionHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.SCHEMA_STORE, METHOD.POST,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new CheckersListHandler(new JsonMetaSchemaChecker()),
                        new TransformersListHandler(PHASE.REQUEST,
                                new JsonSchemaTransformer()),
                        new PostCollectionHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.SCHEMA_STORE, METHOD.DELETE,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new DeleteCollectionHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.SCHEMA, METHOD.GET,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new GetDocumentHandler(),
                        new TransformersListHandler(PHASE.RESPONSE,
                                new JsonSchemaTransformer()),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.SCHEMA, METHOD.PUT,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new CheckersListHandler(new JsonMetaSchemaChecker()),
                        new TransformersListHandler(PHASE.REQUEST,
                                new JsonSchemaTransformer()),
                        new PutDocumentHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.SCHEMA, METHOD.DELETE,
                PipelinedHandler.pipe(
                        new RequestTransformersExecutor(),
                        new DeleteDocumentHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));

        putHandler(TYPE.METRICS, METHOD.GET,
                PipelinedHandler.pipe(new MetricsHandler(),
                        DEFAULT_RESP_TRANFORMERS
                ));
    }

    /**
     * Code to execute before each handleRequest
     *
     * @param exchange the HttpServerExchange
     */
    void before(HttpServerExchange exchange) {
    }

    /**
     * code to execute after each handleRequest
     *
     * @param exchange the HttpServerExchange
     */
    void after(HttpServerExchange exchange) {
    }

    private static class RequestDispatcherHandlerHolder {
        private static final RequestDispatcherHandler INSTANCE
                = new RequestDispatcherHandler();

        private RequestDispatcherHandlerHolder() {
        }
    }
}
