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

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoCommandException;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.restheart.plugins.PluginsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoClientSingleton {

    private static boolean initialized = false;
    private static MongoClientURI mongoUri;
    private static PluginsRegistry pluginsRegistry;
    private String serverVersion = null;
    private boolean replicaSet = false;

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoClientSingleton.class);

    /**
     *
     * @param uri
     * @param pr
     */
    public static void init(MongoClientURI uri, PluginsRegistry pr) {
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
     *
     * @return
     */
    public static MongoClientSingleton getInstance() {
        return MongoClientSingletonHolder.INSTANCE;
    }

    /**
     * @return the initialized
     */
    public Boolean isReplicaSet() {
        return replicaSet;
    }

    /**
     * @return the serverVersion
     */
    public String getServerVersion() {
        return serverVersion;
    }

    private MongoClient mongoClient;

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

        mongoClient = new MongoClient(mongoUri);

        // invoke Plugins methods annotated with @InjectMongoClient
        // passing them the MongoClient
        if (pluginsRegistry != null) {
            pluginsRegistry.injectDependency(mongoClient);
        }

        // get the db version
        // this also is the first time we check the connection
        try {
            var res = mongoClient.getDatabase("admin").runCommand(new BsonDocument("buildInfo", new BsonInt32(1)));

            var _version = res.get("version");

            if (_version != null && _version instanceof String) {
                serverVersion = (String) _version;
            } else {
                LOGGER.warn("Cannot get the MongoDB version.");
                serverVersion = "?";
            }

            // check if db is configured as replica set
            try {
                // this throws an exception if not running as replica set
                mongoClient.getDatabase("admin")
                        .runCommand(new BsonDocument("replSetGetStatus",
                                new BsonInt32(1)));
                replicaSet = true;
            } catch (MongoCommandException mce) {
                if (mce.getCode() == 13) { // Unauthorized
                    LOGGER.warn("Unable to check if MongoDB is configured as replica set. "
                            + "The MongoDB user cannot execute replSetGetStatus() command. "
                            + "Tip: add to the MongoDB user the built-in role 'clusterMonitor' that provides this action.");
                }

                replicaSet = false;
            } catch (Throwable t) {
                replicaSet = false;
            }

            LOGGER.info("MongoDB version {}",
                    ansi()
                        .fg(MAGENTA)
                        .a(getServerVersion())
                        .reset()
                        .toString());

            if (isReplicaSet()) {
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
            replicaSet = false;
        }
    }

    /**
     *
     * @return
     */
    public MongoClient getClient() {
        if (!initialized) {
            throw new IllegalStateException("MongoClientSingleton is not initialized");
        }

        if (this.mongoClient == null) {
            setup();
        }

        return this.mongoClient;
    }

    private static class MongoClientSingletonHolder {

        private static final MongoClientSingleton INSTANCE = new MongoClientSingleton();

        private MongoClientSingletonHolder() {
        }
    }
}
