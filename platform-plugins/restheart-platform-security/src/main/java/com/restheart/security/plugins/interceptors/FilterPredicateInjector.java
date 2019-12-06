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
package com.restheart.security.plugins.interceptors;

import com.google.gson.JsonObject;
import com.restheart.security.plugins.authorizers.FilterPredicate;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import org.restheart.security.handlers.exchange.ByteArrayRequest;
import org.restheart.security.plugins.RequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilterPredicateInjector implements RequestInterceptor {
    public static final AttachmentKey<Boolean> FILTER_ADDED
            = AttachmentKey.create(Boolean.class);

    private static final Logger LOGGER = LoggerFactory
            .getLogger(FilterPredicateInjector.class);

    private void addFilter(final HttpServerExchange exchange, final JsonObject filter) {
        if (filter == null) {
            return;
        }

        // this resolve the filter against the current exchange
        // eg {'username':'%u'} => {'username':'uji'}
        var resolvedFilter = FilterPredicate.interpolateFilterVars(exchange, filter);

        exchange.addQueryParam("filter", resolvedFilter.toString());
        exchange.putAttachment(FILTER_ADDED, true);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = ByteArrayRequest.wrap(exchange);

        FilterPredicate predicate = FilterPredicate.from(exchange);

        if (request.isGet()
                && predicate != null
                && predicate.getReadFilter() != null) {
            LOGGER.debug("read filter: {}", predicate.getReadFilter());
            addFilter(exchange, predicate.getReadFilter());
        } else if ((request.isPatch()
                || request.isPut()
                || request.isPost()
                || request.isDelete())
                && predicate != null
                && predicate.getWriteFilter() != null) {
            LOGGER.debug("write filter to add: {}", predicate.getWriteFilter());
            addFilter(exchange, predicate.getWriteFilter());
        } else {
            LOGGER.trace("predicate specifies no filter");
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return exchange.getAttachment(FILTER_ADDED) == null;
    }

    @Override
    public IPOINT interceptPoint() {
        return RequestInterceptor.IPOINT.AFTER_AUTH;
    }
}
