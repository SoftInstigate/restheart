/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.handlers;

import org.restheart.security.handlers.exchange.AbstractExchange;
import org.restheart.security.handlers.exchange.ByteArrayResponse;
import java.util.Arrays;

import org.restheart.security.utils.HttpStatus;
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
