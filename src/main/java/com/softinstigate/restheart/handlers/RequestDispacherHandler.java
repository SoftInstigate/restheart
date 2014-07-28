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

import com.mongodb.MongoClient;
import com.softinstigate.restheart.db.MongoDBClientSingleton;
import com.softinstigate.restheart.handlers.root.DeleteRootHandler;
import com.softinstigate.restheart.handlers.root.GetRootHandler;
import com.softinstigate.restheart.handlers.root.PatchRootHandler;
import com.softinstigate.restheart.handlers.root.PostRootHandler;
import com.softinstigate.restheart.handlers.root.PutRootHandler;
import com.softinstigate.restheart.handlers.collection.DeleteCollectionHandler;
import com.softinstigate.restheart.handlers.collection.GetCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PatchCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PostCollectionHandler;
import com.softinstigate.restheart.handlers.collection.PutCollectionHandler;
import com.softinstigate.restheart.handlers.database.DeleteDBHandler;
import com.softinstigate.restheart.handlers.database.GetDBHandler;
import com.softinstigate.restheart.handlers.database.PatchDBHandler;
import com.softinstigate.restheart.handlers.database.PostDBHandler;
import com.softinstigate.restheart.handlers.database.PutDBHandler;
import com.softinstigate.restheart.handlers.document.DeleteDocumentHandler;
import com.softinstigate.restheart.handlers.document.GetDocumentHandler;
import com.softinstigate.restheart.handlers.document.PatchDocumentHandler;
import com.softinstigate.restheart.handlers.document.PostDocumentHandler;
import com.softinstigate.restheart.handlers.document.PutDocumentHandler;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.RequestContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import static com.softinstigate.restheart.utils.RequestContext.METHOD;
import static com.softinstigate.restheart.utils.RequestContext.TYPE;
import com.softinstigate.restheart.utils.ResponseHelper;

/**
 *
 * @author uji
 */
