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
import org.restheart.handlers.applicationlogic.MetricsHandler;
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
import org.restheart.handlers.metadata.AfterWriteCheckMetadataHandler;
import org.restheart.handlers.metadata.BeforeWriteCheckMetadataHandler;
import org.restheart.handlers.metadata.CheckHandler;
import org.restheart.handlers.metadata.HookMetadataHandler;
import org.restheart.handlers.metadata.RequestTransformerMetadataHandler;
import org.restheart.handlers.metadata.ResponseTransformerMetadataHandler;
import org.restheart.handlers.metadata.TransformerHandler;
import org.restheart.handlers.root.GetRootHandler;
import org.restheart.handlers.schema.JsonMetaSchemaChecker;
import org.restheart.handlers.schema.JsonSchemaTransformer;
import org.restheart.metadata.transformers.PlainJsonTransformer;
import org.restheart.metadata.transformers.RepresentationTransformer.PHASE;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public final class RequestDispatcherHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestDispatcherHandler.class);

    private final Map<TYPE, Map<METHOD, PipedHttpHandler>> handlersMultimap;

    private final ResponseSenderHandler responseSenderHandler
            = new ResponseSenderHandler(null);

    /**
     * Creates a new instance of RequestDispacherHandler
     */
    public RequestDispatcherHandler() {
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
     * Put into handlersMultimap all the default combinations of types, methods
     * and PipedHttpHandler objects
     */
    void defaultInit() {
        LOGGER.trace("Initialize default HTTP handlers:");

        // *** ROOT handlers
        putPipedHttpHandler(TYPE.ROOT, METHOD.GET,
                new RequestTransformerMetadataHandler(
                        new GetRootHandler(
                                new TransformerHandler(
                                        new ResponseSenderHandler(null),
                                        PHASE.RESPONSE,
                                        new PlainJsonTransformer()))));

        // *** DB handlers
        putPipedHttpHandler(TYPE.DB, METHOD.GET,
                new RequestTransformerMetadataHandler(
                        new GetDBHandler(
                                new TransformerHandler(
                                        new ResponseTransformerMetadataHandler(
                                                new ResponseSenderHandler(null)),
                                        PHASE.RESPONSE,
                                        new AggregationTransformer(),
                                        new PlainJsonTransformer()))));

        putPipedHttpHandler(TYPE.DB, METHOD.PUT,
                new RequestTransformerMetadataHandler(
                        new RequestTransformerMetadataHandler(
                                new PutDBHandler(
                                        respTransformers()))));

        putPipedHttpHandler(TYPE.DB, METHOD.DELETE,
                new RequestTransformerMetadataHandler(
                        new DeleteDBHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.DB, METHOD.PATCH,
                new RequestTransformerMetadataHandler(
                        new PatchDBHandler(
                                respTransformers())));

        // *** COLLECTION handlers
        putPipedHttpHandler(TYPE.COLLECTION, METHOD.GET,
                new RequestTransformerMetadataHandler(
                        new GetCollectionHandler(
                                new ResponseTransformerMetadataHandler(
                                        new TransformerHandler(
                                                new HookMetadataHandler(
                                                        new ResponseSenderHandler()),
                                                PHASE.RESPONSE,
                                                new PlainJsonTransformer(),
                                                new AggregationTransformer())))));

        putPipedHttpHandler(TYPE.COLLECTION, METHOD.POST,
                new NormalOrBulkDispatcherHandler(
                        new RequestTransformerMetadataHandler(
                                new BeforeWriteCheckMetadataHandler(
                                        new PostCollectionHandler(
                                                new AfterWriteCheckMetadataHandler(
                                                        respTransformers())))),
                        new RequestTransformerMetadataHandler(
                                new BeforeWriteCheckMetadataHandler(
                                        new BulkPostCollectionHandler(
                                                respTransformers())))));

        putPipedHttpHandler(TYPE.COLLECTION, METHOD.PUT,
                new RequestTransformerMetadataHandler(
                        new TransformerHandler(
                                new PutCollectionHandler(
                                        respTransformers()),
                                PHASE.REQUEST,
                                new AggregationTransformer())));

        putPipedHttpHandler(TYPE.COLLECTION, METHOD.DELETE,
                new RequestTransformerMetadataHandler(
                        new DeleteCollectionHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.COLLECTION, METHOD.PATCH,
                new RequestTransformerMetadataHandler(
                        new TransformerHandler(
                                new PatchCollectionHandler(
                                        respTransformers()),
                                PHASE.REQUEST,
                                new AggregationTransformer())));

        // *** DOCUMENT handlers
        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.GET,
                new RequestTransformerMetadataHandler(
                        new GetDocumentHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.PUT,
                new RequestTransformerMetadataHandler(
                        new BeforeWriteCheckMetadataHandler(
                                new PutDocumentHandler(
                                        new AfterWriteCheckMetadataHandler(
                                                respTransformers())))));

        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.DELETE,
                new RequestTransformerMetadataHandler(
                        new DeleteDocumentHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.DOCUMENT, METHOD.PATCH,
                new RequestTransformerMetadataHandler(
                        new BeforeWriteCheckMetadataHandler(
                                new PatchDocumentHandler(
                                        new AfterWriteCheckMetadataHandler(
                                                respTransformers())))));

        // *** BULK_DOCUMENTS handlers, i.e. bulk operations
        putPipedHttpHandler(TYPE.BULK_DOCUMENTS, METHOD.DELETE,
                new RequestTransformerMetadataHandler(
                        new BulkDeleteDocumentsHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.BULK_DOCUMENTS, METHOD.PATCH,
                new RequestTransformerMetadataHandler(
                        new BeforeWriteCheckMetadataHandler(
                                new BulkPatchDocumentsHandler(
                                        respTransformers()))));

        // *** COLLECTION_INDEXES handlers
        putPipedHttpHandler(TYPE.COLLECTION_INDEXES, METHOD.GET,
                new RequestTransformerMetadataHandler(
                        new GetIndexesHandler(
                                respTransformers())));

        // *** INDEX handlers
        putPipedHttpHandler(TYPE.INDEX, METHOD.PUT,
                new RequestTransformerMetadataHandler(
                        new PutIndexHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.INDEX, METHOD.DELETE,
                new RequestTransformerMetadataHandler(
                        new DeleteIndexHandler(
                                respTransformers())));

        // *** FILES_BUCKET and FILE handlers
        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.GET,
                new RequestTransformerMetadataHandler(
                        new GetCollectionHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.POST,
                new RequestTransformerMetadataHandler(
                        new BeforeWriteCheckMetadataHandler(
                                new PostBucketHandler(
                                        respTransformers()))));

        putPipedHttpHandler(TYPE.FILE, METHOD.PUT,
                new RequestTransformerMetadataHandler(
                        new BeforeWriteCheckMetadataHandler(
                                new PutFileHandler(
                                        new FileMetadataHandler(
                                                respTransformers())))));

        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.PUT,
                new RequestTransformerMetadataHandler(
                        new PutBucketHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.FILES_BUCKET, METHOD.DELETE,
                new RequestTransformerMetadataHandler(
                        new DeleteBucketHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.FILE, METHOD.GET,
                new RequestTransformerMetadataHandler(
                        new GetFileHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.FILE_BINARY, METHOD.GET,
                new GetFileBinaryHandler(
                        new HookMetadataHandler(
                                new ResponseSenderHandler())));

        putPipedHttpHandler(TYPE.FILE, METHOD.DELETE,
                new RequestTransformerMetadataHandler(
                        new DeleteFileHandler(
                                respTransformers())));

        // PUTting or PATCHing a file involves updating the metadata in the
        // xxx.files bucket for an _id. Although the chunks are immutable we
        // can treat the metadata like a regular document.
        putPipedHttpHandler(TYPE.FILE, METHOD.PATCH,
                new RequestTransformerMetadataHandler(
                        new BeforeWriteCheckMetadataHandler(
                                new FileMetadataHandler(
                                        new AfterWriteCheckMetadataHandler(
                                                respTransformers())))));

        /*
         * TODO There's already a PUT handler that allows custom id's to be set. Need to think about how to handle PUT with no binary data
        putPipedHttpHandler(TYPE.FILE, METHOD.PUT,
                new RequestTransformerMetadataHandler(
                        new BeforeWriteCheckMetadataHandler(
                                new FileMetadataHandler(
                                        new AfterWriteCheckMetadataHandler(
                                                respTransformers())))));
         */

        // *** AGGREGATION handler
        putPipedHttpHandler(TYPE.AGGREGATION, METHOD.GET,
                new RequestTransformerMetadataHandler(
                        new GetAggregationHandler(
                                respTransformers())));

        // *** SCHEMA handlers
        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.GET,
                new RequestTransformerMetadataHandler(
                        new GetCollectionHandler(
                                new TransformerHandler(
                                        respTransformers(),
                                        PHASE.RESPONSE,
                                        new JsonSchemaTransformer()))));

        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.PUT,
                new RequestTransformerMetadataHandler(
                        new PutCollectionHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.POST,
                new RequestTransformerMetadataHandler(
                        new CheckHandler(
                                new TransformerHandler(
                                        new PostCollectionHandler(
                                                respTransformers()),
                                        PHASE.REQUEST,
                                        new JsonSchemaTransformer()),
                                new JsonMetaSchemaChecker())));

        putPipedHttpHandler(TYPE.SCHEMA_STORE, METHOD.DELETE,
                new RequestTransformerMetadataHandler(
                        new DeleteCollectionHandler(
                                respTransformers())));

        putPipedHttpHandler(TYPE.SCHEMA, METHOD.GET,
                new RequestTransformerMetadataHandler(
                        new GetDocumentHandler(
                                new TransformerHandler(
                                        respTransformers(),
                                        PHASE.RESPONSE,
                                        new JsonSchemaTransformer()))));

        putPipedHttpHandler(TYPE.SCHEMA, METHOD.PUT,
                new RequestTransformerMetadataHandler(
                        new CheckHandler(
                                new TransformerHandler(
                                        new PutDocumentHandler(
                                                respTransformers()),
                                        PHASE.REQUEST,
                                        new JsonSchemaTransformer()),
                                new JsonMetaSchemaChecker())));

        putPipedHttpHandler(TYPE.SCHEMA, METHOD.DELETE,
                new RequestTransformerMetadataHandler(
                        new DeleteDocumentHandler(
                                respTransformers())));


        putPipedHttpHandler(TYPE.METRICS, METHOD.GET, new MetricsHandler(respTransformers()));
    }

    private PipedHttpHandler respTransformers() {
        return new ResponseTransformerMetadataHandler(
                new TransformerHandler(
                        new HookMetadataHandler(
                                new ResponseSenderHandler()),
                        PHASE.RESPONSE,
                        new PlainJsonTransformer()));
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
    void putPipedHttpHandler(TYPE type, METHOD method, PipedHttpHandler handler) {
        LOGGER.trace("putPipedHttpHandler( {}, {}, {} )", type, method, getHandlerToLog(handler).getClass().getCanonicalName());
        Map<METHOD, PipedHttpHandler> methodsMap = handlersMultimap.get(type);
        if (methodsMap == null) {
            methodsMap = new HashMap<>();
            handlersMultimap.put(type, methodsMap);
        }
        methodsMap.put(method, handler);
    }

    private PipedHttpHandler getHandlerToLog(PipedHttpHandler handler) {
        if (handler instanceof BeforeWriteCheckMetadataHandler
                || handler instanceof RequestTransformerMetadataHandler
                || handler instanceof CheckHandler
                || handler instanceof TransformerHandler) {
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
}
