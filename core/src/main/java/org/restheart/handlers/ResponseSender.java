/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import java.nio.ByteBuffer;
import org.restheart.handlers.exchange.ByteArrayResponse;

/**
 *
 * Sends the response content attached to ByteArrayResponse to the client
 *
 * The response content of ByteArrayResponse can be set by any ProxableResponse
 * implementation (eg. JsonResponse)
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseSender extends PipelinedHandler {

    /**
     *
     */
    public ResponseSender() {
        super(null);
    }

    /**
     * @param next
     */
    public ResponseSender(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var response = ByteArrayResponse.wrap(exchange);

        if (!exchange.isResponseStarted() && response.getStatusCode() > 0) {
            exchange.setStatusCode(response.getStatusCode());
        }

        if (response.isContentAvailable()) {
            exchange.getResponseSender().send(
                    ByteBuffer.wrap(response.readContent()));
        }

        exchange.endExchange();

        next(exchange);
    }
}
