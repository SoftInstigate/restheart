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
package org.restheart.plugins;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.handlers.RequestContext;

/**
 * A Checker performs validation on write requests. If the check fails, request
 * fails with status code BAD_REQUEST
 * <p>
 * Note: data to be checked is the argument contentToCheck. this can differ from
 * context.getContent() on bulk requests where it is an array of objects
 * <p>
 * Some useful info that can be retrived from arguments request content:
 * <ul>
 * <li>request content: context.getContent()
 * <li>response data (in read requests): context.getResponseContent()
 * <li>old data (in write requests): contenx.getDbOperationResult.getOldData()
 * <li>new data (in write requests): contenx.getDbOperationResult.getNewData()
 * <li>request method: context.getMethod()
 * <li>request type: context.getType()
 * <li>remote user: ExchangeAttributes.remoteUser().readAttribute(exchange)
 * <li>user roles:
 * exchange.getSecurityContext().getAuthenticatedAccount().getRoles()
 * <li>resource type: context.getPhase()
 * <li>request date: ExchangeAttributes.dateTime().readAttribute(exchange)
 * <li>local ip: ExchangeAttributes.localIp().readAttribute(exchange)
 * <li>local port: ExchangeAttributes.localPort().readAttribute(exchange)
 * <li>local server name:
 * ExchangeAttributes.localServerName().readAttribute(exchange)
 * <li>query string: ExchangeAttributes.queryString().readAttribute(exchange)
 * <li>relative path: ExchangeAttributes.relativePath().readAttribute(exchange)
 * <li>remote ip: ExchangeAttributes.remoteIp().readAttribute(exchange)
 * <li>ETag in request:
 * ExchangeAttributes.requestHeader(HttpString.tryFromString(HttpHeaders.ETAG)).readAttribute(exchange)
 * <li>request list: ExchangeAttributes.requestList().readAttribute(exchange)
 * <li>request protocol:
 * ExchangeAttributes.requestProtocol().readAttribute(exchange)
 * <li>response code: ExchangeAttributes.responseCode()
 * <li>Location header:
 * ExchangeAttributes.responseHeader(HttpString.tryFromString(HttpHeaders.LOCATION)).readAttribute(exchange)
 * </ul>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public interface Checker extends Plugin {
    enum PHASE {
        BEFORE_WRITE,
        AFTER_WRITE // for optimistic checks, i.e. document is inserted and in case rolled back
    };

    /**
     *
     * @param exchange the server exchange
     * @param context the request context
     * @param contentToCheck the contet to check
     * @param args the args sepcified in the collection metadata via args
     * @return true if check completes successfully
     */
    boolean check(
            HttpServerExchange exchange,
            RequestContext context,
            BsonDocument contentToCheck,
            BsonValue args);

    /**
     *
     * @param exchange the server exchange
     * @param context the request context
     * @param args the args sepcified in the collection metadata via args property
     * @param confArgs the args specified in the configuration file via args property
     * @return true if check completes successfully
     */
    default boolean check(
            HttpServerExchange exchange,
            RequestContext context,
            BsonDocument contentToCheck,
            BsonValue args,
            BsonValue confArgs) {
        return check(exchange, context, contentToCheck, args);
    }

    /**
     * Specify when the checker should be performed: with BEFORE_WRITE the
     * checkers gets the request data (that may use the dot notation and update
     * operators); with AFTER_WRITE the data is optimistically written to the db
     * and rolled back eventually. Note that AFTER_WRITE helps checking data
     * with dot notation and update operators since the data to check is
     * retrieved normalized from the db.
     *
     * @param context
     * @return BEFORE_WRITE or AFTER_WRITE
     */
    PHASE getPhase(RequestContext context);

    /**
     *
     * @param context
     * @return true if the checker supports the requests
     */
    boolean doesSupportRequests(RequestContext context);
}
