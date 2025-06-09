/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.ContentEncodingRepository;
import io.undertow.server.handlers.encoding.DeflateEncodingProvider;
import io.undertow.server.handlers.encoding.EncodingHandler;
import io.undertow.server.handlers.encoding.GzipEncodingProvider;
import io.undertow.util.Headers;
import java.util.Arrays;

import org.restheart.Bootstrapper;
import org.restheart.exchange.ByteArrayProxyResponse;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ConfigurableEncodingHandler extends EncodingHandler {

    private final ResponseSender sender = new ResponseSender(null);

    private boolean forceCompression = false;

    /**
     * Creates a new instance of ConfigurableEncodingHandler
     *
     * if Configuration().isForceGzipEncoding() is true
     * requests without gzip or deflate encodingin Accept-Encoding header
     * will be rejected
     *
     * @param next
     */
    public ConfigurableEncodingHandler(HttpHandler next) {
        super(next, new ContentEncodingRepository()
                .addEncodingHandler("gzip", new GzipEncodingProvider(), 60)
                .addEncodingHandler("deflate", new DeflateEncodingProvider(), 50));

        this.forceCompression = Bootstrapper.getConfiguration().coreModule().forceGzipEncoding();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (forceCompression) {
            var acceptedEncodings = exchange.getRequestHeaders().get(Headers.ACCEPT_ENCODING_STRING);

            for (String values : acceptedEncodings) {
                if (Arrays.stream(values.split(",")).anyMatch((v) -> Headers.GZIP.toString().equals(v) || Headers.DEFLATE.toString().equals(v))) {
                    super.handleRequest(exchange);
                    return;
                }
            }

            Exchange.setInError(exchange);
            ByteArrayProxyResponse.of(exchange).setInError(HttpStatus.SC_BAD_REQUEST, "Accept-Encoding header must include gzip or deflate");

            sender.handleRequest(exchange);
        } else {
            super.handleRequest(exchange);
        }
    }
}
