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
package com.restheart.authorizers;

import com.google.gson.JsonObject;
import com.restheart.security.plugins.authorizers.FilterPredicate;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name ="filterPredicateInjector",
        description = "inject the filter set by ACL into the request",
        interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH)
public class FilterPredicateInjector implements MongoInterceptor {
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
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var exchange = request.getExchange();
        var predicate = FilterPredicate.from(exchange);

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
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo") && 
                request.getExchange().getAttachment(FILTER_ADDED) == null;
    }
}
