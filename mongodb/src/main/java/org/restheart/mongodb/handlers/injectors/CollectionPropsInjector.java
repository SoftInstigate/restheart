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
package org.restheart.mongodb.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import static org.restheart.handlers.exchange.ExchangeKeys.FS_FILES_SUFFIX;
import static org.restheart.handlers.exchange.ExchangeKeys._SCHEMAS;
import org.restheart.mongodb.db.DatabaseImpl;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * this handler injects the collection properties in the RequestContext it is
 * also responsible of sending NOT_FOUND in case of requests involving not
 * existing collections (that are not PUT)
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class CollectionPropsInjector extends PipelinedHandler {
    private final DatabaseImpl dbsDAO = new DatabaseImpl();

    private static final Logger LOGGER = LoggerFactory
            .getLogger(CollectionPropsInjector.class);

    private static final String RESOURCE_DOES_NOT_EXIST = "Resource does not exist";
    private static final String COLLECTION_DOES_NOT_EXIST = "Collection '%s' does not exist";
    private static final String FILE_BUCKET_DOES_NOT_EXIST = "File Bucket '%s' does not exist";
    private static final String SCHEMA_STORE_DOES_NOT_EXIST = "Schema Store does not exist";

    /**
     *
     * @param request
     * @return
     */
    public static boolean checkCollection(BsonRequest request) {
        return !(request.isCollection() && request.isPut())
                && !(request.isFilesBucket() && request.isPut())
                && !(request.isSchemaStore() && request.isPut())
                && !request.isRoot()
                && !request.isDb()
                && !request.isDbSize();
    }

    /**
     * Creates a new instance of MetadataInjecterHandler
     *
     */
    public CollectionPropsInjector() {
        super(null);
    }
    
    /**
     * Creates a new instance of MetadataInjecterHandler
     *
     * @param next
     */
    public CollectionPropsInjector(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        
        if (request.isInError() 
                || request.isTxn()
                || request.isTxns()) {
            next(exchange);
            return;
        }

        String dbName = request.getDBName();
        String collName = request.getCollectionName();

        if (dbName != null && collName != null && !request.isDbMeta()) {
            BsonDocument collProps;

            if (!LocalCachesSingleton.isEnabled() 
                    || request.getClientSession() != null) {
                collProps = dbsDAO.
                        getCollectionProperties(
                                request.getClientSession(),
                                dbName, 
                                collName);
            } else {
                collProps = LocalCachesSingleton.getInstance()
                        .getCollectionProperties(dbName, collName);
            }

            // if collProps is null, the collection does not exist
            if (collProps == null
                    && checkCollection(request)) {
                doesNotExists(exchange);
                return;
            }

            if (collProps == null
                    && request.isGet()) {
                collProps = new BsonDocument("_id", new BsonString(collName));
            }

            request.setCollectionProps(collProps);
        }

        next(exchange);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    protected void doesNotExists(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        
        final String errMsg;
        final String resourceName = request.getCollectionName();

        if (resourceName == null) {
            errMsg = RESOURCE_DOES_NOT_EXIST;
        } else if (resourceName.endsWith(FS_FILES_SUFFIX)) {
            errMsg = String.format(FILE_BUCKET_DOES_NOT_EXIST, 
                    request.getCollectionName());
        } else if (_SCHEMAS.equals(resourceName)) {
            errMsg = SCHEMA_STORE_DOES_NOT_EXIST;
        } else {
            errMsg = String.format(COLLECTION_DOES_NOT_EXIST, 
                    request.getCollectionName());
        }

        LOGGER.debug(errMsg);
        ResponseHelper.endExchangeWithMessage(
                exchange,
                HttpStatus.SC_NOT_FOUND,
                errMsg);
        next(exchange);
    }
}
