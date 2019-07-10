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
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.handlers.RequestContext.TYPE;
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
public class RequestDispatcherHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestDispatcherHandler.class);

    /**
     * the default response tranformer handlers chain
     */
    public static PipedHttpHandler DEFAULT_RESP_TRANFORMERS
            = new ResponseTransformerHandler(
                    new TransformersListHandler(
                            new HookHandler(
                                    new ResponseSenderHandler()),
                            PHASE.RESPONSE,
                            new RepresentationTransformer()));

    public static RequestDispatcherHandler getInstance() {
        return RequestDispatcherHandlerHolder.INSTANCE;
    }

    private final Map<TYPE, Map<METHOD, PipedHttpHandler>> handlersMultimap;
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
        super(null, null);
        this.handlersMultimap = new HashMap<>();
        if (initialize) {
            defaultInit();
        }
    }

    /**
     * Handle the request, delegating to the proper PipedHttpHandler
     *
     * @param exchange the HttpServerExchange
     * @param context the RequestContext
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.getType() == TYPE.INVALID) {
            LOGGER.debug(
                    "This is a bad request: returning a <{}> HTTP code",
                    HttpStatus.SC_BAD_REQUEST);
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_BAD_REQUEST,
                    "bad request");
            responseSenderHandler.handleRequest(exchange, context);
            return;
        }

        if (context.getMethod() == METHOD.OTHER) {
            LOGGER.debug(
                    "This method is not allowed: returning a <{}> HTTP code",
                    HttpStatus.SC_METHOD_NOT_ALLOWED);
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_METHOD_NOT_ALLOWED,
                    "method " + context.getMethod().name() + " not allowed");
            responseSenderHandler.handleRequest(exchange, context);
            return;
        }

        if (context.isReservedResource()) {
            LOGGER.debug(
                    "The resource is reserved: returning a <{}> HTTP code",
                    HttpStatus.SC_FORBIDDEN);
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_FORBIDDEN,
                    "reserved resource");
            responseSenderHandler.handleRequest(exchange, context);
            return;
        }

        final PipedHttpHandler httpHandler
                = getPipedHttpHandler(context.getType(), context.getMethod());

        if (httpHandler != null) {
            before(exchange, context);
            httpHandler.handleRequest(exchange, context);
            after(exchange, context);
        } else {
            LOGGER.error(
                    "Can't find PipedHttpHandler({}, {})",
                    context.getType(), context.getMethod());
            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    context,
                    HttpStatus.SC_METHOD_NOT_ALLOWED,
                    "method " + context.getMethod().name() + " not allowed");
            responseSenderHandler.handleRequest(exchange, context);
        }
    }

    /**
     * Given a type and method, return the appropriate PipedHttpHandler which
     * can handle this request
     *
     * @param type
     * @param method
     * @return the PipedHttpHandler
     */
    public PipedHttpHandler getPipedHttpHandler(TYPE type, METHOD method) {
        Map<METHOD, PipedHttpHandler> methodsMap = handlersMultimap.get(type);
        return methodsMap != null ? methodsMap.get(method) : null;
    }

    /**
     * Given a type and method, put in a PipedHttpHandler
     *
     * @param type the DB type
     * @param method the HTTP method
     * @param handler the PipedHttpHandler
     */
    public void putPipedHttpHandler(TYPE type, METHOD method, PipedHttpHandler handler) {
        LOGGER.trace("putPipedHttpHandler( {}, {}, {} )", type, method, getHandlerToLog(handler).getClass().getCanonicalName());
        Map<METHOD, PipedHttpHandler> methodsMap = handlersMultimap.get(type);
        if (methodsMap == null) {
            methodsMap = new HashMap<>();
            handlersMultimap.put(type, methodsMap);
        }
        methodsMap.put(method, handler);
    }

    /**
     * Put into handlersMultimap all the default combinations of types, methods
     * and PipedHttpHandler objects
     */
    private void defaultInit() {
        LOGGER.trace("Initialize default HTTP handlers:");

        // *** ROOT handlers
        putPipedHttpHandler(TYPE.ROOT, METHOD.GET,
                new RequestTransformerHandler(
                        new GetRootHandler(
                                new TransformersListHandler(
                                        new ResponseSenderHandler(null),
                                        PHASE.RESPONSE,
                                        new RepresentationTransformer()))));

        putPipedHttpHandler(TYPE.ROOT_SIZE, METHOD.GET,
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
        putPipedHttpHandler(TYPE.DB, METHOD.GET,
                new RequestTransformerHandler(
                        new GetDBHandler(
                                new TransformersListHandler(
                                        new ResponseTransformerHandler(
                                                new ResponseSenderHandler(null)),
                                        PHASE.RESPONSE,
                                        new AggregationTransformer(),
                                        new RepresentationTransformer()))));

        putPipedHttpHandler(TYPE.DB_SIZE, METHOD.GET,
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

        putPipedHttpHandler(TYPE.DB_META, METHOD.GET,
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

        putPipedHttpHandler(TYPE.DB, METHOD.PUT,
                new RequestTransformerHandler(
                        new RequestTransformerHandler(
                                new PutDBHandler(
                                        DEFAULT_RESP_TRANFORMERS))));

        putPipedHttpHandler(TYPE.DB, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteDBHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.DB, METHOD.PATCH,
                new RequestTransformerHandler(
                        new PatchDBHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // *** COLLECTION handlers
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.GET,
                new RequestTransformerHandler(
                        new GetCollectionHandler(
                                new ResponseTransformerHandler(
                                        new TransformersListHandler(
                                                new HookHandler(
                                                        new ResponseSenderHandler()),
                                                PHASE.RESPONSE,
                                                new RepresentationTransformer(),
                                                new AggregationTransformer())))));

        putPipedHttpHandler(TYPE.COLLECTION_SIZE, METHOD.GET,
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

        putPipedHttpHandler(TYPE.COLLECTION_META, METHOD.GET,
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

        putPipedHttpHandler(TYPE.COLLECTION, METHOD.POST,
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

        putPipedHttpHandler(TYPE.COLLECTION, METHOD.PUT,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new PutCollectionHandler(
                                        DEFAULT_RESP_TRANFORMERS),
                                PHASE.REQUEST,
                                new AggregationTransformer())));

        putPipedHttpHandler(TYPE.COLLECTION, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.COLLECTION, METHOD.PATCH,
                new RequestTransformerHandler(
                        new TransformersListHandler(
                                new PatchCollectionHandler(
                                        DEFAULT_RESP_TRANFORMERS),
                                PHASE.REQUEST,
                                new AggregationTransformer())));

        // *** DOCUMENT handlers
        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.GET,
                new RequestTransformerHandler(
                        new GetDocumentHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.PUT,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new PutDocumentHandler(
                                        new AfterWriteCheckHandler(
                                                DEFAULT_RESP_TRANFORMERS)))));

        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteDocumentHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.PATCH,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new PatchDocumentHandler(
                                        new AfterWriteCheckHandler(
                                                DEFAULT_RESP_TRANFORMERS)))));

        // *** BULK_DOCUMENTS handlers, i.e. bulk operations
        putPipedHttpHandler(TYPE.BULK_DOCUMENTS, METHOD.DELETE,
                new RequestTransformerHandler(
                        new BulkDeleteDocumentsHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.BULK_DOCUMENTS, METHOD.PATCH,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new BulkPatchDocumentsHandler(
                                        DEFAULT_RESP_TRANFORMERS))));

        // *** COLLECTION_INDEXES handlers
        putPipedHttpHandler(TYPE.COLLECTION_INDEXES, METHOD.GET,
                new RequestTransformerHandler(
                        new GetIndexesHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // *** INDEX handlers
        putPipedHttpHandler(TYPE.INDEX, METHOD.PUT,
                new RequestTransformerHandler(
                        new PutIndexHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.INDEX, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteIndexHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // *** FILES_BUCKET and FILE handlers
        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.GET,
                new RequestTransformerHandler(
                        new GetCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.GET,
                new RequestTransformerHandler(
                        new GetCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.FILES_BUCKET_SIZE, METHOD.GET,
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

        putPipedHttpHandler(TYPE.FILES_BUCKET_META, METHOD.GET,
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

        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.POST,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new PostBucketHandler(
                                        DEFAULT_RESP_TRANFORMERS))));

        putPipedHttpHandler(TYPE.FILE, METHOD.PUT,
                new RequestTransformerHandler(
                        new BeforeWriteCheckHandler(
                                new PutFileHandler(
                                        new FileMetadataHandler(
                                                DEFAULT_RESP_TRANFORMERS)))));

        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.PUT,
                new RequestTransformerHandler(
                        new PutBucketHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.PATCH,
                new RequestTransformerHandler(
                        new PatchCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteBucketHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.FILE, METHOD.GET,
                new RequestTransformerHandler(
                        new GetFileHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.FILE_BINARY, METHOD.GET,
                new GetFileBinaryHandler(
                        new HookHandler(
                                new ResponseSenderHandler())));

        putPipedHttpHandler(TYPE.FILE, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteFileHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // PUTting or PATCHing a file involves updating the metadata in the
        // xxx.files bucket for an _id. Although the chunks are immutable we
        // can treat the metadata like a regular document.
        putPipedHttpHandler(TYPE.FILE, METHOD.PATCH,
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
        putPipedHttpHandler(TYPE.AGGREGATION, METHOD.GET,
                new RequestTransformerHandler(
                        new GetAggregationHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // *** Sessions handlers
        putPipedHttpHandler(TYPE.SESSIONS, METHOD.POST,
                new RequestTransformerHandler(
                        new PostSessionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        // *** SCHEMA handlers
        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.GET,
                new RequestTransformerHandler(
                        new GetCollectionHandler(
                                new TransformersListHandler(
                                        DEFAULT_RESP_TRANFORMERS,
                                        PHASE.RESPONSE,
                                        new JsonSchemaTransformer()))));

        putPipedHttpHandler(TYPE.SCHEMA_STORE_SIZE, METHOD.GET,
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

        putPipedHttpHandler(TYPE.SCHEMA_STORE_META, METHOD.GET,
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

        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.PUT,
                new RequestTransformerHandler(
                        new PutCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.PATCH,
                new RequestTransformerHandler(
                        new PatchCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.POST,
                new RequestTransformerHandler(
                        new CheckersListHandler(
                                new TransformersListHandler(
                                        new PostCollectionHandler(
                                                DEFAULT_RESP_TRANFORMERS),
                                        PHASE.REQUEST,
                                        new JsonSchemaTransformer()),
                                new JsonMetaSchemaChecker())));

        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteCollectionHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.SCHEMA, METHOD.GET,
                new RequestTransformerHandler(
                        new GetDocumentHandler(
                                new TransformersListHandler(
                                        DEFAULT_RESP_TRANFORMERS,
                                        PHASE.RESPONSE,
                                        new JsonSchemaTransformer()))));

        putPipedHttpHandler(TYPE.SCHEMA, METHOD.PUT,
                new RequestTransformerHandler(
                        new CheckersListHandler(
                                new TransformersListHandler(
                                        new PutDocumentHandler(
                                                DEFAULT_RESP_TRANFORMERS),
                                        PHASE.REQUEST,
                                        new JsonSchemaTransformer()),
                                new JsonMetaSchemaChecker())));

        putPipedHttpHandler(TYPE.SCHEMA, METHOD.DELETE,
                new RequestTransformerHandler(
                        new DeleteDocumentHandler(
                                DEFAULT_RESP_TRANFORMERS)));

        putPipedHttpHandler(TYPE.METRICS, METHOD.GET, new MetricsHandler(DEFAULT_RESP_TRANFORMERS));
    }

    private PipedHttpHandler getHandlerToLog(PipedHttpHandler handler) {
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
     * @param context the RequestContext
     */
    void before(HttpServerExchange exchange, RequestContext context) {
    }

    /**
     * code to execute after each handleRequest
     *
     * @param exchange the HttpServerExchange
     * @param context the RequestContext
     */
    void after(HttpServerExchange exchange, RequestContext context) {
    }

    private static class RequestDispatcherHandlerHolder {

        private static final RequestDispatcherHandler INSTANCE
                = new RequestDispatcherHandler();

        private RequestDispatcherHandlerHolder() {
        }
    }
}
