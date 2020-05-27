/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this handler injects the collection properties in the RequestContext it is
 * also responsible of sending NOT_FOUND in case of requests involving not
 * existing collections (that are not PUT)
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CollectionPropsInjectorHandler extends PipedHttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionPropsInjectorHandler.class);

    private static final String RESOURCE_DOES_NOT_EXIST = "Resource does not exist";
    private static final String COLLECTION_DOES_NOT_EXIST = "Collection '%s' does not exist";
    private static final String FILE_BUCKET_DOES_NOT_EXIST = "File Bucket '%s' does not exist";
    private static final String SCHEMA_STORE_DOES_NOT_EXIST = "Schema Store does not exist";

    public static boolean checkCollection(RequestContext context) {
        return !(context.getType() == RequestContext.TYPE.COLLECTION && context.getMethod() == RequestContext.METHOD.PUT)
                && !(context.getType() == RequestContext.TYPE.FILES_BUCKET && context.getMethod() == RequestContext.METHOD.PUT)
                && !(context.getType() == RequestContext.TYPE.SCHEMA_STORE && context.getMethod() == RequestContext.METHOD.PUT)
                && context.getType() != RequestContext.TYPE.ROOT
                && context.getType() != RequestContext.TYPE.DB
                && context.getType() != RequestContext.TYPE.DB_SIZE;
    }

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
        if (context.isInError() 
                || context.isTxn()
                || context.isTxns()) {
            next(exchange, context);
            return;
        }

        String dbName = context.getDBName();
        String collName = context.getCollectionName();

        if (dbName != null && collName != null && !context.isDbMeta()) {
            BsonDocument collProps;

            if (!LocalCachesSingleton.isEnabled() || context.isNoCache()) {
                collProps = getDatabase().
                        getCollectionProperties(
                                context.getClientSession(),
                                dbName, 
                                collName);
            } else {
                collProps = LocalCachesSingleton.getInstance()
                        .getCollectionProperties(dbName, collName);
            }

            // if collProps is null, the collection does not exist
            if (collProps == null
                    && checkCollection(context)) {
                doesNotExists(context, exchange);
                return;
            }

            if (collProps == null
                    && context.getMethod() == RequestContext.METHOD.GET) {
                collProps = new BsonDocument("_id", new BsonString(collName));
            }

            context.setCollectionProps(collProps);
        }

        getNext()
                .handleRequest(exchange, context);
    }

    protected void doesNotExists(RequestContext context, HttpServerExchange exchange) throws Exception {
        final String errMsg;
        final String resourceName = context.getCollectionName();

        if (resourceName == null) {
            errMsg = RESOURCE_DOES_NOT_EXIST;
        } else if (resourceName.endsWith(RequestContext.FS_FILES_SUFFIX)) {
            errMsg = String.format(FILE_BUCKET_DOES_NOT_EXIST, context.getCollectionName());
        } else if (RequestContext._SCHEMAS.equals(resourceName)) {
            errMsg = SCHEMA_STORE_DOES_NOT_EXIST;
        } else {
            errMsg = String.format(COLLECTION_DOES_NOT_EXIST, context.getCollectionName());
        }

        LOGGER.debug(errMsg);
        ResponseHelper.endExchangeWithMessage(
                exchange,
                context,
                HttpStatus.SC_NOT_FOUND,
                errMsg);
        next(exchange, context);
    }
}
