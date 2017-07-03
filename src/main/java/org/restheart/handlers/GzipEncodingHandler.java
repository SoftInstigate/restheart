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
package org.restheart.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import java.util.Arrays;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GzipEncodingHandler extends EncodingHandler {

    private final ResponseSenderHandler sender = new ResponseSenderHandler(null);

    private boolean forceCompression = false;

    /**
     * Creates a new instance of GzipEncodingHandler
     *
     * @param next
     * @param forceCompression if true requests without gzip encoding in
     * Accept-Encoding header will be rejected
     */
    public GzipEncodingHandler(HttpHandler next, boolean forceCompression) {
        super(next, new ContentEncodingRepository().addEncodingHandler("gzip", new GzipEncodingProvider(), 50));
        this.forceCompression = forceCompression;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (forceCompression) {
            HeaderValues acceptedEncodings = exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING_STRING);

            for (String values : acceptedEncodings) {
                if (Arrays.stream(values.split(",")).anyMatch((v) -> Headers.GZIP.toString().equals(v))) {
                    super.handleRequest(exchange);
                    return;
                }
            }

            RequestContext errorContext = new RequestContext(exchange, "/", "_error");

            ResponseHelper.endExchangeWithMessage(
                    exchange,
                    errorContext,
                    HttpStatus.SC_BAD_REQUEST,
                    "Accept-Encoding header must include gzip");

            sender.handleRequest(exchange, errorContext);
        } else {
            super.handleRequest(exchange);
        }
    }
}
