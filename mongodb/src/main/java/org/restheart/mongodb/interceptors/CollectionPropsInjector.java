/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
package org.restheart.mongodb.interceptors;

import com.mongodb.MongoException;
import org.bson.BsonDocument;
import org.bson.BsonString;
import static org.restheart.exchange.ExchangeKeys.FS_FILES_SUFFIX;
import static org.restheart.exchange.ExchangeKeys._SCHEMAS;
import java.util.Optional;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.utils.ResponseHelper;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Injects the collection properties into the Request
 *
 * It is also responsible of sending NOT_FOUND in case of requests involving not
 * existing collections (that are not PUT)
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "collectionPropsInjector",
        description = "Injects the collection properties into the BsonRequest",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH,
        priority = Integer.MIN_VALUE + 1)
public class CollectionPropsInjector implements MongoInterceptor {
    private final Logger LOGGER = LoggerFactory.getLogger(CollectionPropsInjector.class);

    private Databases dbs = null;

    private static final String RESOURCE_DOES_NOT_EXIST = "Resource does not exist";
    private static final String COLLECTION_DOES_NOT_EXIST = "Collection '%s' does not exist";
    private static final String FILE_BUCKET_DOES_NOT_EXIST = "File Bucket '%s' does not exist";
    private static final String SCHEMA_STORE_DOES_NOT_EXIST = "Schema Store does not exist";

    /**
     * Makes sure that dbs is instantiated after MongoClient initialization
     *
     */
    @OnInit
    public void init() {
        this.dbs = Databases.get();
    }

    /**
     *
     * @param request
     * @param response
     * @throws Exception
     */
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        // skip if OPTIONS
        if (request.isOptions()) {
            return;
        }

        var dbName = request.getDBName();
        var collName = request.getCollectionName();

        if (dbName != null && collName != null && !request.isDbMeta()) {
            BsonDocument collProps;

            try {
                if (!MetadataCachesSingleton.isEnabled() || request.isNoCache()) {
                    collProps = dbs.getCollectionProperties(Optional.ofNullable(request.getClientSession()), request.rsOps(), dbName, collName);
                } else {
                    collProps = MetadataCachesSingleton.getInstance().getCollectionProperties(dbName, collName);
                }
            } catch(MongoException mce) {
                int httpCode = ResponseHelper.getHttpStatusFromErrorCode(mce.getCode());

                if (httpCode >= 500 && mce.getMessage() != null && !mce.getMessage().isBlank()) {
                    LOGGER.error("Error handling the request", mce);
                    response.setInError(httpCode, mce.getMessage());
                } else {
                    LOGGER.debug("Error handling the request", mce);
                    response.setInError(httpCode, ResponseHelper.getMessageFromMongoException(mce));
                }

                return;
            } catch (Exception e) {
                LOGGER.error("Error handling the request", e);
                response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error getting properties of collection " + dbName.concat(".").concat(collName), e);
                return;
            }

            // if collProps is null, the collection does not exist
            if (collProps == null && checkCollection(request)) {
                doesNotExists(request, response);
                return;
            }

            if (collProps == null && request.isGet()) {
                collProps = new BsonDocument("_id", new BsonString(collName));
            }

            request.setCollectionProps(collProps);
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return dbs != null
                && request.isHandledBy("mongo")
                && !(request.isInError()
                || request.isDbSize()
                || request.isTxn()
                || request.isTxns()
                || request.isSessions()
                || request.isSession());
    }

    /**
     *
     * @param request
     * @param response
     * @throws Exception
     */
    protected void doesNotExists(MongoRequest request, MongoResponse response)
            throws Exception {
        final String errMsg;
        final String resourceName = request.getCollectionName();

        if (resourceName == null) {
            errMsg = RESOURCE_DOES_NOT_EXIST;
        } else if (resourceName.endsWith(FS_FILES_SUFFIX)) {
            errMsg = String.format(FILE_BUCKET_DOES_NOT_EXIST, request.getCollectionName());
        } else if (_SCHEMAS.equals(resourceName)) {
            errMsg = SCHEMA_STORE_DOES_NOT_EXIST;
        } else {
            errMsg = String.format(COLLECTION_DOES_NOT_EXIST, request.getCollectionName());
        }

        response.setInError(HttpStatus.SC_NOT_FOUND, errMsg);
    }

    /**
     *
     * @param request
     * @return
     */
    public static boolean checkCollection(MongoRequest request) {
        return !(request.isCollection() && request.isPut())
                && !(request.isFilesBucket() && request.isPut())
                && !(request.isSchemaStore() && request.isPut())
                && !request.isRoot()
                && !request.isDb()
                && !request.isDbSize();
    }
}
