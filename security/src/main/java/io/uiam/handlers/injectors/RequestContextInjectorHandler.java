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
package io.uiam.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import java.util.logging.Logger;
import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.RequestContext;
import io.uiam.utils.URLUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestContextInjectorHandler extends PipedHttpHandler {

    private static final Logger LOG = Logger.getLogger(RequestContextInjectorHandler.class.getName());

    private final String uri;
    private final String resourceUrl;

    /**
     *
     * @param uri
     * @param resourceUrl
     * @param next
     */
    public RequestContextInjectorHandler(String uri, String resourceUrl, PipedHttpHandler next) {
        super(next);

        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null. check your resource-mounts configuration option.");
        }

        if (!uri.startsWith("/")) {
            throw new IllegalArgumentException("URI must start with \"/\". check your resource-mounts configuration option.");
        }

        if (resourceUrl == null ||
                (!resourceUrl.startsWith("http://") && 
                !resourceUrl.startsWith("https://") &&
                !resourceUrl.startsWith("/"))) {
            throw new IllegalArgumentException("URL must start with \"/\", \"http://\" or \"https://\". check your resource-mounts configuration option.");
        }

        this.uri = URLUtils.removeTrailingSlashes(uri);
        this.resourceUrl = resourceUrl;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        RequestContext rcontext = new RequestContext(exchange, uri, resourceUrl);

        if (getNext() != null) {
            next(exchange, rcontext);
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        handleRequest(exchange, new RequestContext(exchange, uri, resourceUrl));
    }
}
