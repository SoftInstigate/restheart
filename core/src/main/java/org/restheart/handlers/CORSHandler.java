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

import com.google.common.net.HttpHeaders;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import static io.undertow.util.Headers.LOCATION_STRING;
import static io.undertow.util.Headers.ORIGIN;
import io.undertow.util.HttpString;
import static org.restheart.handlers.CORSHandler.CORSHeaders.ACCESS_CONTROL_ALLOW_CREDENTIAL;
import static org.restheart.handlers.CORSHandler.CORSHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.restheart.handlers.CORSHandler.CORSHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_VALID_HEADER;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * The Access-Control-Expose-Headers header indicates which headers are safe to
 * expose to the API of a CORS API specification.
 *
 */
public class CORSHandler extends PipelinedHandler {

    public static final String ALL_ORIGINS = "*";

    public static void injectAccessControlAllowHeaders(HttpServerExchange exchange) {
        HeaderMap requestHeaders = exchange.getRequestHeaders();
        HeaderMap responseHeaders = exchange.getResponseHeaders();

        if (!responseHeaders.contains(ACCESS_CONTROL_ALLOW_ORIGIN)) {
            if (requestHeaders.contains(ORIGIN)) {

                responseHeaders.add(ACCESS_CONTROL_ALLOW_ORIGIN,
                        requestHeaders.get(ORIGIN).getFirst());
            } else {
                responseHeaders.add(ACCESS_CONTROL_ALLOW_ORIGIN, ALL_ORIGINS);
            }
        }

        if (!responseHeaders.contains(ACCESS_CONTROL_ALLOW_CREDENTIAL)) {
            responseHeaders.add(ACCESS_CONTROL_ALLOW_CREDENTIAL, "true");
        }

        if (!responseHeaders.contains(ACCESS_CONTROL_EXPOSE_HEADERS)) {
            responseHeaders.add(ACCESS_CONTROL_EXPOSE_HEADERS,
                    LOCATION_STRING + ", " + Headers.ETAG + ", "
                    + AUTH_TOKEN_HEADER.toString() + ", "
                    + AUTH_TOKEN_VALID_HEADER.toString() + ", "
                    + AUTH_TOKEN_LOCATION_HEADER.toString() + ", "
                    + HttpHeaders.X_POWERED_BY);
        }
    }

    /**
     * Creates a new instance of CORSHandler
     *
     */
    public CORSHandler() {
        super();
    }

    /**
     * Creates a new instance of CORSHandler
     *
     * @param next
     */
    public CORSHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        injectAccessControlAllowHeaders(exchange);

        next(exchange);
    }

    interface CORSHeaders {
        HttpString ACCESS_CONTROL_EXPOSE_HEADERS = HttpString.tryFromString("Access-Control-Expose-Headers");
        HttpString ACCESS_CONTROL_ALLOW_CREDENTIAL = HttpString.tryFromString("Access-Control-Allow-Credentials");
        HttpString ACCESS_CONTROL_ALLOW_ORIGIN = HttpString.tryFromString("Access-Control-Allow-Origin");
        HttpString ACCESS_CONTROL_ALLOW_METHODS = HttpString.tryFromString("Access-Control-Allow-Methods");
    }
}
