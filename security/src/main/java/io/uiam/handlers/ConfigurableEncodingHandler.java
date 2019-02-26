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

import io.uiam.handlers.exchange.AbstractExchange;
import io.uiam.handlers.exchange.ByteArrayResponse;
import java.util.Arrays;

import io.uiam.utils.HttpStatus;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.DeflateEncodingProvider;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ConfigurableEncodingHandler extends EncodingHandler {

    private final ResponseSender sender
            = new ResponseSender(null);

    private boolean forceCompression = false;

    /**
     * Creates a new instance of GzipEncodingHandler
     *
     * @param next
     * @param forceCompression if true requests without gzip or deflate encoding
     * in Accept-Encoding header will be rejected
     */
    public ConfigurableEncodingHandler(HttpHandler next, boolean forceCompression) {
        super(next, new ContentEncodingRepository()
                .addEncodingHandler("gzip", new GzipEncodingProvider(), 60)
                .addEncodingHandler("deflate", new DeflateEncodingProvider(), 50));

        this.forceCompression = forceCompression;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (forceCompression) {
            HeaderValues acceptedEncodings = exchange
                    .getRequestHeaders()
                    .get(Headers.ACCEPT_ENCODING_STRING);

            for (String values : acceptedEncodings) {
                if (Arrays.stream(values.split(",")).anyMatch((v)
                        -> Headers.GZIP.toString().equals(v)
                        || Headers.DEFLATE.toString().equals(v))) {
                    super.handleRequest(exchange);
                    return;
                }
            }

            AbstractExchange.setInError(exchange);
            ByteArrayResponse.wrap(exchange).endExchangeWithMessage(
                    HttpStatus.SC_BAD_REQUEST,
                    "Accept-Encoding header must include gzip or deflate");

            sender.handleRequest(exchange);
        } else {
            super.handleRequest(exchange);
        }
    }
}
