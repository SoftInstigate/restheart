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
import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.exchange.ByteArrayProxyResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this closes the  a request 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ProxyExchangeBuffersCloser extends PipelinedHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyExchangeBuffersCloser.class);

    /**
     * Creates a new instance of ProxyExchangeBuffersCloser
     *
     */
    public ProxyExchangeBuffersCloser() {
        super();
    }

    /**
     * Creates a new instance of ProxyExchangeBuffersCloser
     *
     * @param next
     */
    public ProxyExchangeBuffersCloser(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addExchangeCompleteListener(
            (completeExchange, nextListener) -> {
                if (ByteArrayProxyRequest.of(exchange).isContentAvailable()) {
                    ByteArrayProxyRequest.of(exchange).close();
                    LOGGER.debug("release request content buffer");
                }

                if (ByteArrayProxyResponse.of(exchange).isContentAvailable()) {
                    ByteArrayProxyResponse.of(exchange).close();
                    LOGGER.debug("release response content buffer");
                }

                nextListener.proceed();
            });

        next(exchange);
    }
}
