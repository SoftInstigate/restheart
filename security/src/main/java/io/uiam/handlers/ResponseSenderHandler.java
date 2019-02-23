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

import io.uiam.utils.BuffersUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.nio.ByteBuffer;

/**
 *
 * sends the response content attached to HttpServerExchange to the client
 *
 * @see ExchangeHelper.getResponseContentAsJson() and
 * ExchangeHelper.getResponseContent()
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseSenderHandler extends PipedHttpHandler {

    /**
     *
     */
    public ResponseSenderHandler() {
        super(null);
    }

    /**
     * @param next
     */
    public ResponseSenderHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var response = Response.wrap(exchange);

        if (!exchange.isResponseStarted()) {
            exchange.setStatusCode(response.getStatusCode());
        }

        if (response.getContentAsJson() != null) {
            exchange.getResponseHeaders().add(
                    Headers.CONTENT_TYPE,
                    "application/json");

            exchange.getResponseSender().send(response
                    .getContentAsJson()
                    .toString());
        } else if (response.getContent() != null) {
            if (response.getContentType() != null) {
                exchange.getResponseHeaders().add(
                        Headers.CONTENT_TYPE,
                        response.getContentType());
            }

            exchange.getResponseSender().send(
                    BuffersUtils.toByteBuffer(response.getContent()));
        }

        exchange.endExchange();

        next(exchange);
    }
}
