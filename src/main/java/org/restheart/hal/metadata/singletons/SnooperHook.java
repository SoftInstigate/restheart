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
package org.restheart.hal.metadata.singletons;

import com.mongodb.DBObject;
import com.mongodb.util.JSONSerializers;
import com.mongodb.util.ObjectSerializer;
import io.undertow.server.HttpServerExchange;
import org.bson.Document;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SnooperHook implements Hook {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnooperHook.class);
    private final static ObjectSerializer SERIALIZER = JSONSerializers.getStrict();

    @Override
    public boolean hook(HttpServerExchange exchange, RequestContext context, DBObject args) {
        LOGGER.info("Request {} {} {}", 
                context.getMethod(), 
                exchange.getRequestURI(), exchange.getStatusCode());

        if (context.getDbOperationResult() != null) {
            Document newData = context.getDbOperationResult().getNewData();
            Document oldData = context.getDbOperationResult().getOldData();
            
            LOGGER.info("**** New data ****\n{}", newData == null ? null : newData.toJson());
            LOGGER.info("**** Old data ****\n{}", oldData == null ? null : oldData.toJson());
        }
        
        DBObject responseContent = context.getResponseContent();
        
        if (responseContent != null) {
            LOGGER.info("*** Response content ****\n{}", SERIALIZER.serialize(responseContent));
        }

        return true;
    }

    @Override
    public boolean doesSupportRequests(RequestContext context) {
        return true;
    }
}