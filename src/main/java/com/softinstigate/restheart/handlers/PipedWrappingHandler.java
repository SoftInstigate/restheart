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
package com.softinstigate.restheart.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author uji
 */
public class PipedWrappingHandler extends PipedHttpHandler {
    private final HttpHandler wrapped;

    /**
     * Creates a new instance of PipedWrappingHandler
     *
     * @param next
     * @param toWrap
     */
    public PipedWrappingHandler(PipedHttpHandler next, HttpHandler toWrap) {
        super(next);
        wrapped = toWrap;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (wrapped == null) {
            next.handleRequest(exchange, context);
        }
        else {
            wrapped.handleRequest(exchange);

            if (!exchange.isResponseComplete()) {
                next.handleRequest(exchange, context);
            }
        }
    }
}
