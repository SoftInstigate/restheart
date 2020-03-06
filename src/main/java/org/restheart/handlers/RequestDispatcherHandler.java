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
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import java.util.HashMap;
import java.util.Map;
import org.restheart.handlers.aggregation.AggregationTransformer;
import org.restheart.handlers.aggregation.GetAggregationHandler;
import org.restheart.handlers.bulk.BulkDeleteDocumentsHandler;
import org.restheart.handlers.bulk.BulkPatchDocumentsHandler;
import org.restheart.handlers.bulk.BulkPostCollectionHandler;
import org.restheart.handlers.collection.DeleteCollectionHandler;
import org.restheart.handlers.collection.GetCollectionHandler;
import org.restheart.handlers.collection.PatchCollectionHandler;
import org.restheart.handlers.collection.PostCollectionHandler;
import org.restheart.handlers.collection.PutCollectionHandler;
import org.restheart.handlers.database.DeleteDBHandler;
import org.restheart.handlers.database.GetDBHandler;
import org.restheart.handlers.database.PatchDBHandler;
import org.restheart.handlers.database.PutDBHandler;
import org.restheart.handlers.document.DeleteDocumentHandler;
import org.restheart.handlers.document.GetDocumentHandler;
import org.restheart.handlers.document.PatchDocumentHandler;
import org.restheart.handlers.document.PutDocumentHandler;
import org.restheart.handlers.exchange.AbstractExchange.METHOD;
import org.restheart.handlers.exchange.BsonRequest;
import org.restheart.handlers.exchange.ExchangeKeys.TYPE;
import org.restheart.handlers.files.DeleteBucketHandler;
import org.restheart.handlers.files.DeleteFileHandler;
import org.restheart.handlers.files.FileMetadataHandler;
import org.restheart.handlers.files.GetFileBinaryHandler;
import org.restheart.handlers.files.GetFileHandler;
import org.restheart.handlers.files.PostBucketHandler;
import org.restheart.handlers.files.PutBucketHandler;
import org.restheart.handlers.files.PutFileHandler;
import org.restheart.handlers.indexes.DeleteIndexHandler;
import org.restheart.handlers.indexes.GetIndexesHandler;
import org.restheart.handlers.indexes.PutIndexHandler;
import org.restheart.handlers.metadata.AfterWriteCheckHandler;
import org.restheart.handlers.metadata.BeforeWriteCheckHandler;
import org.restheart.handlers.metadata.CheckersListHandler;
import org.restheart.handlers.metadata.HookHandler;
import org.restheart.handlers.metadata.RequestTransformerHandler;
import org.restheart.handlers.metadata.ResponseTransformerHandler;
import org.restheart.handlers.metadata.TransformersListHandler;
import org.restheart.handlers.metrics.MetricsHandler;
import org.restheart.handlers.root.GetRootHandler;
import org.restheart.handlers.schema.JsonMetaSchemaChecker;
import org.restheart.handlers.schema.JsonSchemaTransformer;
import org.restheart.handlers.sessions.PostSessionHandler;
import org.restheart.handlers.transformers.MetaRequestTransformer;
import org.restheart.handlers.transformers.RepresentationTransformer;
import org.restheart.handlers.transformers.SizeRequestTransformer;
import org.restheart.metadata.TransformerMetadata.PHASE;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
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
            = new ResponseTransformerHandler(
                    new TransformersListHandler(
                            new HookHandler(
                                    new ResponseSenderHandler()),
                            PHASE.RESPONSE,
                            new RepresentationTransformer()));

    /**
     *
     * @return
     */
    public static RequestDispatcherHandler getInstance() {
        return RequestDispatcherHandlerHolder.INSTANCE;
    }

    private final Map<TYPE, Map<METHOD, PipelinedHandler>> handlersMultimap;
    private final ResponseSenderHandler responseSenderHandler
            = new ResponseSenderHandler(null);

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
        LOGGER.trace("putPipedHttpHandler( {}, {}, {} )", type, method, getHandlerToLog(handler).getClass().getCanonicalName());
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
                new RequestTransformerHandler(
                        new GetRootHandler(
                                new TransformersListHandler(
                                        new ResponseSenderHandler(null),
                                        PHASE.RESPONSE,
                                        new RepresentationTransformer()))));

        putHandler(TYPE.ROOT_SIZE, METHOD.GET,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new GetRootHandler(
                                        new TransformersListHandler(
                                                new ResponseSenderHandler(null),
                                                PHASE.RESPONSE,
                                                new SizeRequestTransformer())),
                                PHASE.REQUEST,
                                new SizeRequestTransformer()))
        );

        // *** DB handlers
        putHandler(TYPE.DB, METHOD.GET,
                new RequestTransformerHandler(
                        new GetDBHandler(
                                new TransformersListHandler(
                                        new ResponseTransformerHandler(
                                                new ResponseSenderHandler(null)),
                                        PHASE.RESPONSE,
                                        new AggregationTransformer(),
                                        new RepresentationTransformer()))));

        putHandler(TYPE.DB_SIZE, METHOD.GET,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new GetDBHandler(
                                        new TransformersListHandler(
                                                new ResponseSenderHandler(null),
                                                PHASE.RESPONSE,
                                                new SizeRequestTransformer())),
                                PHASE.REQUEST,
                                new SizeRequestTransformer()))
        );

        putHandler(TYPE.DB_META, METHOD.GET,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new GetDocumentHandler(
                                        new TransformersListHandler(
                                                new ResponseSenderHandler(null),
                                                PHASE.RESPONSE,
                                                new AggregationTransformer(),
                                                new MetaRequestTransformer())),
                                PHASE.REQUEST,
                                new MetaRequestTransformer()))
        );

        putHandler(TYPE.DB, METHOD.PUT,
                new RequestTransformerHandler(
                        new RequestTransformerHandler(
                                new PutDBHandler(
                                        DEFAULT_RESP_TRANFORMERS))));

        putHandler(TYPE.DB, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteDBHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.DB, METHOD.PATCH,
                new RequestTransformerHandler(
                        new PatchDBHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // *** COLLECTION handlers
        putHandler(TYPE.COLLECTION, METHOD.GET,
                new RequestTransformerHandler(
                        new GetCollectionHandler(
                                new ResponseTransformerHandler(
                                        new TransformersListHandler(
                                                new HookHandler(
                                                        new ResponseSenderHandler()),
                                                PHASE.RESPONSE,
                                                new RepresentationTransformer(),
                                                new AggregationTransformer())))));

        putHandler(TYPE.COLLECTION_SIZE, METHOD.GET,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new GetCollectionHandler(
                                        new ResponseTransformerHandler(
                                                new TransformersListHandler(
                                                        new HookHandler(
                                                                new ResponseSenderHandler()),
                                                        PHASE.RESPONSE,
                                                        new SizeRequestTransformer()))),
                                PHASE.REQUEST,
                                new SizeRequestTransformer()))
        );

        putHandler(TYPE.COLLECTION_META, METHOD.GET,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new GetDocumentHandler(
                                        new TransformersListHandler(
                                                new ResponseSenderHandler(null),
                                                PHASE.RESPONSE,
                                                new AggregationTransformer(),
                                                new MetaRequestTransformer())),
                                PHASE.REQUEST,
                                new MetaRequestTransformer()))
        );

        putHandler(TYPE.COLLECTION, METHOD.POST,
                new NormalOrBulkDispatcherHandler(
                        new RequestTransformerHandler(
                                new BeforeWriteCheckHandler(
                                        new PostCollectionHandler(
                                                new AfterWriteCheckHandler(
                                                        DEFAULT_RESP_TRANFORMERS)))),
                        new RequestTransformerHandler(
                                new BeforeWriteCheckHandler(
                                        new BulkPostCollectionHandler(
                                                DEFAULT_RESP_TRANFORMERS)))));

        putHandler(TYPE.COLLECTION, METHOD.PUT,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new PutCollectionHandler(
                                        DEFAULT_RESP_TRANFORMERS),
                                PHASE.REQUEST,
                                new AggregationTransformer())));

        putHandler(TYPE.COLLECTION, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.COLLECTION, METHOD.PATCH,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new PatchCollectionHandler(
                                        DEFAULT_RESP_TRANFORMERS),
                                PHASE.REQUEST,
                                new AggregationTransformer())));

        // *** DOCUMENT handlers
        putHandler(TYPE.DOCUMENT, METHOD.GET,
                new RequestTransformerHandler(
                        new GetDocumentHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.DOCUMENT, METHOD.PUT,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new PutDocumentHandler(
                                        new AfterWriteCheckHandler(
                                                DEFAULT_RESP_TRANFORMERS)))));

        putHandler(TYPE.DOCUMENT, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteDocumentHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.DOCUMENT, METHOD.PATCH,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new PatchDocumentHandler(
                                        new AfterWriteCheckHandler(
                                                DEFAULT_RESP_TRANFORMERS)))));

        // *** BULK_DOCUMENTS handlers, i.e. bulk operations
        putHandler(TYPE.BULK_DOCUMENTS, METHOD.DELETE,
                new RequestTransformerHandler(
                        new BulkDeleteDocumentsHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.BULK_DOCUMENTS, METHOD.PATCH,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new BulkPatchDocumentsHandler(
                                        DEFAULT_RESP_TRANFORMERS))));

        // *** COLLECTION_INDEXES handlers
        putHandler(TYPE.COLLECTION_INDEXES, METHOD.GET,
                new RequestTransformerHandler(
                        new GetIndexesHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // *** INDEX handlers
        putHandler(TYPE.INDEX, METHOD.PUT,
                new RequestTransformerHandler(
                        new PutIndexHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.INDEX, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteIndexHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // *** FILES_BUCKET and FILE handlers
        putHandler(TYPE.FILES_BUCKET, METHOD.GET,
                new RequestTransformerHandler(
                        new GetCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.FILES_BUCKET, METHOD.GET,
                new RequestTransformerHandler(
                        new GetCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.FILES_BUCKET_SIZE, METHOD.GET,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new GetCollectionHandler(
                                        new TransformersListHandler(
                                                new ResponseSenderHandler(null),
                                                PHASE.RESPONSE,
                                                new SizeRequestTransformer())),
                                PHASE.REQUEST,
                                new SizeRequestTransformer()))
        );

        putHandler(TYPE.FILES_BUCKET_META, METHOD.GET,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new GetDocumentHandler(
                                        new TransformersListHandler(
                                                new ResponseSenderHandler(null),
                                                PHASE.RESPONSE,
                                                new MetaRequestTransformer())),
                                PHASE.REQUEST,
                                new MetaRequestTransformer()))
        );

        putHandler(TYPE.FILES_BUCKET, METHOD.POST,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new PostBucketHandler(
                                        DEFAULT_RESP_TRANFORMERS))));

        putHandler(TYPE.FILE, METHOD.PUT,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new PutFileHandler(
                                        new FileMetadataHandler(
                                                DEFAULT_RESP_TRANFORMERS)))));

        putHandler(TYPE.FILES_BUCKET, METHOD.PUT,
                new RequestTransformerHandler(
                        new PutBucketHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.FILES_BUCKET, METHOD.PATCH,
                new RequestTransformerHandler(
                        new PatchCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.FILES_BUCKET, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteBucketHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.FILE, METHOD.GET,
                new RequestTransformerHandler(
                        new GetFileHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.FILE_BINARY, METHOD.GET,
                new GetFileBinaryHandler(
                        new HookHandler(
                                new ResponseSenderHandler())));

        putHandler(TYPE.FILE, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteFileHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // PUTting or PATCHing a file involves updating the metadata in the
        // xxx.files bucket for an _id. Although the chunks are immutable we
        // can treat the metadata like a regular document.
        putHandler(TYPE.FILE, METHOD.PATCH,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new FileMetadataHandler(
                                        new AfterWriteCheckHandler(
                                                DEFAULT_RESP_TRANFORMERS)))));

        /*
         * TODO There's already a PUT handler that allows custom id's to be set. Need to think about how to handle PUT with no binary data
        putPipedHttpHandler(TYPE.FILE, METHOD.PUT,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new FileMetadataHandler(
                                        new AfterWriteCheckHandler(
                                                RESP_TRANFORMERS)))));
         */
        // *** AGGREGATION handler
        putHandler(TYPE.AGGREGATION, METHOD.GET,
                new RequestTransformerHandler(
                        new GetAggregationHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // *** Sessions handlers
        putHandler(TYPE.SESSIONS, METHOD.POST,
                new RequestTransformerHandler(
                        new PostSessionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // *** SCHEMA handlers
        putHandler(TYPE.SCHEMA_STORE, METHOD.GET,
                new RequestTransformerHandler(
                        new GetCollectionHandler(
                                new TransformersListHandler(
                                        DEFAULT_RESP_TRANFORMERS,
                                        PHASE.RESPONSE,
                                        new JsonSchemaTransformer()))));

        putHandler(TYPE.SCHEMA_STORE_SIZE, METHOD.GET,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new GetCollectionHandler(
                                        new TransformersListHandler(
                                                new ResponseSenderHandler(null),
                                                PHASE.RESPONSE,
                                                new SizeRequestTransformer())),
                                PHASE.REQUEST,
                                new SizeRequestTransformer()))
        );

        putHandler(TYPE.SCHEMA_STORE_META, METHOD.GET,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new GetDocumentHandler(
                                        new TransformersListHandler(
                                                new ResponseSenderHandler(null),
                                                PHASE.RESPONSE,
                                                new MetaRequestTransformer())),
                                PHASE.REQUEST,
                                new MetaRequestTransformer()))
        );

        putHandler(TYPE.SCHEMA_STORE, METHOD.PUT,
                new RequestTransformerHandler(
                        new PutCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.SCHEMA_STORE, METHOD.PATCH,
                new RequestTransformerHandler(
                        new PatchCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.SCHEMA_STORE, METHOD.POST,
                new RequestTransformerHandler(
                        new CheckersListHandler(
                                new TransformersListHandler(
                                        new PostCollectionHandler(
                                                DEFAULT_RESP_TRANFORMERS),
                                        PHASE.REQUEST,
                                        new JsonSchemaTransformer()),
                                new JsonMetaSchemaChecker())));

        putHandler(TYPE.SCHEMA_STORE, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.SCHEMA, METHOD.GET,
                new RequestTransformerHandler(
                        new GetDocumentHandler(
                                new TransformersListHandler(
                                        DEFAULT_RESP_TRANFORMERS,
                                        PHASE.RESPONSE,
                                        new JsonSchemaTransformer()))));

        putHandler(TYPE.SCHEMA, METHOD.PUT,
                new RequestTransformerHandler(
                        new CheckersListHandler(
                                new TransformersListHandler(
                                        new PutDocumentHandler(
                                                DEFAULT_RESP_TRANFORMERS),
                                        PHASE.REQUEST,
                                        new JsonSchemaTransformer()),
                                new JsonMetaSchemaChecker())));

        putHandler(TYPE.SCHEMA, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteDocumentHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putHandler(TYPE.METRICS, METHOD.GET, new MetricsHandler(DEFAULT_RESP_TRANFORMERS));
    }

    private PipelinedHandler getHandlerToLog(PipelinedHandler handler) {
        if (handler instanceof BeforeWriteCheckHandler
                || handler instanceof RequestTransformerHandler
                || handler instanceof CheckersListHandler
                || handler instanceof TransformersListHandler) {
            return getHandlerToLog(handler.getNext());
        } else {
            return handler;
        }
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
