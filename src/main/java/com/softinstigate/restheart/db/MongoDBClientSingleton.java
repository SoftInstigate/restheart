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
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.softinstigate.restheart.Configuration;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author uji
 */
public class MongoDBClientSingleton
{
    private static boolean initialized = false;
    
    private static transient List<Map<String, Object>> mongoServers;
    private static transient List<Map<String, Object>> mongoCredentials;
    
    private MongoClient mongoClient;
    
    private static Logger logger = LoggerFactory.getLogger(MongoDBClientSingleton.class);
    
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
    
    public static void init(Configuration conf)
    {
        mongoServers = conf.getMongoServers();
        mongoCredentials = conf.getMongoCredentials();
        
        if (mongoServers != null && !mongoServers.isEmpty())
            initialized = true;
        else
        {
            logger.error("error initializing mongodb client, no servers found in configuration");
        }
    }
    
    private void setup() throws UnknownHostException
    {
        if (initialized)
        {
            List<ServerAddress> servers = new ArrayList<>();
            List<MongoCredential> credentials = new ArrayList<>();
            
            for (Map<String, Object> mongoServer : mongoServers)
            {
                Object mongoHost = mongoServer.get(Configuration.MONGO_HOST);
                Object mongoPort = mongoServer.get(Configuration.MONGO_PORT);
                
                if (mongoHost != null && mongoHost instanceof String && mongoPort != null && mongoPort instanceof Integer)
                    servers.add(new ServerAddress((String) mongoHost, (int) mongoPort));
            }
            
            if (mongoCredentials != null)
            {
                for (Map<String, Object> mongoCredential : mongoCredentials)
                {
                    Object mongoAuthDb = mongoCredential.get(Configuration.MONGO_AUTH_DB);
                    Object mongoUser = mongoCredential.get(Configuration.MONGO_USER);
                    Object mongoPwd = mongoCredential.get(Configuration.MONGO_PASSWORD);

                    if (mongoAuthDb != null && mongoAuthDb instanceof String && mongoUser != null && mongoUser instanceof String && mongoPwd != null && mongoPwd instanceof String)
                        credentials.add(MongoCredential.createMongoCRCredential((String) mongoUser, (String) mongoAuthDb, ((String)mongoPwd).toCharArray()));
                }
            }
        
            MongoClientOptions opts = MongoClientOptions.builder().readPreference(ReadPreference.primaryPreferred()).writeConcern(WriteConcern.ACKNOWLEDGED).build();
            
            mongoClient = new MongoClient(servers, credentials, opts); 
        }
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