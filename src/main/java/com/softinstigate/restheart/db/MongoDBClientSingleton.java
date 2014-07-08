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

package com.softinstigate.restheart.db;

import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class MongoDBClientSingleton
{
    private static boolean initialized = false;
    
    private static transient String mongoHost = null;
    private static transient int mongoPort = 27017;
    private static transient String mongoUser = null;
    private static transient String mongoPassword = null;
    
    private MongoClient mongoClient;
    
    private Logger logger = LoggerFactory.getLogger(MongoDBClientSingleton.class);
    
    private MongoDBClientSingleton()
    {
        if (!initialized)
            throw new IllegalStateException("not initialized");
        
        try
        {
            setup();
        }
        catch (UnknownHostException ex)
        {
            logger.error("error initializing mongodb client", ex);
        }
        catch (Throwable tr)
        {
            logger.error("error initializing mongodb client", tr);
        }
    }
    
    public static void init(String host, int port, String user, String password)
    {
        mongoHost = host;
        mongoPort = port;
        mongoUser = user;
        mongoPassword = password;
        initialized = true;
    }
    
    private void setup() throws UnknownHostException
    {
        List<ServerAddress> servers = new ArrayList<>();
        List<MongoCredential> credentials = new ArrayList<>();
        
        servers.add(new ServerAddress(mongoHost, mongoPort));
        
        credentials.add(MongoCredential.createMongoCRCredential(mongoUser, "admin", mongoPassword.toCharArray()));
                
        mongoClient = new MongoClient(servers, credentials); 
    }
    
    public static MongoDBClientSingleton getInstance()
    {
        return MongoDBClientSingletonHolder.INSTANCE;
    }
    
    private static class MongoDBClientSingletonHolder
    {
        private static final MongoDBClientSingleton INSTANCE = new MongoDBClientSingleton();
    }
    
    public MongoClient getClient()
    {
        if (this.mongoClient == null)
            throw new IllegalStateException("mongo client not initialized");
        
        return this.mongoClient;
    }
}