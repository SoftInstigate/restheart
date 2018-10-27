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
package io.uiam.handlers.security;

import com.google.common.net.HttpHeaders;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import static io.undertow.util.Headers.LOCATION_STRING;
import static io.undertow.util.Headers.ORIGIN;
import io.undertow.util.HttpString;
import static java.lang.Boolean.TRUE;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.RequestContext;
import static io.uiam.handlers.security.CORSHandler.CORSHeaders.ACCESS_CONTROL_ALLOW_CREDENTIAL;
import static io.uiam.handlers.security.CORSHandler.CORSHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.uiam.handlers.security.CORSHandler.CORSHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;
import static io.uiam.handlers.security.IAuthToken.AUTH_TOKEN_HEADER;
import static io.uiam.handlers.security.IAuthToken.AUTH_TOKEN_LOCATION_HEADER;
import static io.uiam.handlers.security.IAuthToken.AUTH_TOKEN_VALID_HEADER;
import io.undertow.util.HeaderMap;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * The Access-Control-Expose-Headers header indicates which headers are safe to
 * expose to the API of a CORS API specification.
 * 
 */
public class CORSHandler extends PipedHttpHandler {

    public static final String ALL_ORIGINS = "*";
    private final HttpHandler noPipedNext;

    /**
     * Creates a new instance of CORSHandler
     *
     * @param next
     */
    public CORSHandler(PipedHttpHandler next) {
        super(next);
        this.noPipedNext = null;
    }

    /**
     * Creates a new instance of GetRootHandler
     *
     * @param next
     */
    public CORSHandler(HttpHandler next) {
        super(null);
        this.noPipedNext = next;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        injectAccessControlAllowHeaders(exchange);

        if (noPipedNext != null) {
            noPipedNext.handleRequest(exchange);
        } else {
            next(exchange, context);
        }
    }
    

    private void injectAccessControlAllowHeaders(HttpServerExchange exchange) {
        HeaderMap requestHeaders = exchange.getRequestHeaders();
        HeaderMap responseHeaders = exchange.getResponseHeaders();
        
        if (requestHeaders.contains(ORIGIN)) {
            responseHeaders.add(ACCESS_CONTROL_ALLOW_ORIGIN, requestHeaders.get(ORIGIN).getFirst());
        } else {
            responseHeaders.add(ACCESS_CONTROL_ALLOW_ORIGIN, ALL_ORIGINS);
        }

        responseHeaders.add(ACCESS_CONTROL_ALLOW_CREDENTIAL, "true");

        responseHeaders.add(ACCESS_CONTROL_EXPOSE_HEADERS, LOCATION_STRING);
        responseHeaders.add(ACCESS_CONTROL_EXPOSE_HEADERS,
                LOCATION_STRING + ", "
                + Headers.ETAG + ", "
                + AUTH_TOKEN_HEADER.toString() + ", "
                + AUTH_TOKEN_VALID_HEADER.toString() + ", "
                + AUTH_TOKEN_LOCATION_HEADER.toString() + ", "
                + HttpHeaders.X_POWERED_BY);
    }

    interface CORSHeaders {
        HttpString ACCESS_CONTROL_EXPOSE_HEADERS = HttpString.tryFromString("Access-Control-Expose-Headers");
        HttpString ACCESS_CONTROL_ALLOW_CREDENTIAL = HttpString.tryFromString("Access-Control-Allow-Credentials");
        HttpString ACCESS_CONTROL_ALLOW_ORIGIN = HttpString.tryFromString("Access-Control-Allow-Origin");
    }
}
