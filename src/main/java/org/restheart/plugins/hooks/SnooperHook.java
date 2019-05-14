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
package org.restheart.plugins.hooks;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;
import org.restheart.plugins.Hook;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "snooper", 
        description = "An example hook that logs request and response info")
public class SnooperHook implements Hook {
    private static final Logger LOGGER
            = LoggerFactory.getLogger(SnooperHook.class);

    @Override
    public boolean hook(
            HttpServerExchange exchange,
            RequestContext context,
            BsonValue args,
            BsonDocument confArgs) {
        LOGGER.info("Request {} {} {}",
                context.getMethod(),
                exchange.getRequestURI(), exchange.getStatusCode());
        
        LOGGER.info("Metadata args {}",
                JsonUtils.toJson(args));

        LOGGER.info("Configuration args {}",
                JsonUtils.toJson(confArgs));
        
        if (context.getDbOperationResult() != null) {
            BsonValue newId = context
                    .getDbOperationResult()
                    .getNewId();
            
            BsonDocument newData = context
                    .getDbOperationResult()
                    .getNewData();

            BsonDocument oldData = context
                    .getDbOperationResult()
                    .getOldData();

            LOGGER.info("**** New id ****\n{}",
                    newId == null ? null : newId);
            
            LOGGER.info("**** New data ****\n{}",
                    newData == null ? null : newData.toJson());

            LOGGER.info("**** Old data ****\n{}",
                    oldData == null ? null : oldData.toJson());
        }

        BsonValue responseContent = context.getResponseContent();

        if (responseContent != null) {
            LOGGER.info("*** Response content ****\n{}",
                    JsonUtils.toJson(responseContent));
        }

        return true;
    }

    @Override
    public boolean doesSupportRequests(RequestContext context) {
        return true;
    }
}
