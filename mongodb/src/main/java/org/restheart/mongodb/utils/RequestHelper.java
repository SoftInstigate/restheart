/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.db.OperationResult;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RequestHelper {

    private RequestHelper() {
    }

    /**
     *
     * @param exchange
     * @param etag
     * @return
     */
    public static boolean checkReadEtag(HttpServerExchange exchange, BsonObjectId etag) {
        if (etag == null) {
            return false;
        }

        HeaderValues vs = exchange.getRequestHeaders().get(Headers.IF_NONE_MATCH);

        return vs == null || vs.getFirst() == null ? false : vs.getFirst().equals(etag.getValue().toString());
    }

    /**
     *
     * @param exchange
     * @return the etag ObjectId value or null in case the IF_MATCH header is not
     *         present. If the header contains an invalid ObjectId string value
     *         returns a new ObjectId (the check will fail for sure)
     */
    public static ObjectId getWriteEtag(HttpServerExchange exchange) {
        HeaderValues vs = exchange.getRequestHeaders().get(Headers.IF_MATCH);

        return vs == null || vs.getFirst() == null ? null : getEtagAsObjectId(vs.getFirst());
    }

    /**
     *
     * @param etag
     * @return
     */
    private static ObjectId getEtagAsObjectId(String etag) {
        if (etag == null) {
            return null;
        }

        if (ObjectId.isValid(etag)) {
            return new ObjectId(etag);
        } else {
            return new ObjectId();
        }
    }

    /**
     *
     * @param content
     * @param exchange
     * @return true if content is not acceptable. In this case it also invoke
     *         response.setInError() on the exchange and the caller must invoke
     *         next() and return
     * @throws Exception
     */
    public static boolean isNotAcceptableContent(BsonValue content, HttpServerExchange exchange) throws Exception {
        // cannot proceed with no data
        if (content == null) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_NOT_ACCEPTABLE, "no data provided");
            return true;
        }
        // cannot proceed with an array
        if (!content.isDocument()) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_NOT_ACCEPTABLE, "data must be a json object");
            return true;
        }
        if (content.asDocument().isEmpty()) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_NOT_ACCEPTABLE, "no data provided");
            return true;
        }
        return false;
    }

    /**
     *
     * @param content
     * @param exchange
     * @return true if content is not acceptable. In this case it also invoke
     *         response.setInError() on the exchange and the caller must invoke
     *         next() and return
     * @throws Exception
     */
    public static boolean isNotAcceptableContentForPatch(BsonValue content, HttpServerExchange exchange) throws Exception {
        // cannot proceed with no data
        if (content == null) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_NOT_ACCEPTABLE, "no data provided");
            return true;
        }
        // can only proceed with an object or an array
        if (!content.isDocument() && !content.isArray() ) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_NOT_ACCEPTABLE, "data must be a json object or array");
            return true;
        }
        if (content.isDocument() && content.asDocument().isEmpty()) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_NOT_ACCEPTABLE, "no data provided");
            return true;
        }

        if (content.isArray() && content.asArray().isEmpty()) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_NOT_ACCEPTABLE, "no data provided");
            return true;
        }
        return false;
    }

    /**
     *
     * Warn side effect: invokes
     * MongoResponse.of(exchange).setDbOperationResult(result)
     *
     * @param result
     * @param exchange
     * @return true if response is in coflict. In this case it also invoke
     *         response.setInError() on the exchange and the caller must invoke
     *         next() and return
     * @throws Exception
     */
    public static boolean isResponseInConflict(OperationResult result, HttpServerExchange exchange) throws Exception {
        MongoResponse.of(exchange).setDbOperationResult(result);
        // inject the etag
        if (result.getEtag() != null) {
            ResponseHelper.injectEtagHeader(exchange, result.getEtag());
        }
        if (result.getHttpCode() == HttpStatus.SC_CONFLICT) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_CONFLICT,
                    "The ETag must be provided using the '" + Headers.IF_MATCH + "' header");
            return true;
        }
        // handle the case of duplicate key error
        if (result.getHttpCode() == HttpStatus.SC_EXPECTATION_FAILED) {
            MongoResponse.of(exchange).setInError(HttpStatus.SC_EXPECTATION_FAILED,
                    ResponseHelper.getMessageFromErrorCode(11000));
            return true;
        }
        return false;
    }
}
