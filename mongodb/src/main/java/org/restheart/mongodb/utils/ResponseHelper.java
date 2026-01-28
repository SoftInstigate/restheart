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
package org.restheart.mongodb.utils;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.restheart.utils.HttpStatus;

import com.mongodb.MongoException;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

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
        } else if (etag instanceof String setag) {
            setETagHeader(exchange, setag);
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
            // Invalid database name
            case 73 -> HttpStatus.SC_BAD_REQUEST;
            // Document failed validation
            case 121 -> HttpStatus.SC_BAD_REQUEST;
            //WriteConflict
            case 112 -> HttpStatus.SC_CONFLICT;
            // Cannot start transaction X on session Y because a newer transaction Z has already started
            // transaction number X does not match any in-progress transactions
            // Transaction X has been committed.
            case 225,251,256 -> HttpStatus.SC_BAD_REQUEST;
            // error 11000 is duplicate key error
            // happens when the _id and a filter are specified,
            // the document exists but does not match the filter
            case 11000 ->HttpStatus.SC_CONFLICT;
            // db already exists with different case
            case 13297 -> HttpStatus.SC_CONFLICT;
            // 56, 40353 FieldPath must not end with a '.'
            // 40352 FieldPath cannot be constructed with empty string
            // 51091 Regular expression is invalid: unmatched parentheses
            // 51108 Invalid flag in regex options
            // 16410 FieldPath field names may not start with '$'
            case 56, 40353, 40352, 51091, 51108, 40323, 16410  -> HttpStatus.SC_BAD_REQUEST;
            // 31253 Cannot do inclusion on field xxxx in exclusion projection
            case 31253 -> HttpStatus.SC_BAD_REQUEST;
            // 15974 Illegal key in $sort specification
            // 17312 $meta is the only expression supported by $sort right now
            // 31138 Illegal $meta sort
            case 15974, 17312, 31138 -> HttpStatus.SC_BAD_REQUEST;
            case 15998 -> HttpStatus.SC_BAD_REQUEST;
            // 31249 Path collision
            case 31249 -> HttpStatus.SC_BAD_REQUEST;
            // 168 InvalidPipelineOperator
            case 168 -> HttpStatus.SC_BAD_REQUEST;
            // 17276 Use of undefined variable
            case 17276 -> HttpStatus.SC_BAD_REQUEST;
            // 1728, Can't canonicalize query: BadValue Projection cannot have a mix of inclusion and exclusion (old error message)
            // 31254 Cannot do exclusion on field x in inclusion projection
            case 17287, 31254 -> HttpStatus.SC_BAD_REQUEST;
            // 241 ConversionFailure
            case 241 -> HttpStatus.SC_BAD_REQUEST;
            // wrong $sort value (must be 1 or -1)
            case 15975 -> HttpStatus.SC_BAD_REQUEST;
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
            // Query failed with error code 51108 with name 'Location51108' and error message 'invalid flag in regex options: z' on server 127.0.0.1:27017'
            // Command failed with error 31249 (Location31249): 'Path collision at page.children.children.displayname remaining portion children.children.displayname' on server...
            case 2, 51091, 51108, 31249, 168, 17276, 31254 -> {
                var msg = me.getMessage();

                var b = msg.indexOf(": '");
                var e = msg.indexOf("' on server");

                if (b >= 0 && e >= 0) {
                    yield "Invalid query parameter: " + msg.substring(b+3, e).strip();
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
            case 73 -> "Invalid database name";
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
            case 40352 -> "FieldPath cannot be constructed with empty string";
            case 16410 -> "FieldPath field names may not start with '$'";
            case 13297 -> "Db already exists with different case";
            // 31253 Cannot do inclusion on field xxxx in exclusion projection
            case 17287, 31253 -> "Keys projection cannot have a mix of inclusion and exclusion";
            // 15974 Illegal key in $sort specification
            // 17312 $meta is the only expression supported by $sort right now
            case 15974, 17312 -> "Invalid sort parameter";
            // 31138 Illegal $meta sort
            case 31138 -> "Invalid $meta sort";
            case 40323 -> "A pipeline stage specification object must contain exactly one field.";
            case 15998 -> "FieldPath field names may not be empty strings";
            case 241 ->  "Failed to parse number in $convert";
            case 15975 -> "Wrong sort parameter, key ordering must be 1 (for ascending) or -1 (for descending)";
            default -> "Error handling the request, see log for more information";
        };
    }

    private ResponseHelper() {
    }
}
