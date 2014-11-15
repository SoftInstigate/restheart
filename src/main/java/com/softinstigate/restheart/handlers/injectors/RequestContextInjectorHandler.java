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
package com.softinstigate.restheart.handlers.injectors;

import com.mongodb.util.JSON;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import com.softinstigate.restheart.utils.URLUtilis;
import io.undertow.server.HttpServerExchange;
import java.util.Deque;

/**
 *
 * @author Andrea Di Cesare
 */
public class RequestContextInjectorHandler extends PipedHttpHandler {
    private final String whereUri;
    private final String whatUri;

    /**
     *
     * @param whereUri
     * @param whatUri
     * @param next
     */
    public RequestContextInjectorHandler(String whereUri, String whatUri, PipedHttpHandler next) {
        super(next);

        if (whereUri == null) {
            throw new IllegalArgumentException("whereUri cannot be null. check your mongo-mounts.");
        }

        if (!whereUri.startsWith("/")) {
            throw new IllegalArgumentException("whereUri must start with \"/\". check your mongo-mounts");
        }

        if (!whatUri.startsWith("/") && !whatUri.equals("*")) {
            throw new IllegalArgumentException("whatUri must start with \"/\". check your mongo-mounts");
        }

        this.whereUri = URLUtilis.removeTrailingSlashes(whereUri);
        this.whatUri = whatUri;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        RequestContext rcontext = new RequestContext(exchange, whereUri, whatUri);

        Deque<String> __pagesize = exchange.getQueryParameters().get("pagesize");

        int page = 1; // default page
        int pagesize = 100; // default pagesize

        if (__pagesize != null && !(__pagesize.isEmpty())) {
            try {
                pagesize = Integer.parseInt(__pagesize.getFirst());
            } catch (NumberFormatException ex) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "illegal pagesize paramenter, it is not a number", ex);
                return;
            }
        }

        if (pagesize < 1 || pagesize > 1000) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "illegal page parameter, pagesize must be >= 0 and <= 1000");
            return;
        } else {
            rcontext.setPagesize(pagesize);
        }

        Deque<String> __page = exchange.getQueryParameters().get("page");

        if (__page != null && !(__page.isEmpty())) {
            try {
                page = Integer.parseInt(__page.getFirst());
            } catch (NumberFormatException ex) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "illegal page paramenter, it is not a number", ex);
                return;
            }
        }

        if (page < 1) {
            ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "illegal page paramenter, it is < 1");
            return;
        } else {
            rcontext.setPage(page);
        }

        Deque<String> __count = exchange.getQueryParameters().get("count");

        if (__count != null) {
            rcontext.setCount(true);
        }
        // get and check sort_by parameter
        Deque<String> sort_by = exchange.getQueryParameters().get("sort_by");

        if (sort_by != null) {
            if (sort_by.stream().anyMatch(s -> s == null || s.isEmpty())) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "illegal sort_by paramenter");
                return;
            }

            rcontext.setSortBy(exchange.getQueryParameters().get("sort_by"));
        }

        // get and check filter parameter
        Deque<String> filters = exchange.getQueryParameters().get("filter");

        if (filters != null) {
            if (filters.stream().anyMatch(f -> {
                if (f == null || f.isEmpty()) {
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "illegal filter paramenter (empty)");
                    return true;
                }

                try {
                    JSON.parse(f);
                } catch (Throwable t) {
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_BAD_REQUEST, "illegal filter paramenter: " + f, t);
                    return true;
                }

                return false;
            })) {
                return; // an error occurred
            }

            rcontext.setFilter(exchange.getQueryParameters().get("filter"));
        }

        next.handleRequest(exchange, rcontext);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        handleRequest(exchange, new RequestContext(exchange, whereUri, whatUri));
    }
}
