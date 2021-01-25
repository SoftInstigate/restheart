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
package org.restheart.mongodb.utils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ResponseHelper {
    /**
     * Set the ETag in the response's header
     *
     * @param exchange
     * @param etag
     */
    protected static void setETagHeader(final HttpServerExchange exchange, final String etag) {
        exchange.getResponseHeaders().put(Headers.ETAG, etag);
    }

    /**
     *
     * @param exchange
     * @param properties
     */
    public static void injectEtagHeader(
            HttpServerExchange exchange,
            BsonDocument properties) {
        if (properties == null) {
            return;
        }

        BsonValue _etag = properties.get("_etag");

        if (_etag == null) {
            return;
        }

        if (_etag.isObjectId()) {
            setETagHeader(exchange, _etag.asObjectId().getValue().toString());
        } else if (_etag.isString()) {
            setETagHeader(exchange, _etag.asString().getValue());
        }
    }

    /**
     *
     * @param exchange
     * @param properties
     */
    public static void injectEtagHeader(
            HttpServerExchange exchange,
            Document properties) {
        if (properties == null) {
            return;
        }

        Object _etag = properties.get("_etag");

        if (_etag == null) {
            return;
        }

        if (_etag instanceof ObjectId) {
            setETagHeader(exchange, _etag.toString());
        } else if (_etag instanceof String) {
            setETagHeader(exchange, (String) _etag);
        }
    }

    /**
     *
     * @param exchange
     * @param etag
     */
    public static void injectEtagHeader(
            HttpServerExchange exchange,
            Object etag) {

        if (etag == null) {
            return;
        }

        if (etag instanceof BsonValue) {
            BsonValue _etag = (BsonValue) etag;

            if (_etag.isObjectId()) {
                setETagHeader(exchange, _etag.asObjectId().getValue().toString());
            } else if (_etag.isString()) {
                setETagHeader(exchange, _etag.asString().getValue());
            }

        } else if (etag instanceof ObjectId) {
            setETagHeader(exchange, etag.toString());
        } else if (etag instanceof String) {
            setETagHeader(exchange, (String) etag);
        }

    }

    /**
     *
     * @param code mongodb error code from MongoException.getCode()
     * @return
     */
    public static int getHttpStatusFromErrorCode(int code) {
        switch (code) {
            case 2:
                // Unknown top level operator
                return HttpStatus.SC_BAD_REQUEST;
            case 13:
                // The MongoDB user does not have enough permissions to execute this operation.
                return HttpStatus.SC_FORBIDDEN;
            case 18:
                // Wrong MongoDB user credentials
                return HttpStatus.SC_FORBIDDEN;
            case 61:
                // Write request for sharded collection must specify the shardkey.
                return HttpStatus.SC_BAD_REQUEST;
            case 66:
                // Update tried to change the immutable shardkey
                return HttpStatus.SC_FORBIDDEN;
            case 121:
                // Document failed validation
                return HttpStatus.SC_BAD_REQUEST;
            case 112:
                //WriteConflict
                return HttpStatus.SC_CONFLICT;
            case 225:
            // Cannot start transaction X on session Y because a newer transaction Z has already started
            case 251:
            // transaction number X does not match any in-progress transactions
            case 256:
                // Transaction X has been committed.
                return HttpStatus.SC_NOT_ACCEPTABLE;
            case 11000:
                // error 11000 is duplicate key error
                // happens when the _id and a filter are specified,
                // the document exists but does not match the filter
                return HttpStatus.SC_CONFLICT;
            case 13297:
                // db already exists with different case
                return HttpStatus.SC_CONFLICT;
            default:
                // Other
                return HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }
    }

    /**
     *
     * @param code mongodb error code from MongoException.getCode()
     * @return
     */
    public static String getMessageFromErrorCode(int code) {
        switch (code) {
            case 2:
                return "Bad value";
            case 13:
                return "The MongoDB user does not have enough "
                        + "permissions to execute this operation";
            case 18:
                return "Wrong MongoDB user credentials "
                        + "(wrong password or need to specify the "
                        + "authentication dababase "
                        + "with 'authSource=<db>' option in mongo-uri)";
            case 61:
                return "Write request for sharded "
                        + "collection must specify the shardkey. "
                        + "Use the 'shardkey' query parameter";
            case 66:
                return "Update tried to change the immutable shardkey";
            case 121:
                //Document failed validation
                return "Document failed collection validation";
            case 112:
                //WriteConflict
                return "Write conflict inside transaction";
            case 225:
            // Cannot start transaction X on session Y because a newer transaction Z has already started// Cannot start transaction X on session Y because a newer transaction Z has already started
            case 251:
            // transaction number X does not match any in-progress transactions
            case 256:
                // Transaction X has been committed.
                return "The given transaction is not in-progress";
            case 11000:
                // error 11000 is duplicate key error
                // can happens for
                // - insert requests but _id already exists
                // - for upsert/updates with the _id and a filter, and the update document does not match the filter
                // - a unique index exists, and the document duplicates an existing key
                return "Duplicate key error (insert with existing _id, update a document not matching specified filter or unique index violation)";
                case 13297:
                // db already exists with different case
                return "Db already exists with different case";
            default:
                return "Error handling the request, "
                        + "see log for more information";
        }
    }

    private ResponseHelper() {
    }
}
