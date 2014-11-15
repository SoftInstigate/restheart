/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.security.handlers;

import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;

/**
 *
 * @author Andrea Di Cesare
 */
public class CORSHandler extends PipedHttpHandler {
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
        injectAccessControlAllowHeaders(exchange);

        if (noPipedNext != null) {
            noPipedNext.handleRequest(exchange);
        } else {
            next.handleRequest(exchange, context);
        }
    }

    private static void injectAccessControlAllowHeaders(HttpServerExchange exchange) {
        HeaderValues vals = exchange.getRequestHeaders().get(HttpString.tryFromString("Origin"));
        if (vals != null && !vals.isEmpty()) {
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Origin"), vals.getFirst());
        } else {
            exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Origin"), "*");
        }

        exchange.getResponseHeaders().put(HttpString.tryFromString("Access-Control-Allow-Credentials"), "true");

    }
}
