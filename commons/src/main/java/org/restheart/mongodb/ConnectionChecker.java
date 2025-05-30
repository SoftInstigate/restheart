/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.mongodb;

import org.restheart.cache.Cache.EXPIRE_POLICY;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import static org.restheart.utils.BsonUtils.document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;

/**
 * Utility class for checking MongoDB connection status and replica set configuration.
 * <p>
 * This class provides methods to verify if a MongoDB client is connected to a MongoDB instance
 * and whether the MongoDB instance is configured as a replica set. It uses a cache mechanism
 * to avoid frequent connection checks and improve performance.
 * </p>
 * 
 * <p>The connection status is cached for 5 seconds to reduce the overhead of repeated
 * connection checks. The cache can hold up to 10 different MongoClient instances.</p>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ConnectionChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionChecker.class);

    /**
     * Cache for storing connection status of MongoClient instances.
     * The cache expires entries 5 seconds after write to ensure fresh connection status.
     */
    private static LoadingCache<MongoClient, Boolean> CACHE = CacheFactory.createLocalLoadingCache(10, EXPIRE_POLICY.AFTER_WRITE, 5_000, mclient -> {
        if (mclient == null) {
            return false;
        }

        try {
            mclient.getDatabase("admin").runCommand(document().put("ping", 1).get());
            return true;
        } catch(Throwable t) {
            LOGGER.error("Error checking connection to MongoDB", t);
            return false;
        }
    });

    /**
     * Checks if the given MongoClient is connected to a MongoDB instance.
     * <p>
     * This method uses a cached result to avoid performing the connection check
     * on every call. The cache entry expires after 5 seconds, ensuring that
     * connection status is reasonably up-to-date.
     * </p>
     * 
     * @param mclient the MongoClient instance to check
     * @return {@code true} if the client is connected to MongoDB, {@code false} otherwise
     */
    public static boolean connected(MongoClient mclient) {
        return CACHE.getLoading(mclient).orElse(false);
    }


    /**
     * Checks if the MongoDB instance is configured as a replica set.
     * <p>
     * This method executes the {@code replSetGetStatus} command to determine if MongoDB
     * is running as part of a replica set. If the user lacks authorization to execute
     * this command, a warning is logged and the method returns {@code false}.
     * </p>
     * 
     * <p>Note: The MongoDB user must have the 'clusterMonitor' role to execute
     * the replSetGetStatus command. Without this permission, the method will
     * return {@code false} even if MongoDB is configured as a replica set.</p>
     * 
     * @param mclient the MongoClient instance to use for checking
     * @return {@code true} if MongoDB is configured as a replica set, {@code false} otherwise
     * @throws MongoTimeoutException if the client is not connected to MongoDB
     */
    public static boolean replicaSet(MongoClient mclient) {
        if (!connected(mclient)) {
            throw new MongoTimeoutException("not connected");
        }

        try {
            // this throws an exception if not running as replica set
            mclient.getDatabase("admin").runCommand(document().put("replSetGetStatus", 1).get());
            return true;
        } catch (MongoCommandException mce) {
            if (mce.getCode() == 13) { // Unauthorized
                LOGGER.warn("Unable to check if MongoDB is configured as replica set. The MongoDB user cannot execute replSetGetStatus() command. Tip: add to the MongoDB user the built-in role 'clusterMonitor' that provides this action.");
            }

            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
