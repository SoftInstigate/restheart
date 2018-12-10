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

import io.uiam.handlers.PipedHttpHandler;
import io.uiam.plugins.service.PluggableService;
import io.uiam.utils.ChannelReader;
import io.uiam.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class EchoService extends PluggableService {
    /**
     *
     * @param next
     * @param name
     * @param uri
     * @param secured
     */
    public EchoService(PipedHttpHandler next, String name, String uri, Boolean secured) {
        super(next, name, uri, secured, null);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.setStatusCode(HttpStatus.SC_OK);

        var msg = new StringBuffer();

        msg.append("Method: ");
        msg.append(exchange.getRequestMethod().toString());
        msg.append("\n");

        msg.append("URL: ");
        msg.append(exchange.getRequestURL());
        msg.append("\n\n");

        msg.append("Body\n");

        msg.append(ChannelReader.read(exchange.getRequestChannel()));

        msg.append("\n\n");

        msg.append("Query Parameters\n");

        exchange.getQueryParameters().forEach((name, values) -> {
            msg.append("\t");
            msg.append(name);
            msg.append(": ");

            values.iterator().forEachRemaining(value -> {
                msg.append(value);
                msg.append(",");
            });

            msg.delete(msg.length() - 1, msg.length());

            msg.append("\n");
        });

        msg.append("\nHeaders\n");

        exchange.getRequestHeaders().forEach(header -> {
            msg.append("\t");
            msg.append(header.getHeaderName().toString());
            msg.append(": ");

            header.iterator().forEachRemaining(value -> {
                msg.append(value);
                msg.append(",");
            });

            msg.delete(msg.length() - 1, msg.length());

            msg.append("\n");
        });

        exchange.getResponseSender().send(msg.toString());
        exchange.endExchange();
    }
}
