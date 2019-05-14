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
package org.restheart.plugins.services;

import org.restheart.plugins.Service;
import io.undertow.server.HttpServerExchange;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.handlers.RequestContext;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "pingService",
        description = "Ping service")
public class PingService extends Service {

    private final String msg;

    /**
     *
     * @param confArgs arguments optionally specified in the configuration file
     */
    public PingService(Map<String, Object> confArgs) {
        super(confArgs);

        this.msg =  confArgs != null  && confArgs.containsKey("msg") 
                ? (String) confArgs.get("msg") 
                : "ping";
    }
    
    @Override
    public String defaultUri() {
        return "/ping";
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.isOptions()) {
            handleOptions(exchange, context);
        } else if (context.isGet()) {
            context.setResponseContent(new BsonDocument("msg",
                    new BsonString(msg)));
            context.setResponseStatusCode(HttpStatus.SC_OK);
        } else {
            context.setResponseStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        }
        
        next(exchange, context);
    }
}
