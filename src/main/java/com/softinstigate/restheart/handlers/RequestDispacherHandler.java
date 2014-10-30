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
import com.softinstigate.restheart.handlers.collection.OptionsCollectionHandler;
import com.softinstigate.restheart.handlers.database.OptionsDBHandler;
import com.softinstigate.restheart.handlers.document.OptionsDocumentHandler;
import com.softinstigate.restheart.handlers.indexes.OptionsIndexHandler;
import com.softinstigate.restheart.handlers.indexes.OptionsIndexesHandler;
import com.softinstigate.restheart.handlers.root.OptionsRootHandler;
import com.softinstigate.restheart.utils.ResponseHelper;

/**
 *
 * @author uji
 */
public class RequestDispacherHandler extends PipedHttpHandler
{
    private final GetRootHandler rootGet;
    private final OptionsRootHandler rootOptions;
    private final GetDBHandler dbGet;
    private final PutDBHandler dbPut;
    private final DeleteDBHandler dbDelete;
    private final PatchDBHandler dbPatch;
    private final OptionsDBHandler dbOptions;
    private final GetCollectionHandler collectionGet;
    private final PostCollectionHandler collectionPost;
    private final PutCollectionHandler collectionPut;
    private final DeleteCollectionHandler collectionDelete;
    private final PatchCollectionHandler collectionPatch;
    private final OptionsCollectionHandler collectionOptions;
    private final GetDocumentHandler documentGet;
    private final PutDocumentHandler documentPut;
    private final DeleteDocumentHandler documentDelete;
    private final PatchDocumentHandler documentPatch;
    private final OptionsDocumentHandler documentOptions;
    private final GetIndexesHandler indexesGet;
    private final OptionsIndexesHandler indexesOptions;
    private final PutIndexHandler indexPut;
    private final DeleteIndexHandler indexDelete;
    private final OptionsIndexHandler indexOptions;

    /**
     * Creates a new instance of RequestDispacherHandler
     *
     * @param rootGet
     * @param rootOptions
     * @param dbGet
     * @param dbOptions
     * @param dbPut
     * @param dbDelete
     * @param dbPatch
     * @param collectionGet
     * @param collectionOptions
     * @param collectionPost
     * @param collectionPut
     * @param collectionDelete
     * @param collectionPatch
     * @param documentOptions
     * @param documentGet
     * @param documentPut
     * @param indexOptions
     * @param documentDelete
     * @param documentPatch
     * @param indexesOptions
     * @param indexesGet
     * @param indexDelete
     * @param indexPut
     */
    public RequestDispacherHandler(
            GetRootHandler rootGet,
            OptionsRootHandler rootOptions,
            GetDBHandler dbGet,
            PutDBHandler dbPut,
            DeleteDBHandler dbDelete,
            PatchDBHandler dbPatch,
            OptionsDBHandler dbOptions,
            GetCollectionHandler collectionGet,
            PostCollectionHandler collectionPost,
            PutCollectionHandler collectionPut,
            DeleteCollectionHandler collectionDelete,
            PatchCollectionHandler collectionPatch,
            OptionsCollectionHandler collectionOptions,
            GetDocumentHandler documentGet,
            PutDocumentHandler documentPut,
            DeleteDocumentHandler documentDelete,
            PatchDocumentHandler documentPatch,
            OptionsDocumentHandler documentOptions,
            GetIndexesHandler indexesGet,
            OptionsIndexesHandler indexesOptions,
            PutIndexHandler indexPut,
            DeleteIndexHandler indexDelete,
            OptionsIndexHandler indexOptions
    )
    {

        super(null);
        this.rootGet = rootGet;
        this.rootOptions = rootOptions;
        this.dbGet = dbGet;
        this.dbPut = dbPut;
        this.dbDelete = dbDelete;
        this.dbPatch = dbPatch;
        this.dbOptions = dbOptions;
        this.collectionGet = collectionGet;
        this.collectionPost = collectionPost;
        this.collectionPut = collectionPut;
        this.collectionDelete = collectionDelete;
        this.collectionPatch = collectionPatch;
        this.collectionOptions = collectionOptions;
        this.documentGet = documentGet;
        this.documentPut = documentPut;
        this.documentDelete = documentDelete;
        this.documentPatch = documentPatch;
        this.documentOptions = documentOptions;
        this.indexesGet = indexesGet;
        this.indexesOptions = indexesOptions;
        this.indexPut = indexPut;
        this.indexDelete = indexDelete;
        this.indexOptions = indexOptions;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception
    {
        if (context.getType() == TYPE.ERROR)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return;
        }

        if (context.getMethod() == METHOD.OTHER)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_IMPLEMENTED);
            return;
        }

        if (context.isReservedResource())
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return;
        }

        if (context.getMethod() == METHOD.GET)
        {
            switch (context.getType())
            {
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
        }
        else if (context.getMethod() == METHOD.POST)
        {
            switch (context.getType())
            {
                case COLLECTION:
                    collectionPost.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        }
        else if (context.getMethod() == METHOD.PUT)
        {
            switch (context.getType())
            {
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
        }
        else if (context.getMethod() == METHOD.DELETE)
        {
            switch (context.getType())
            {
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
        }
        else if (context.getMethod() == METHOD.PATCH)
        {
            switch (context.getType())
            {
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
        }
        else if (context.getMethod() == METHOD.OPTIONS)
        {
            switch (context.getType())
            {
                case ROOT:
                    rootOptions.handleRequest(exchange, context);
                    return;
                case DB:
                    dbOptions.handleRequest(exchange, context);
                    return;
                case COLLECTION:
                    collectionOptions.handleRequest(exchange, context);
                    return;
                case DOCUMENT:
                    documentOptions.handleRequest(exchange, context);
                    return;
                case COLLECTION_INDEXES:
                    indexesOptions.handleRequest(exchange, context);
                    return;
                 case INDEX:
                    indexOptions.handleRequest(exchange, context);
                    return;
                default:
                    ResponseHelper.endExchange(exchange, HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
        }
    }
}
