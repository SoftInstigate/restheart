/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.db;

import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoReactiveClientSingleton {

    private static boolean initialized = false;

    private static ConnectionString mongoUri;

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoReactiveClientSingleton.class);

    /**
     *
     * @param uri
     */
    public static void init(ConnectionString uri) {
        mongoUri = uri;
        initialized = true;
    }

    /**
     *
     * @return
     */
    public static MongoReactiveClientSingleton getInstance() {
        return MongoDBClientSingletonHolder.INSTANCE;
    }

    /**
     * @return the initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    private MongoClient mongoClient;

    private MongoReactiveClientSingleton() {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }

        try {
            setup();
        } catch (UnknownHostException ex) {
            LOGGER.error("error initializing reactive mongodb client", ex);
        } catch (Throwable tr) {
            LOGGER.error("error initializing reactive mongodb client", tr);
        }
    }

    private void setup() throws UnknownHostException {
        if (isInitialized()) {
            mongoClient = MongoClients.create(mongoUri.toString());
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

        private static final MongoReactiveClientSingleton INSTANCE = new MongoReactiveClientSingleton();

        private MongoDBClientSingletonHolder() {
        }
    }
}
