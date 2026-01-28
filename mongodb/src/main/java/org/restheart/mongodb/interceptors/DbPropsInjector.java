/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

import java.util.Optional;

import org.bson.BsonDocument;
import org.bson.BsonString;
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

import com.mongodb.MongoException;

/**
 *
 * Injects the db properties into the MongoRequest

 It is also responsible of sending NOT_FOUND in case of requests involving not
 existing dbs (that are not PUT)
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "dbPropsInjector",
        description = "Injects the db properties into the BsonRequest",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH,
        priority = Integer.MIN_VALUE)
public class DbPropsInjector implements MongoInterceptor {
    private final Logger LOGGER = LoggerFactory.getLogger(DbPropsInjector.class);

    private Databases dbs = null;

    /**
     * Makes sure that dbs is instantiated after MongoClient initialization
     */
    @OnInit
    public void init() {
        this.dbs = Databases.get();
    }

    /**
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

        if (dbName != null) {
            BsonDocument dbProps;

            try {
                if (!MetadataCachesSingleton.isEnabled() || request.isNoCache()) {
                    dbProps = dbs.getDatabaseProperties(Optional.ofNullable(request.getClientSession()), request.rsOps(), dbName);
                } else {
                    dbProps = MetadataCachesSingleton.getInstance().getDBProperties(dbName);
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
                response.setInError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Error getting properties of db " + dbName, e);
                return;
            }

            // if dbProps is null, the db does not exist
            if (dbProps == null
                && !(request.isDb()
                && request.isPut())
                && !request.isRoot()) {
                response.setInError(HttpStatus.SC_NOT_FOUND, "Db '" + dbName + "' does not exist");
                return;
            }

            if (dbProps != null) {
                dbProps.append("_id", new BsonString(dbName));
            }

            request.setDbProps(dbProps);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return this.dbs != null
            && request.isHandledBy("mongo")
            && !(request.isInError()
            || request.isSessions()
            || request.isSession()
            || request.isTxn()
            || request.isTxns()
            || request.isRoot()
            || request.isRootSize());
    }
}
