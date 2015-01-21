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

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import java.util.HashMap;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class RequestHelper {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHelper.class);

    /**
     *
     * @param exchange
     * @param etag
     * @return
     */
    public static boolean checkReadEtag(HttpServerExchange exchange, String etag) {
        if (etag == null) {
            return false;
        }

        HeaderValues vs = exchange.getRequestHeaders().get(Headers.IF_NONE_MATCH);

        return vs == null || vs.getFirst() == null ? false : vs.getFirst().equals(etag);
    }

    /**
     *
     * @param exchange
     * @return
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
    public static ObjectId getEtagAsObjectId(Object etag) {
        if (etag == null) {
            return null;
        }

        if (ObjectId.isValid("" + etag)) {
            return new ObjectId("" + etag);
        } else {
            return new ObjectId();
        }
    }
    
    /**
     * undertow bug UNDERTOW-371 workaround, reported to be fixed, waiting for releases 1.1.2.Final and
     * 1.2.0.Beta9 to test and remove
     *
     * @param exchange
     * @see https://issues.jboss.org/browse/UNDERTOW-371
     *
     */
    public static void fixExchangeForUndertowBug(HttpServerExchange exchange) {
        LOGGER.debug("applying undertow bug 371 workaround - to be removed when 1.1.2.Final or 1.2.0.Beta9 are available");
        if (exchange.getAttachment(Predicate.PREDICATE_CONTEXT) == null) {
            exchange.putAttachment(Predicate.PREDICATE_CONTEXT, new HashMap<String, Object>());
        }
    }
}
