/*
 * RESTHeart - the Web API for MongoDB
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
import com.mongodb.MongoClientURI;
import org.restheart.Configuration;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class MongoDBClientSingleton {
    private static boolean initialized = false;

    private static MongoClientURI mongoUri;

    private MongoClient mongoClient;

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDBClientSingleton.class);

    private MongoDBClientSingleton() {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }

        try {
            setup();
        } catch (UnknownHostException ex) {
            LOGGER.error("error initializing mongodb client", ex);
        } catch (Throwable tr) {
            LOGGER.error("error initializing mongodb client", tr);
        }
    }

    /**
     *
     * @param conf
     */
    public static void init(Configuration conf) {
        mongoUri = conf.getMongoUri();
        initialized = true;
    }

    private void setup() throws UnknownHostException {
        if (isInitialized()) {
            mongoClient = new MongoClient(mongoUri);
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
