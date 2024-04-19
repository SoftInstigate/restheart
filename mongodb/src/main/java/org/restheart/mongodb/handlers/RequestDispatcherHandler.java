/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import java.util.HashMap;
import java.util.Map;

import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.ExchangeKeys.TYPE;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.handlers.PipelinedHandler;
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
import org.restheart.mongodb.handlers.root.GetRootHandler;
import org.restheart.mongodb.handlers.schema.JsonMetaSchemaChecker;
import org.restheart.mongodb.handlers.schema.JsonSchemaTransformer;
import org.restheart.mongodb.handlers.sessions.DeleteSessionHandler;
import org.restheart.mongodb.handlers.sessions.PostSessionHandler;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@SuppressWarnings("deprecation")
public class RequestDispatcherHandler extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestDispatcherHandler.class);

    /**
     *
     * @return
     */
    public static RequestDispatcherHandler getInstance() {
        return RequestDispatcherHandlerHolder.INSTANCE;
    }

    private final Map<TYPE, Map<METHOD, PipelinedHandler>> handlersMultimap;

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
        var request = MongoRequest.of(exchange);

        if (request.getMethod() == METHOD.OTHER || request.getType() == TYPE.INVALID) {
            LOGGER.debug("This method is not allowed: returning a <{}> HTTP code", HttpStatus.SC_METHOD_NOT_ALLOWED);
            MongoResponse.of(exchange).setInError(HttpStatus.SC_METHOD_NOT_ALLOWED, "method " + request.getMethod().name() + " not allowed");
            next(exchange);
            return;
        }

        final PipelinedHandler httpHandler = getPipedHttpHandler(request.getType(), request.getMethod());

        if (httpHandler != null) {
            before(exchange);
            httpHandler.handleRequest(exchange);
            after(exchange);
        } else {
            LOGGER.error("Can't find PipelinedHandler({}, {})", request.getType(), request.getMethod());
            MongoResponse.of(exchange).setInError(HttpStatus.SC_METHOD_NOT_ALLOWED, "method " + request.getMethod().name() + " not allowed");
            next(exchange);
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
        putHandler(TYPE.ROOT, METHOD.GET, new GetRootHandler());

        putHandler(TYPE.ROOT_SIZE, METHOD.GET,
                PipelinedHandler.pipe(
                        new SizeRequestTransformer(true),
                        new GetRootHandler(),
                        new SizeRequestTransformer(false)));

        // *** DB handlers
        putHandler(TYPE.DB, METHOD.GET,
                PipelinedHandler.pipe(
                        new GetDBHandler(),
                        new AggregationTransformer(false)
                ));

        putHandler(TYPE.DB_SIZE, METHOD.GET,
                PipelinedHandler.pipe(
                        new SizeRequestTransformer(true),
                        new GetDBHandler(),
                        new SizeRequestTransformer(false)));

        putHandler(TYPE.DB_META, METHOD.GET,
                PipelinedHandler.pipe(
                        new GetDocumentHandler(),
                        new AggregationTransformer(false),
                        new MetaRequestTransformer()));

        putHandler(TYPE.DB, METHOD.PUT, new PutDBHandler());

        putHandler(TYPE.DB, METHOD.DELETE, new DeleteDBHandler());

        putHandler(TYPE.DB, METHOD.PATCH, new PatchDBHandler());

        // *** COLLECTION handlers
        putHandler(TYPE.COLLECTION, METHOD.GET,
                PipelinedHandler.pipe(
                        new GetCollectionHandler(),
                        new AggregationTransformer(false)
                ));

        putHandler(TYPE.COLLECTION_SIZE, METHOD.GET,
                PipelinedHandler.pipe(
                        new SizeRequestTransformer(true),
                        new GetCollectionHandler(),
                        new SizeRequestTransformer(false)));

        putHandler(TYPE.COLLECTION_META, METHOD.GET,
                PipelinedHandler.pipe(
                        new GetDocumentHandler(),
                        new AggregationTransformer(false),
                        new MetaRequestTransformer()
                ));

        putHandler(TYPE.COLLECTION, METHOD.POST,
                new NormalOrBulkDispatcherHandler(
                                new PostCollectionHandler(),
                                new BulkPostCollectionHandler()));

        putHandler(TYPE.COLLECTION, METHOD.PUT,
                PipelinedHandler.pipe(
                        new AggregationTransformer(true),
                        new PutCollectionHandler()));

        putHandler(TYPE.COLLECTION, METHOD.DELETE, new DeleteCollectionHandler());

        putHandler(TYPE.COLLECTION, METHOD.PATCH,
                PipelinedHandler.pipe(
                        new AggregationTransformer(true),
                        new PatchCollectionHandler()
                ));

        // *** DOCUMENT handlers
        putHandler(TYPE.DOCUMENT, METHOD.GET, new GetDocumentHandler());

        putHandler(TYPE.DOCUMENT, METHOD.PUT, new PutDocumentHandler());

        putHandler(TYPE.DOCUMENT, METHOD.DELETE, new DeleteDocumentHandler());

        putHandler(TYPE.DOCUMENT, METHOD.PATCH, new PatchDocumentHandler());

        // *** BULK_DOCUMENTS handlers, i.e. bulk operations
        putHandler(TYPE.BULK_DOCUMENTS, METHOD.DELETE,
                new BulkDeleteDocumentsHandler());

        putHandler(TYPE.BULK_DOCUMENTS, METHOD.PATCH,
                new BulkPatchDocumentsHandler());

        // *** COLLECTION_INDEXES handlers
        putHandler(TYPE.COLLECTION_INDEXES, METHOD.GET, new GetIndexesHandler());

        // *** INDEX handlers
        putHandler(TYPE.INDEX, METHOD.PUT, new PutIndexHandler());

        putHandler(TYPE.INDEX, METHOD.DELETE, new DeleteIndexHandler());

        // *** FILES_BUCKET and FILE handlers
        putHandler(TYPE.FILES_BUCKET, METHOD.GET, new GetCollectionHandler());

        putHandler(TYPE.FILES_BUCKET, METHOD.GET, new GetCollectionHandler());

        putHandler(TYPE.FILES_BUCKET_SIZE, METHOD.GET,
                PipelinedHandler.pipe(
                        new SizeRequestTransformer(true),
                        new GetCollectionHandler(),
                        new SizeRequestTransformer(false)));

        putHandler(TYPE.FILES_BUCKET_META, METHOD.GET,
                PipelinedHandler.pipe(
                        new GetDocumentHandler(),
                        new MetaRequestTransformer()
                ));

        putHandler(TYPE.FILES_BUCKET, METHOD.POST, new PostBucketHandler());

        putHandler(TYPE.FILE, METHOD.PUT,
                PipelinedHandler.pipe(
                        new PutFileHandler(),
                        new FileMetadataHandler()
                ));

        putHandler(TYPE.FILES_BUCKET, METHOD.PUT, new PutBucketHandler());

        putHandler(TYPE.FILES_BUCKET, METHOD.PATCH, new PatchCollectionHandler());

        putHandler(TYPE.FILES_BUCKET, METHOD.DELETE, new DeleteBucketHandler());

        putHandler(TYPE.FILE, METHOD.GET, new GetFileHandler());

        putHandler(TYPE.FILE_BINARY, METHOD.GET, new GetFileBinaryHandler());

        putHandler(TYPE.FILE, METHOD.DELETE, new DeleteFileHandler());

        // PUTting or PATCHing a file involves updating the metadata in the
        // xxx.files bucket for an _id. Although the chunks are immutable we
        // can treat the metadata like a regular document.
        putHandler(TYPE.FILE, METHOD.PATCH, new FileMetadataHandler());

        // *** AGGREGATION handler
        putHandler(TYPE.AGGREGATION, METHOD.GET, new GetAggregationHandler());

        // *** Sessions handlers
        putHandler(TYPE.SESSIONS, METHOD.POST, new PostSessionHandler());

        // *** Session handlers
        putHandler(TYPE.SESSION, METHOD.DELETE, new DeleteSessionHandler());

        // *** SCHEMA handlers
        putHandler(TYPE.SCHEMA_STORE, METHOD.GET,
                PipelinedHandler.pipe(
                        new GetCollectionHandler(),
                        new JsonSchemaTransformer(false)
                ));

        putHandler(TYPE.SCHEMA_STORE_SIZE, METHOD.GET,
                PipelinedHandler.pipe(
                        new SizeRequestTransformer(true),
                        new GetCollectionHandler(),
                        new SizeRequestTransformer(false)));

        putHandler(TYPE.SCHEMA_STORE_META, METHOD.GET,
                PipelinedHandler.pipe(
                        new GetDocumentHandler(),
                        new MetaRequestTransformer()
                ));

        putHandler(TYPE.SCHEMA_STORE, METHOD.PUT,
                PipelinedHandler.pipe(
                        new PutCollectionHandler()
                ));

        putHandler(TYPE.SCHEMA_STORE, METHOD.PATCH, new PatchCollectionHandler());

        putHandler(TYPE.SCHEMA_STORE, METHOD.POST,
                PipelinedHandler.pipe(
                        new JsonMetaSchemaChecker(),
                        new JsonSchemaTransformer(true),
                        new PostCollectionHandler()
                ));

        putHandler(TYPE.SCHEMA_STORE, METHOD.DELETE, new DeleteCollectionHandler());

        putHandler(TYPE.SCHEMA, METHOD.GET,
                PipelinedHandler.pipe(
                        new GetDocumentHandler(),
                        new JsonSchemaTransformer(false)
                ));

        putHandler(TYPE.SCHEMA, METHOD.PUT,
                PipelinedHandler.pipe(
                        new JsonMetaSchemaChecker(),
                        new JsonSchemaTransformer(true),
                        new PutDocumentHandler()
                ));

        putHandler(TYPE.SCHEMA, METHOD.DELETE, new DeleteDocumentHandler());
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
        private static final RequestDispatcherHandler INSTANCE = new RequestDispatcherHandler();

        private RequestDispatcherHandlerHolder() {
        }
    }
}
