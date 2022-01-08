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

import com.mongodb.MongoException;

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
    public static void injectEtagHeader(HttpServerExchange exchange, BsonDocument properties) {
        if (properties == null) {
            return;
        }

        var _etag = properties.get("_etag");

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
    public static void injectEtagHeader(HttpServerExchange exchange, Document properties) {
        if (properties == null) {
            return;
        }

        var _etag = properties.get("_etag");

        if (_etag == null) {
            return;
        }

        if (_etag instanceof ObjectId) {
            setETagHeader(exchange, _etag.toString());
        } else if (_etag instanceof String setag) {
            setETagHeader(exchange, setag);
        }
    }

    /**
     *
     * @param exchange
     * @param etag
     */
    public static void injectEtagHeader(HttpServerExchange exchange, Object etag) {

        if (etag == null) {
            return;
        }

        if (etag instanceof BsonValue _etag) {
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
        return switch (code) {
            // Unknown top level operator
            case 2 -> HttpStatus.SC_BAD_REQUEST;
            // The MongoDB user does not have enough permissions to execute this operation.
            case 13 -> HttpStatus.SC_FORBIDDEN;
            // Wrong MongoDB user credentials
            case 18 -> HttpStatus.SC_FORBIDDEN;
            // Write request for sharded collection must specify the shardkey.
            case 61 -> HttpStatus.SC_BAD_REQUEST;
            // Update tried to change the immutable shardkey
            case 66 -> HttpStatus.SC_FORBIDDEN;
            // Document failed validation
            case 121 -> HttpStatus.SC_BAD_REQUEST;
            //WriteConflict
            case 112 -> HttpStatus.SC_CONFLICT;
            // Cannot start transaction X on session Y because a newer transaction Z has already started
            // transaction number X does not match any in-progress transactions
            // Transaction X has been committed.
            case 225,251,256 -> HttpStatus.SC_NOT_ACCEPTABLE;
            // error 11000 is duplicate key error
            // happens when the _id and a filter are specified,
            // the document exists but does not match the filter
            case 11000 ->HttpStatus.SC_CONFLICT;
            // db already exists with different case
            case 13297 -> HttpStatus.SC_CONFLICT;
            // FieldPath must not end with a '.'
            case 56, 40353 -> HttpStatus.SC_BAD_REQUEST;
            case 51091 -> HttpStatus.SC_BAD_REQUEST;
            default -> HttpStatus.SC_INTERNAL_SERVER_ERROR;
        };
    }

    /**
     *
     * @param me the MongoException
     * @return
     */
    public static String getMessageFromMongoException(MongoException me) {
        var code = me.getCode();

        return switch(code) {
            // Query failed with error code 51091 and error message 'Regular expression is invalid: unmatched parentheses'
            case 2, 51091-> {
                var msg = me.getMessage();

                var b = msg.indexOf("error message '");
                var e = msg.indexOf("' on server");

                if (b >= 0 && e >= 0) {
                    yield "Invalid filter: " + msg.substring(b+15, e);
                } else {
                    yield msg;
                }
            }
            default -> getMessageFromErrorCode(code);
        };
    }

    /**
     *
     * @param code mongodb error code from MongoException.getCode()
     * @return
     */
    public static String getMessageFromErrorCode(int code) {
        return switch(code) {
            case 13 -> "The MongoDB user does not have enough permissions to execute this operation";
            case 18 -> "Wrong MongoDB user credentials (wrong password or need to specify the authentication dababase with 'authSource=<db>' option in mongo-uri)";
            case 61 -> "Write request for sharded collection must specify the shardkey. Use the 'shardkey' query parameter";
            case 66 -> "Update tried to change the immutable shardkey";
            case 121 -> "Document failed validation";
            case 112 -> "Write conflict inside transaction";
            // Cannot start transaction X on session Y because a newer transaction Z has already started// Cannot start transaction X on session Y because a newer transaction Z has already started
            case 225 -> "The given transaction is not in-progress";
            // transaction number X does not match any in-progress transactions
            case 251 -> "The given transaction is not in-progress";
            // Transaction X has been committed.
            case 256 -> "The given transaction is not in-progress";
            // error 11000 is duplicate key error
            // can happens for
            // - insert requests but _id already exists
            // - for upsert/updates with the _id and a filter, and the update document does not match the filter
            // - a unique index exists, and the document duplicates an existing key
            case 11000 -> "Duplicate key error (insert with existing _id, update a document not matching specified filter or unique index violation)";
            // FieldPath must not end with a '.'
            case 56, 40353 -> "FieldPath must not end with a '.'";
            case 13297 -> "Db already exists with different case";
            default -> "Error handling the request, see log for more information";
        };
    }

    private ResponseHelper() {
    }
}
