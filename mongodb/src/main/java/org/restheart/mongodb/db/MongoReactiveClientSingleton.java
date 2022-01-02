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

import com.mongodb.Block;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.connection.ConnectionPoolSettings;
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
        // in case of error, e.g. invalid mongo uri, it's null
        initialized = uri != null;
    }

    /**
     * alias for getInstance()
     * @return the MongoReactiveClientSingleton
     */
    public static MongoReactiveClientSingleton get() {
        return getInstance();
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
            // TODO add minSize and maxSize to configuration
            var settings = MongoClientSettings.builder()
                .applyToConnectionPoolSettings(new Block<ConnectionPoolSettings.Builder>() {
                    @Override
                    public void apply(final ConnectionPoolSettings.Builder builder) {
                        builder.minSize(64).maxSize(512);
                    }})
                .applicationName("restheart (reactivestreams)")
                .applyConnectionString(mongoUri)
                .build();

            mongoClient = MongoClients.create(settings);
        }
    }

    /**
     * alias for getClient()
     * @return the MongoClient
     */
    public MongoClient client() {
        return getClient();
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

        private static final MongoReactiveClientSingleton INSTANCE;

        // make sure the Singleton is a Singleton even in a multi-classloader environment
        // credits to https://stackoverflow.com/users/145989/ondra-Žižka
        // https://stackoverflow.com/a/47445573/4481670
        static {
            // There should be just one system class loader object in the whole JVM.
            synchronized(ClassLoader.getSystemClassLoader()) {
                var sysProps = System.getProperties();
                // The key is a String, because the .class object would be different across classloaders.
                var singleton = (MongoReactiveClientSingleton) sysProps.get(MongoReactiveClientSingleton.class.getName());

                // Some other class loader loaded MongoClientSingleton earlier.
                if (singleton != null) {
                    INSTANCE = singleton;
                }
                else {
                    // Otherwise this classloader is the first one, let's create a singleton.
                    // Make sure not to do any locking within this.
                    INSTANCE = new MongoReactiveClientSingleton();
                    System.getProperties().put(MongoClientSingleton.class.getName(), INSTANCE);
                }
            }
        }
    }
}
