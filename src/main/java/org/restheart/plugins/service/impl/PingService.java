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
package org.restheart.plugins.service.impl;

import org.restheart.plugins.service.Service;
import io.undertow.server.HttpServerExchange;
import java.util.Map;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.plugins.service.RegisterService;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterService(name = "pingService",
        description = "Ping service")
public class PingService extends Service {

    private final String msg;

    /**
     *
     * @param confArgs arguments optionally specified in the configuration file
     */
    public PingService(Map<String, Object> confArgs) {
        super(confArgs);

        this.msg =  confArgs.containsKey("msg") 
                ? (String) confArgs.get("msg") 
                : "ping";
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.getMethod() == METHOD.GET) {
            exchange.setStatusCode(HttpStatus.SC_OK);
            exchange.getResponseSender().send(msg);
            exchange.endExchange();
        } else {
            exchange.setStatusCode(HttpStatus.SC_OK);
            if (context.getContent() != null) {
                exchange.getResponseSender().send(context.getContent().toString());
            }
            exchange.endExchange();
        }
    }
}
