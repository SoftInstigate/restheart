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
package org.restheart.mongodb;

import java.util.Map;
import static org.fusesource.jansi.Ansi.Color.MAGENTA;
import static org.fusesource.jansi.Ansi.ansi;
import org.restheart.ConfigurationException;
import org.restheart.mongodb.db.MongoDBClientSingleton;
import org.restheart.mongodb.handlers.injectors.LocalCachesSingleton;
import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name="mongoDbInitializer",
        description = "",
        initPoint = InitPoint.BEFORE_STARTUP,
        priority = 10)
public class MongoServiceInitializer implements Initializer {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(MongoService.class);
    
    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public MongoServiceInitializer(Map<String, Object> confArgs) {
        MongoServiceConfiguration.init(confArgs);
    }
    
    @Override
    public void init() {
        // initialize LocalCachesSingleton
        LocalCachesSingleton.init(MongoServiceConfiguration.get());
        
        // initialize MongoDBClientSingleton
        try {
            MongoDBClientSingleton.init(MongoServiceConfiguration.get().getMongoUri());

            LOGGER.info("Connecting to MongoDB...");

            // force connection to MongoDB
             var mclient = MongoDBClientSingleton.getInstance();

            LOGGER.info("MongoDB version {}",
                    ansi()
                            .fg(MAGENTA)
                            .a(mclient.getServerVersion())
                            .reset()
                            .toString());

            if (mclient.isReplicaSet()) {
                LOGGER.info("MongoDB is a replica set");
            } else {
                LOGGER.warn("MongoDB is a standalone instance, use a replica set in production");
            }

        } catch (Throwable t) {
            throw new ConfigurationException("\"Error connecting to MongoDB.");
        }
    }
    
}
