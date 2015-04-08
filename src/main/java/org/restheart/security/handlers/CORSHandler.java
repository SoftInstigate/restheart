/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.security.handlers;

import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

import static org.restheart.security.handlers.CORSHandler.CORSHeaders.ACCESS_CONTROL_ALLOW_CREDENTIAL;
import static org.restheart.security.handlers.CORSHandler.CORSHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.restheart.security.handlers.CORSHandler.CORSHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;

import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_HEADER;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_VALID_HEADER;

import static io.undertow.util.Headers.LOCATION_STRING;
import static io.undertow.util.Headers.ORIGIN;
import static java.lang.Boolean.TRUE;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 *
 * The Access-Control-Expose-Headers header indicates which headers are safe to
 * expose to the API of a CORS API specification.
 */
public class CORSHandler extends PipedHttpHandler {

    public static final String ALL_ORIGINS = "*";
    private final HttpHandler noPipedNext;

    /**
     * Creates a new instance of GetRootHandler
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
        injectAccessControlAllowHeaders(new HeadersManager(exchange));

        if (noPipedNext != null) {
            noPipedNext.handleRequest(exchange);
        } else {
            getNext().handleRequest(exchange, context);
        }
    }

    private void injectAccessControlAllowHeaders(HeadersManager headers) {

        if (headers.isRequestHeaderSet(ORIGIN)) {
            headers.addResponseHeader(ACCESS_CONTROL_ALLOW_ORIGIN, headers.getRequestHeader(ORIGIN).getFirst());
        } else {
            headers.addResponseHeader(ACCESS_CONTROL_ALLOW_ORIGIN, ALL_ORIGINS);
        }

        headers.addResponseHeader(ACCESS_CONTROL_ALLOW_CREDENTIAL, TRUE);

        headers.addResponseHeader(ACCESS_CONTROL_EXPOSE_HEADERS, LOCATION_STRING);
        headers.addResponseHeader(ACCESS_CONTROL_EXPOSE_HEADERS,
                LOCATION_STRING + ", "
                + Headers.ETAG + ", "
                + AUTH_TOKEN_HEADER.toString() + ", "
                + AUTH_TOKEN_VALID_HEADER.toString() + ", "
                + AUTH_TOKEN_LOCATION_HEADER.toString());
    }

    interface CORSHeaders {
        HttpString ACCESS_CONTROL_EXPOSE_HEADERS = HttpString.tryFromString("Access-Control-Expose-Headers");
        HttpString ACCESS_CONTROL_ALLOW_CREDENTIAL = HttpString.tryFromString("Access-Control-Allow-Credentials");
        HttpString ACCESS_CONTROL_ALLOW_ORIGIN = HttpString.tryFromString("Access-Control-Allow-Origin");
    }
}
