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
package org.restheart.plugins.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bson.BsonDocument;
import org.restheart.utils.LogUtils;
import org.restheart.utils.LogUtils.Level;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterInitializer(
        name = "logMessageInitializer", 
        priority = 100, 
        description = "An initializer that logs the message specified in the configuration file.")
public class LogMessageInitializer implements Initializer {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(LogMessageInitializer.class);

    @Override
    public void init(BsonDocument confArgs) {
        
        try {
            LogUtils.log(
                    LOGGER, 
                    logLevelArg(confArgs), 
                    "{}", 
                    messageArg(confArgs));

        } catch(IllegalArgumentException iae) {
            LOGGER.warn("{}", iae.getMessage());
        }
    }
    
    private static Level logLevelArg(BsonDocument confArgs) {
        Level level = Level.INFO;
        
        if (confArgs != null
                && confArgs.containsKey("log-level")
                && confArgs.get("log-level").isString()) {

            try {
                level = LogUtils.Level.valueOf(confArgs.get("log-level").asString().getValue());
            } catch (Throwable t) {
            }
        }
        
        return level;
    }
    
    private static String messageArg(BsonDocument confArgs) throws IllegalArgumentException {
        String message;
        
        if (confArgs != null
                && confArgs.containsKey("message")
                && confArgs.get("message").isString()) {
            message = confArgs.get("message").asString().getValue();
        } else {
            throw new IllegalArgumentException("Wrong configuration: "
                    + "missing 'message' configuration argument");
        }
        
        return message;
    }
}
