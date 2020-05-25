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
package org.restheart.mongodb.db;

import com.mongodb.MongoClientURI;
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

    private static MongoClientURI mongoUri;

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoReactiveClientSingleton.class);

    /**
     *
     * @param uri
     */
    public static void init(MongoClientURI uri) {
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
