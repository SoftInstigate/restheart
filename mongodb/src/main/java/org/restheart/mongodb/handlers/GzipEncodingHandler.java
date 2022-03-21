/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
package org.restheart.mongodb.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import java.util.Arrays;
import org.restheart.exchange.MongoResponse;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GzipEncodingHandler extends EncodingHandler {

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

    /**
     *
     * @param exchange
     * @throws Exception
     */
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

            MongoResponse.of(exchange).setInError(
                    HttpStatus.SC_BAD_REQUEST,
                    "Accept-Encoding header must include gzip");
        } else {
            super.handleRequest(exchange);
        }
    }
}
