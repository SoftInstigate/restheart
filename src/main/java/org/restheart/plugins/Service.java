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
package org.restheart.plugins;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.Map;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.ResponseSenderHandler;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class Service extends PipedHttpHandler implements Plugin {
    /**
     * The configuration properties passed to this handler.
     */
    protected final Map<String, Object> confArgs;

    /**
     * Creates a new instance of the Service
     *
     * @param confArgs arguments optionally specified in the configuration file
     */
    public Service(Map<String, Object> confArgs) {
        super(new ResponseSenderHandler());
        this.confArgs = confArgs;
    }

    /**
     *
     * @return the default uri of the service, used if not specified in plugin
     * configuration
     */
    public String defaultUri() {
        return null;
    }

    /**
     * helper method to handle OPTIONS requests
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    protected void handleOptions(
            HttpServerExchange exchange,
            RequestContext context)
            throws Exception {
        exchange.getResponseHeaders()
                .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, PUT, POST, PATCH, DELETE, OPTIONS")
                .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Accept, Accept-Encoding, Authorization, Content-Length, Content-Type, Host, If-Match, Origin, X-Requested-With, User-Agent, No-Auth-Challenge");
        exchange.setStatusCode(HttpStatus.SC_OK);
        exchange.endExchange();
    }
}
