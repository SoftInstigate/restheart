/*
 * uIAM - the IAM for microservices
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
package io.uiam.plugins.service.impl;

import static io.uiam.plugins.ConfigurablePlugin.argValue;

import java.util.Map;

import io.uiam.handlers.ExchangeHelper;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.plugins.service.PluggableService;
import io.uiam.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PingService extends PluggableService {

    private final String msg;

    /**
     *
     * @param next
     * @param args
     */
    public PingService(PipedHttpHandler next,
            String name,
            String uri,
            Boolean secured,
            Map<String, Object> args)
            throws Exception {
        super(next, name, uri, secured, args);
        this.msg = argValue(args, "msg");
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var hex = new ExchangeHelper(exchange);

        if (hex.isGet()) {
            exchange.setStatusCode(HttpStatus.SC_OK);
            exchange.getResponseSender().send(msg);
            exchange.endExchange();
        } else {
            exchange.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
            exchange.endExchange();
        }
    }
}
