package org.restheart.mongodb;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;

import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.restheart.cache.Cache.EXPIRE_POLICY;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.restheart.utils.BsonUtils.document;

public class ConnectionChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionChecker.class);

    private static LoadingCache<MongoClient, Boolean> CACHE = CacheFactory.createLocalLoadingCache(10, EXPIRE_POLICY.AFTER_WRITE, 5_000, mclient -> {
        if (mclient == null) {
            return false;
        }

        try {
            mclient.getDatabase("admin").runCommand(document().put("ping", 1).get());
            return true;
        } catch(Throwable t) {
            return false;
        }
    });

    public static boolean connected(MongoClient mclient) {
        return CACHE.getLoading(mclient).orElse(false);
    }


     /**
     * @throws MongoTimeoutException
     * @return the initialized
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
