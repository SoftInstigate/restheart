/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
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

import com.mongodb.CommandResult;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import java.net.UnknownHostException;
import org.restheart.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoDBClientSingleton {

    private static boolean initialized = false;

    private static MongoClientURI mongoUri;
    private static MongoClient outsideMongoClient;

    private static String serverVersion;

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDBClientSingleton.class);

    /**
     *
     * @param conf
     */
    public static void init(Configuration conf) {
        mongoUri = conf.getMongoUri();
        initialized = true;
    }

    public static void init(MongoClient outsideMongoClient) {
        initialized = true;
        MongoDBClientSingleton.outsideMongoClient = outsideMongoClient;
        serverVersion = "?";
    }

    /**
     *
     * @return
     */
    public static MongoDBClientSingleton getInstance() {
        return MongoDBClientSingletonHolder.INSTANCE;
    }

    /**
     * @return the initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * @return the serverVersion
     */
    public static String getServerVersion() {
        return serverVersion;
    }
    private MongoClient mongoClient;

    private MongoDBClientSingleton() {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }

        try {
            setup(outsideMongoClient);
        } catch (UnknownHostException ex) {
            LOGGER.error("error initializing mongodb client", ex);
        } catch (Throwable tr) {
            LOGGER.error("error initializing mongodb client", tr);
        }
    }

    private void setup(MongoClient outsideMongoClient) throws UnknownHostException {
        if (isInitialized()) {

            mongoClient = outsideMongoClient != null ? outsideMongoClient : new MongoClient(mongoUri);
        }

        try {
            CommandResult res = mongoClient.getDB("admin").command("buildInfo");
            Object _version = res.get("version");

            if (_version != null && _version instanceof String) {
                serverVersion = (String) _version;
            } else {
                LOGGER.warn("Cannot get the MongoDb version.");
                serverVersion = "3.x?";
            }
        } catch (Throwable t) {
            LOGGER.warn("Cannot get the MongoDb version.");
            serverVersion = "?";
        }
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

    private static class MongoDBClientSingletonHolder {

        private static final MongoDBClientSingleton INSTANCE = new MongoDBClientSingleton();

        private MongoDBClientSingletonHolder() {
        }
    }
}
