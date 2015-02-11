/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.utils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.bson.types.ObjectId;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RequestHelper {

    /**
     *
     * @param exchange
     * @param etag
     * @return
     */
    public static boolean checkReadEtag(HttpServerExchange exchange, ObjectId etag) {
        if (etag == null) {
            return false;
        }

        HeaderValues vs = exchange.getRequestHeaders().get(Headers.IF_NONE_MATCH);

        return vs == null || vs.getFirst() == null ? false : vs.getFirst().equals(etag.toString());
    }

    /**
     *
     * @param exchange
     * @return the etag ObjectId value or null in case the IF_MATCH header is not present. If the header contains an invalid ObjectId string value returns a new ObjectId (the check will fail for sure)
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
}
