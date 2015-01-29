/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import org.restheart.handlers.root.GetRootHandler;
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
import org.restheart.handlers.indexes.DeleteIndexHandler;
import org.restheart.handlers.indexes.GetIndexesHandler;
import org.restheart.handlers.indexes.PutIndexHandler;
import org.restheart.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import static org.restheart.handlers.RequestContext.METHOD;
import static org.restheart.handlers.RequestContext.TYPE;
import org.restheart.handlers.files.PutFileHandler;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
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
     */
    public RequestDispacherHandler() {
        super(null);

        this.rootGet = new GetRootHandler();
        this.dbGet = new GetDBHandler();
        this.dbPut = new PutDBHandler();
        this.dbDelete = new DeleteDBHandler();
        this.dbPatch = new PatchDBHandler();
        this.collectionGet = new GetCollectionHandler();
        this.collectionPost = new PostCollectionHandler();
        this.collectionPut = new PutCollectionHandler();
        this.collectionDelete = new DeleteCollectionHandler();
        this.collectionPatch = new PatchCollectionHandler();
        this.documentGet = new GetDocumentHandler();
        this.documentPut = new PutDocumentHandler();
        this.documentDelete = new DeleteDocumentHandler();
        this.documentPatch = new PatchDocumentHandler();
        this.indexesGet = new GetIndexesHandler();
        this.indexPut = new PutIndexHandler();
        this.indexDelete = new DeleteIndexHandler();
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

    /**
     * Package private constructor, for testing purposes only.
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
    RequestDispacherHandler(
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

}