public class RequestDispacherHandler implements HttpHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    private final GetRootHandler rootGet;
    private final PostRootHandler rootPost;
    private final PutRootHandler rootPut;
    private final DeleteRootHandler rootDelete;
    private final PatchRootHandler rootPatch;
    private final GetDBHandler dbGet;
    private final PostDBHandler dbPost;
    private final PutDBHandler dbPut;
    private final DeleteDBHandler dbDelete;
    private final PatchDBHandler dbPatch;
    private final GetCollectionHandler collectionGet;
    private final PostCollectionHandler collectionPost;
    private final PutCollectionHandler collectionPut;
    private final DeleteCollectionHandler collectionDelete;
    private final PatchCollectionHandler collectionPatch;
    private final GetDocumentHandler documentGet;
    private final PostDocumentHandler documentPost;
    private final PutDocumentHandler documentPut;
    private final DeleteDocumentHandler documentDelete;
    private final PatchDocumentHandler documentPatch;

    /**
     * Creates a new instance of EntityResource
     *
     * @param rootGet
     * @param rootPost
     * @param rootPut
     * @param rootDelete
     * @param rootPatch
     * @param dbGet
     * @param dbPost
     * @param dbPut
     * @param dbDelete
     * @param dbPatch
     * @param collectionGet
     * @param collectionPost
     * @param collectionPut
     * @param collectionDelete
     * @param collectionPatch
     * @param documentGet
     * @param documentPost
     * @param documentPut
     * @param documentDelete
     * @param documentPatch
     */
    public RequestDispacherHandler(
            GetRootHandler rootGet,
            PostRootHandler rootPost,
            PutRootHandler rootPut,
            DeleteRootHandler rootDelete,
            PatchRootHandler rootPatch,
            GetDBHandler dbGet,
            PostDBHandler dbPost,
            PutDBHandler dbPut,
            DeleteDBHandler dbDelete,
            PatchDBHandler dbPatch,
            GetCollectionHandler collectionGet,
            PostCollectionHandler collectionPost,
            PutCollectionHandler collectionPut,
            DeleteCollectionHandler collectionDelete,
            PatchCollectionHandler collectionPatch,
            GetDocumentHandler documentGet,
            PostDocumentHandler documentPost,
            PutDocumentHandler documentPut,
            DeleteDocumentHandler documentDelete,
            PatchDocumentHandler documentPatch
    )
    {
        this.rootGet = rootGet;
        this.rootPost = rootPost;
        this.rootPut = rootPut;
        this.rootDelete = rootDelete;
        this.rootPatch = rootPatch;
        this.dbGet = dbGet;
        this.dbPost = dbPost;
        this.dbPut = dbPut;
        this.dbDelete = dbDelete;
        this.dbPatch = dbPatch;
        this.collectionGet = collectionGet;
        this.collectionPost = collectionPost;
        this.collectionPut = collectionPut;
        this.collectionDelete = collectionDelete;
        this.collectionPatch = collectionPatch;
        this.documentGet = documentGet;
        this.documentPost = documentPost;
        this.documentPut = documentPut;
        this.documentDelete = documentDelete;
        this.documentPatch = documentPatch;

    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception
    {
        RequestContext c = new RequestContext(exchange);

        if (c.getType() == TYPE.ERROR)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return;
        }
        
        if (c.getMethod() == METHOD.OTHER)
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_IMPLEMENTED);
            return;
        }
        
        // TODO: use AllowedMethodsHandler to limit methods
        
        switch (c.getMethod())
        {
            case GET:
                switch (c.getType())
                {
                    case ROOT:
                        rootGet.handleRequest(exchange);
                        return;
                    case DB:
                        if (doesDbExist(exchange, c.getDBName()))
                            dbGet.handleRequest(exchange);
                        return;
                    case COLLECTION:
                        if (doesCollectionExist(exchange, c.getDBName(), c.getCollectionName()))
                            collectionGet.handleRequest(exchange);
                        return;
                    case DOCUMENT:
                        if (doesCollectionExist(exchange, c.getDBName(), c.getCollectionName()))
                            documentGet.handleRequest(exchange);
                        return;
                }
            
            case POST:
                switch (c.getType())
                {
                    case ROOT:
                        rootPost.handleRequest(exchange);
                        return;
                    case DB:
                        if (doesDbExist(exchange, c.getDBName()))
                            dbPost.handleRequest(exchange);
                        return;
                    case COLLECTION:
                        if (doesCollectionExist(exchange, c.getDBName(), c.getCollectionName()))
                            collectionPost.handleRequest(exchange);
                        return;
                    case DOCUMENT:
                        if (doesCollectionExist(exchange, c.getDBName(), c.getCollectionName()))
                            documentPost.handleRequest(exchange);
                        return;
                }
            
            case PUT:
                switch (c.getType())
                {
                    case ROOT:
                        rootPut.handleRequest(exchange);
                        return;
                    case DB:
                            dbPut.handleRequest(exchange);
                        return;
                    case COLLECTION:
                        if (doesDbExist(exchange, c.getDBName()))
                            collectionPut.handleRequest(exchange);
                        return;
                    case DOCUMENT:
                        if (doesCollectionExist(exchange, c.getDBName(), c.getCollectionName()))
                            documentPut.handleRequest(exchange);
                        return;
                }
            
            case DELETE:
                switch (c.getType())
                {
                    case ROOT:
                        rootDelete.handleRequest(exchange);
                        return;
                    case DB:
                        if (doesDbExist(exchange, c.getDBName()))
                            dbDelete.handleRequest(exchange);
                        return;
                    case COLLECTION:
                        if (doesCollectionExist(exchange, c.getDBName(), c.getCollectionName()))
                            collectionDelete.handleRequest(exchange);
                        return;
                    case DOCUMENT:
                        if (doesCollectionExist(exchange, c.getDBName(), c.getCollectionName()))
                            documentDelete.handleRequest(exchange);
                        return;
                }
            
            case PATCH:
                switch (c.getType())
                {
                    case ROOT:
                        rootPatch.handleRequest(exchange);
                        return;
                    case DB:
                        if (doesDbExist(exchange, c.getDBName()))
                            dbPatch.handleRequest(exchange);
                        return;
                    case COLLECTION:
                        if (doesCollectionExist(exchange, c.getDBName(), c.getCollectionName()))
                            collectionPatch.handleRequest(exchange);
                        return;
                    case DOCUMENT:
                        if (doesCollectionExist(exchange, c.getDBName(), c.getCollectionName()))
                            documentPatch.handleRequest(exchange);
                }
        }
    }
    
    private static boolean doesDbExist(HttpServerExchange exchange, String dbName)
    {
        if (!client.getDatabaseNames().contains(dbName))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return false;
        }
        
        return true;
    }
    
    private static boolean doesCollectionExist(HttpServerExchange exchange, String dbName, String collectionName)
    {
        if (dbName == null || dbName.isEmpty() || dbName.contains(" "))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return false;
        }
        
        if (!client.getDB(dbName).collectionExists(collectionName))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return false;
        }
        
        return true;
    }
}
