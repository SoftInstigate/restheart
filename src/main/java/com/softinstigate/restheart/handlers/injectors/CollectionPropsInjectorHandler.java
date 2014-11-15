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

import com.mongodb.DBObject;
import com.softinstigate.restheart.db.CollectionDAO;
import com.softinstigate.restheart.handlers.PipedHttpHandler;
import com.softinstigate.restheart.handlers.RequestContext;
import com.softinstigate.restheart.utils.HttpStatus;
import com.softinstigate.restheart.utils.ResponseHelper;
import io.undertow.server.HttpServerExchange;

/**
 * this handler injects the collection properties in the RequestContext this
 * handler is also responsible of sending NOT_FOUND in case of requests
 * involving not existing collections (that are not PUT)
 *
 * @author Andrea Di Cesare
 */
public class CollectionPropsInjectorHandler extends PipedHttpHandler {
    /**
     * Creates a new instance of MetadataInjecterHandler
     *
     * @param next
     */
    public CollectionPropsInjectorHandler(PipedHttpHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (context.getDBName() != null && context.getCollectionName() != null) {
            DBObject collProps;

            if (!LocalCachesSingleton.isEnabled()) {
                collProps = CollectionDAO.getCollectionProps(context.getDBName(), context.getCollectionName());

                if (collProps != null) {
                    collProps.put("_collection-props-cached", false);
                } else if (!(context.getType() == RequestContext.TYPE.COLLECTION && context.getMethod() == RequestContext.METHOD.PUT)
                        && context.getType() != RequestContext.TYPE.ROOT
                        && context.getType() != RequestContext.TYPE.DB) {
                    ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "collection does not exist");
                    return;
                }
            } else {
                collProps = LocalCachesSingleton.getInstance().getCollectionProps(context.getDBName(), context.getCollectionName());
            }

            if (collProps == null
                    && !(context.getType() == RequestContext.TYPE.COLLECTION && context.getMethod() == RequestContext.METHOD.PUT)
                    && context.getType() != RequestContext.TYPE.ROOT
                    && context.getType() != RequestContext.TYPE.DB) {
                ResponseHelper.endExchangeWithMessage(exchange, HttpStatus.SC_NOT_FOUND, "collection does not exist");
                return;
            }

            context.setCollectionProps(collProps);
        }

        next.handleRequest(exchange, context);
    }
}
