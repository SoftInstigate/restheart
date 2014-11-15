/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.handlers;

import com.softinstigate.restheart.handlers.root.GetRootHandler;
import com.softinstigate.restheart.handlers.collection.DeleteCollectionHandler;
import com.softinstigate.restheart.handlers.collection.GetCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PatchCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PostCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PutCollectionHandler;
import com.softinstigate.restheart.handlers.database.DeleteDBHandler;
import com.softinstigate.restheart.handlers.database.GetDBHandler;
import com.softinstigate.restheart.handlers.database.PatchDBHandler;
import com.softinstigate.restheart.handlers.database.PutDBHandler;
import com.softinstigate.restheart.handlers.document.DeleteDocumentHandler;
import com.softinstigate.restheart.handlers.document.GetDocumentHandler;
import com.softinstigate.restheart.handlers.document.PatchDocumentHandler;
import com.softinstigate.restheart.handlers.document.PutDocumentHandler;
import com.softinstigate.restheart.handlers.indexes.DeleteIndexHandler;
import com.softinstigate.restheart.handlers.indexes.GetIndexesHandler;
import com.softinstigate.restheart.handlers.indexes.PutIndexHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import static com.softinstigate.restheart.handlers.RequestContext.METHOD;
import static com.softinstigate.restheart.handlers.RequestContext.TYPE;
import com.softinstigate.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare
 */
public class RequestDispacherHandler extends PipedHttpHandler {
    private final GetRootHandler rootGet;
    private final GetDBHandler dbGet;
    private final PutDBHandler dbPut;
    private final DeleteDBHandler dbDelete;
    private final PatchDBHandler dbPatch;
    private final GetCollectionHandler collectionGet;
    private final PostCollectionHandler collectionPost;
    private final PutCollectionHandler collectionPut;
    private final DeleteCollectionHandler collectionDelete;
    private final PatchCollectionHandler collectionPatch;
    private final GetDocumentHandler documentGet;
    private final PutDocumentHandler documentPut;
    private final DeleteDocumentHandler documentDelete;
    private final PatchDocumentHandler documentPatch;
    private final GetIndexesHandler indexesGet;
    private final PutIndexHandler indexPut;
    private final DeleteIndexHandler indexDelete;

    /**
     * Creates a new instance of RequestDispacherHandler
     *
     * @param rootGet
     * @param dbGet
     * @param dbPut
     * @param dbDelete
     * @param dbPatch
     * @param collectionGet
     * @param collectionPost
     * @param collectionPut
     * @param collectionDelete
     * @param collectionPatch
     * @param documentGet
     * @param documentPut
     * @param documentDelete
     * @param documentPatch
     * @param indexesGet
     * @param indexDelete
     * @param indexPut
     */
    public RequestDispacherHandler(
            GetRootHandler rootGet,
            GetDBHandler dbGet,
            PutDBHandler dbPut,
            DeleteDBHandler dbDelete,
            PatchDBHandler dbPatch,
            GetCollectionHandler collectionGet,
            PostCollectionHandler collectionPost,
            PutCollectionHandler collectionPut,
            DeleteCollectionHandler collectionDelete,
            PatchCollectionHandler collectionPatch,
            GetDocumentHandler documentGet,
            PutDocumentHandler documentPut,
            DeleteDocumentHandler documentDelete,
            PatchDocumentHandler documentPatch,
            GetIndexesHandler indexesGet,
            PutIndexHandler indexPut,
            DeleteIndexHandler indexDelete
    ) {

        super(null);
        this.rootGet = rootGet;
        this.dbGet = dbGet;
        this.dbPut = dbPut;
        this.dbDelete = dbDelete;
        this.dbPatch = dbPatch;
        this.collectionGet = collectionGet;
        this.collectionPost = collectionPost;
        this.collectionPut = collectionPut;
        this.collectionDelete = collectionDelete;
        this.collectionPatch = collectionPatch;
        this.documentGet = documentGet;
        this.documentPut = documentPut;
        this.documentDelete = documentDelete;
        this.documentPatch = documentPatch;
        this.indexesGet = indexesGet;
        this.indexPut = indexPut;
        this.indexDelete = indexDelete;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.getType() == TYPE.ERROR) {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_BAD_REQUEST);
            return;
        }

        if (context.getMethod() == METHOD.OTHER) {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (context.isReservedResource()) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_FORBIDDEN, "reserved resource");
            return;
        }

        if (context.getMethod() == METHOD.GET) {
            switch (context.getType()) {
                case ROOT:
                    rootGet.handleRequest(exchange, context);
                    return;
                case DB:
                    dbGet.handleRequest(exchange, context);
                    return;
                case COLLECTION:
                    collectionGet.handleRequest(exchange, context);
                    return;
                case DOCUMENT:
                    documentGet.handleRequest(exchange, context);
                    return;
                case COLLECTION_INDEXES:
                    indexesGet.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        } else if (context.getMethod() == METHOD.POST) {
            switch (context.getType()) {
                case COLLECTION:
                    collectionPost.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        } else if (context.getMethod() == METHOD.PUT) {
            switch (context.getType()) {
                case DB:
                    dbPut.handleRequest(exchange, context);
                    return;
                case COLLECTION:
                    collectionPut.handleRequest(exchange, context);
                    return;
                case DOCUMENT:
                    documentPut.handleRequest(exchange, context);
                    return;
                case INDEX:
                    indexPut.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        } else if (context.getMethod() == METHOD.DELETE) {
            switch (context.getType()) {
                case DB:
                    dbDelete.handleRequest(exchange, context);
                    return;
                case COLLECTION:
                    collectionDelete.handleRequest(exchange, context);
                    return;
                case DOCUMENT:
                    documentDelete.handleRequest(exchange, context);
                    return;
                case INDEX:
                    indexDelete.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        } else if (context.getMethod() == METHOD.PATCH) {
            switch (context.getType()) {
                case DB:
                    dbPatch.handleRequest(exchange, context);
                    return;
                case COLLECTION:
                    collectionPatch.handleRequest(exchange, context);
                    return;
                case DOCUMENT:
                    documentPatch.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        } else {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }
}
