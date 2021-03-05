/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.mongodb.handlers;

import com.google.common.net.HttpHeaders;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import static io.undertow.util.Headers.LOCATION_STRING;
import static io.undertow.util.Headers.ORIGIN;
import io.undertow.util.HttpString;
import static java.lang.Boolean.TRUE;
import org.restheart.handlers.PipelinedHandler;
import static org.restheart.mongodb.handlers.CORSHandler.CORSHeaders.ACCESS_CONTROL_ALLOW_CREDENTIAL;
import static org.restheart.mongodb.handlers.CORSHandler.CORSHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.restheart.mongodb.handlers.CORSHandler.CORSHeaders.ACCESS_CONTROL_EXPOSE_HEADERS;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * The Access-Control-Expose-Headers header indicates which headers are safe to
 * expose to the API of a CORS API specification.
 *
 * It also injects the X-Powered-By response header
 */
public class CORSHandler extends PipelinedHandler {

    /**
     *
     */
    public static final String ALL_ORIGINS = "*";
    private final HttpHandler noPipedNext;

    /**
     * Creates a new instance of CORSHandler
     *
     */
    public CORSHandler() {
        this(null);
    }

    /**
     * Creates a new instance of CORSHandler
     *
     * @param next
     */
    public CORSHandler(PipelinedHandler next) {
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
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        HeadersManager hm = new HeadersManager(exchange);

        injectXPBHeader(hm);

        injectAccessControlAllowHeaders(hm);

        if (noPipedNext != null) {
            noPipedNext.handleRequest(exchange);
        } else {
            next(exchange);
        }
    }

    private void injectXPBHeader(HeadersManager headers) {
        headers.addResponseHeader(HttpString.tryFromString(HttpHeaders.X_POWERED_BY), "restheart.org");
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
                + HttpHeaders.X_POWERED_BY);
    }

    interface CORSHeaders {

        HttpString ACCESS_CONTROL_EXPOSE_HEADERS = HttpString.tryFromString("Access-Control-Expose-Headers");
        HttpString ACCESS_CONTROL_ALLOW_CREDENTIAL = HttpString.tryFromString("Access-Control-Allow-Credentials");
        HttpString ACCESS_CONTROL_ALLOW_ORIGIN = HttpString.tryFromString("Access-Control-Allow-Origin");
    }
}
