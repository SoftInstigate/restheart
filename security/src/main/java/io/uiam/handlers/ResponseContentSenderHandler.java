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
package io.uiam.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 *
 * sends the response content attached to HttpServerExchange to the client
 *
 * @see ExchangeHelper.getResponseContentAsJson() and
 * ExchangeHelper.getResponseContent()
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseContentSenderHandler extends PipedHttpHandler {

    /**
     *
     */
    public ResponseContentSenderHandler() {
        super(null);
    }

    /**
     * @param next
     */
    public ResponseContentSenderHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var eh = new ExchangeHelper(exchange);

        if (!exchange.isResponseStarted()) {
            exchange.setStatusCode(eh.getResponseStatusCode());
        }

        if (eh.getResponseContentAsJson() != null) {
            exchange.getResponseHeaders().add(
                    Headers.CONTENT_TYPE,
                    "application/json");

            exchange.getResponseSender().send(eh
                    .getResponseContentAsJson()
                    .toString());
        } else if (eh.getResponseContent() != null) {
            if (eh.getResponseContentType() != null) {
                exchange.getResponseHeaders().add(
                        Headers.CONTENT_TYPE,
                        eh.getResponseContentType());
            }

            exchange.getResponseSender().send(eh.getResponseContent());
        }

        exchange.endExchange();

        next(exchange);
    }
}
