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
package com.softinstigate.restart.handlers;

import com.mongodb.MongoClient;
import com.softinstigate.restart.db.MongoDBClientSingleton;
import com.softinstigate.restart.utils.HttpStatus;
import com.softinstigate.restart.utils.RequestContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import static com.softinstigate.restart.utils.RequestContext.METHOD;
import static com.softinstigate.restart.utils.RequestContext.TYPE;
import com.softinstigate.restart.utils.ResponseHelper;

/**
 *
 * @author uji
 */
public class RequestDispacherHandler implements HttpHandler
{
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();
    
    private HttpHandler dbGet;
    private HttpHandler dbPost;
    private HttpHandler dbPut;
    private HttpHandler dbDelete;
    private HttpHandler collectionGet;
    private HttpHandler collectionPost;
    private HttpHandler collectionPut;
    private HttpHandler collectionDelete;
    private HttpHandler documentGet;
    private HttpHandler documentPost;
    private HttpHandler documentPut;
    private HttpHandler documentDelete;

    /**
     * Creates a new instance of EntityResource
     *
     * @param dbGet
     * @param dbPost
     * @param dbPut
     * @param dbDelete
     * @param collectionGet
     * @param collectionPost
     * @param collectionPut
     * @param collectionDelete
     * @param documentGet
     * @param documentPost
     * @param documentPut
     * @param documentDelete
     */
    public RequestDispacherHandler(
            HttpHandler dbGet,
            HttpHandler dbPost,
            HttpHandler dbPut,
            HttpHandler dbDelete,
            HttpHandler collectionGet,
            HttpHandler collectionPost,
            HttpHandler collectionPut,
            HttpHandler collectionDelete,
            HttpHandler documentGet,
            HttpHandler documentPost,
            HttpHandler documentPut,
            HttpHandler documentDelete
    )
    {
        this.dbGet = dbGet;
        this.dbPost = dbPost;
        this.dbPut = dbPut;
        this.dbDelete = dbDelete;
        this.collectionGet = collectionGet;
        this.collectionPost = collectionPost;
        this.collectionPut = collectionPut;
        this.collectionDelete = collectionDelete;
        this.documentGet = documentGet;
        this.documentPost = documentPost;
        this.documentPut = documentPut;
        this.documentDelete = documentDelete;

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
        
        switch (c.getMethod())
        {
            case GET:
                switch (c.getType())
                {
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
