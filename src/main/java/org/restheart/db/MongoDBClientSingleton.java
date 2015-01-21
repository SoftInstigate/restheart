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
package org.restheart.db;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import org.restheart.Configuration;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class MongoDBClientSingleton {

    private static boolean initialized = false;

    private static transient List<Map<String, Object>> mongoServers;
    private static transient List<Map<String, Object>> mongoCredentials;

    private MongoClient mongoClient;

    private static Logger logger = LoggerFactory.getLogger(MongoDBClientSingleton.class);

    private MongoDBClientSingleton() {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }

        try {
            setup();
        } catch (UnknownHostException ex) {
            logger.error("error initializing mongodb client", ex);
        } catch (Throwable tr) {
            logger.error("error initializing mongodb client", tr);
        }
    }

    /**
     *
     * @param conf
     */
    public static void init(Configuration conf) {
        mongoServers = conf.getMongoServers();
        mongoCredentials = conf.getMongoCredentials();

        if (mongoServers != null && !mongoServers.isEmpty()) {
            initialized = true;
        } else {
            logger.error("error initializing mongodb client, no servers found in configuration");
        }
    }

    private void setup() throws UnknownHostException {
        if (isInitialized()) {
            List<ServerAddress> servers = new ArrayList<>();
            List<MongoCredential> credentials = new ArrayList<>();

            for (Map<String, Object> mongoServer : mongoServers) {
                Object mongoHost = mongoServer.get(Configuration.MONGO_HOST_KEY);
                Object mongoPort = mongoServer.get(Configuration.MONGO_PORT_KEY);

                if (mongoHost != null && mongoHost instanceof String && mongoPort != null && mongoPort instanceof Integer) {
                    servers.add(new ServerAddress((String) mongoHost, (int) mongoPort));
                }
            }

            if (mongoCredentials != null) {
                for (Map<String, Object> mongoCredential : mongoCredentials) {
                    Object mongoAuthDb = mongoCredential.get(Configuration.MONGO_AUTH_DB_KEY);
                    Object mongoUser = mongoCredential.get(Configuration.MONGO_USER_KEY);
                    Object mongoPwd = mongoCredential.get(Configuration.MONGO_PASSWORD_KEY);

                    if (mongoAuthDb != null && mongoAuthDb instanceof String && mongoUser != null && mongoUser instanceof String && mongoPwd != null && mongoPwd instanceof String) {
                        credentials.add(MongoCredential.createMongoCRCredential((String) mongoUser, (String) mongoAuthDb, ((String) mongoPwd).toCharArray()));
                    }
                }
            }

            MongoClientOptions opts = MongoClientOptions.builder().readPreference(ReadPreference.primaryPreferred()).writeConcern(WriteConcern.ACKNOWLEDGED).build();

            mongoClient = new MongoClient(servers, credentials, opts);
        }
    }

    /**
     *
     * @return
     */
    public static MongoDBClientSingleton getInstance() {
        return MongoDBClientSingletonHolder.INSTANCE;
    }

    private static class MongoDBClientSingletonHolder {

        private static final MongoDBClientSingleton INSTANCE = new MongoDBClientSingleton();
    }

    /**
     *
     * @return
     */
    public MongoClient getClient() {
        if (this.mongoClient == null) {
            throw new IllegalStateException("mongo client not initialized");
        }

        return this.mongoClient;
    }

    /**
     * @return the initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
}
