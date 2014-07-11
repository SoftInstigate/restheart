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
import com.softinstigate.restheart.handlers.account.DeleteAccountHandler;
import com.softinstigate.restheart.handlers.account.GetAccountHandler;
import com.softinstigate.restheart.handlers.account.PatchAccountHandler;
import com.softinstigate.restheart.handlers.account.PostAccountHandler;
import com.softinstigate.restheart.handlers.account.PutAccountHandler;
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
    
    private final GetAccountHandler accountGet;
    private final PostAccountHandler accountPost;
    private final PutAccountHandler accountPut;
    private final DeleteAccountHandler accountDelete;
    private final PatchAccountHandler accountPatch;
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
     * @param accountGet
     * @param accountPost
     * @param accountPut
     * @param accountDelete
     * @param accountPatch
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
            GetAccountHandler accountGet,
            PostAccountHandler accountPost,
            PutAccountHandler accountPut,
            DeleteAccountHandler accountDelete,
            PatchAccountHandler accountPatch,
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
        this.accountGet = accountGet;
        this.accountPost = accountPost;
        this.accountPut = accountPut;
        this.accountDelete = accountDelete;
        this.accountPatch = accountPatch;
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
                    case ACCOUNT:
                        accountGet.handleRequest(exchange);
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
                    case ACCOUNT:
                        accountPost.handleRequest(exchange);
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
                    case ACCOUNT:
                        accountPut.handleRequest(exchange);
                        return;
                    case DB:
                        if (doesDbExist(exchange, c.getDBName()))
                            dbPut.handleRequest(exchange);
                        return;
                    case COLLECTION:
                        if (doesCollectionExist(exchange, c.getDBName(), c.getCollectionName()))
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
                    case ACCOUNT:
                        accountDelete.handleRequest(exchange);
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
                    case ACCOUNT:
                        accountPatch.handleRequest(exchange);
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
        if (!client.getDB(dbName).collectionExists(collectionName))
        {
            ResponseHelper.endExchange(exchange, HttpStatus.SC_NOT_FOUND);
            return false;
        }
        
        return true;
    }
}
