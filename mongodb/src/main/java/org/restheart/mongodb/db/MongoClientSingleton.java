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

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.MAGENTA;
import static org.fusesource.jansi.Ansi.Color.RED;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.Block;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.restheart.plugins.PluginsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.restheart.mongodb.ConnectionChecker.connected;
import static org.restheart.mongodb.ConnectionChecker.replicaSet;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoClientSingleton {

    private static boolean initialized = false;
    private static ConnectionString mongoUri;
    private static PluginsRegistry pluginsRegistry;
    private String serverVersion = null;

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoClientSingleton.class);

    /**
     *
     * @param uri
     * @param pr
     */
    public static void init(ConnectionString uri, PluginsRegistry pr) {
        mongoUri = uri;
        pluginsRegistry = pr;
        initialized = true;
    }

    /**
     * @return the initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }


    /**
     * alias for getInstance()
     * @return the MongoClientSingleton
     */
    public static MongoClientSingleton get() {
        return getInstance();
    }

    /**
     *
     * @return
     */
    public static MongoClientSingleton getInstance() {
        return MongoClientSingletonHolder.INSTANCE;
    }

    /**
     * @return the serverVersion
     */
    public String getServerVersion() {
        return serverVersion;
    }

    private MongoClient mclient;

    private MongoClientSingleton() {
        if (!initialized) {
            throw new IllegalStateException("not initialized");
        }
    }

    private void setup() {
        if (!initialized) {
            throw new IllegalStateException("MongoClientSingleton is not initialized");
        }

        LOGGER.info("Connecting to MongoDB...");

        // TODO add minSize and maxSize to configuration
        var settings = MongoClientSettings.builder()
            .applyToConnectionPoolSettings(new Block<ConnectionPoolSettings.Builder>() {
                @Override
                public void apply(final ConnectionPoolSettings.Builder builder) {
                    // default values: min=0 and max=100
                    builder.minSize(64).maxSize(512);
                }})
            .applicationName("restheart (sync)")
            .applyConnectionString(mongoUri)
            .build();

        mclient = MongoClients.create(settings);

        // this is the first time we check the connection
        if (connected(mclient)) {
            // get the db version
            try {
                var res = mclient.getDatabase("admin").runCommand(new BsonDocument("buildInfo", new BsonInt32(1)));

                var _version = res.get("version");

                if (_version != null && _version instanceof String) {
                    serverVersion = (String) _version;
                } else {
                    LOGGER.warn("Cannot get the MongoDB version.");
                    serverVersion = "?";
                }

                LOGGER.info("MongoDB version {}", ansi()
                    .fg(MAGENTA)
                    .a(getServerVersion())
                    .reset()
                    .toString());

                if (replicaSet(this.mclient)) {
                    LOGGER.info("MongoDB is a replica set.");
                } else {
                    LOGGER.warn("MongoDB is a standalone instance.");
                }

            } catch (Throwable t) {
                LOGGER.error(ansi().fg(RED).bold().a("Cannot connect to MongoDB. ").reset().toString()
                    + "Check that MongoDB is running and "
                    + "the configuration property 'mongo-uri' "
                    + "is set properly");
                serverVersion = "?";
            }
        } else {
            LOGGER.error(ansi().fg(RED).bold().a("Cannot connect to MongoDB. ").reset().toString()
                + "Check that MongoDB is running and "
                + "the configuration property 'mongo-uri' "
                + "is set properly");
            serverVersion = "?";
        }

        // invoke Plugins methods annotated with @InjectMongoClient
        // passing them the MongoClient
        if (pluginsRegistry != null) {
            pluginsRegistry.injectDependency(mclient);
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
        if (!initialized) {
            throw new IllegalStateException("MongoClientSingleton is not initialized");
        }

        if (this.mclient == null) {
            setup();
        }

        return this.mclient;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        // it is a singleton!
        return true;
    }

    private static class MongoClientSingletonHolder {
        private static final MongoClientSingleton INSTANCE;

        // make sure the Singleton is a Singleton even in a multi-classloader environment
        // credits to https://stackoverflow.com/users/145989/ondra-Žižka
        // https://stackoverflow.com/a/47445573/4481670
        static {
            // There should be just one system class loader object in the whole JVM.
            synchronized(ClassLoader.getSystemClassLoader()) {
                var sysProps = System.getProperties();
                // The key is a String, because the .class object would be different across classloaders.
                var singleton = (MongoClientSingleton) sysProps.get(MongoClientSingleton.class.getName());

                // Some other class loader loaded MongoClientSingleton earlier.
                if (singleton != null) {
                    INSTANCE = singleton;
                }
                else {
                    // Otherwise this classloader is the first one, let's create a singleton.
                    // Make sure not to do any locking within this.
                    INSTANCE = new MongoClientSingleton();
                    System.getProperties().put(MongoClientSingleton.class.getName(), INSTANCE);
                }
            }
        }
    }
}
