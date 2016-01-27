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
package org.restheart.hal.metadata.singletons;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.RequestContext;

/**
 * A Checker performs validation on write requests. If the check fails, request
 * fails with status code BAD_REQUEST
 *
 * note: data to be checked is the argument contentToCheck. this can differ from
 * context.getContent() on bulk requests where it is an array of objects
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 *
 * some useful info that can be retrived from arguments request content
 * context.getContent() response content context.getgetResponseContent() old
 * data contenx.getDbOperationResult.getOldData() written data
 * contenx.getDbOperationResult.getNewData() remote user
 * ExchangeAttributes.remoteUser().readAttribute(exchange) user roles
 * exchange.getSecurityContext().getAuthenticatedAccount().getRoles() resource
 * type context.getPhase() dateTime
 * ExchangeAttributes.dateTime().readAttribute(exchange) local ip
 * ExchangeAttributes.localIp().readAttribute(exchange) local port
 * ExchangeAttributes.localPort().readAttribute(exchange) local server name
 * ExchangeAttributes.localServerName().readAttribute(exchange) query string
 * ExchangeAttributes.queryString().readAttribute(exchange) relative path
 * ExchangeAttributes.relativePath().readAttribute(exchange) remote ip
 * ExchangeAttributes.remoteIp().readAttribute(exchange) ETag in request
 * ExchangeAttributes.requestHeader(HttpString.tryFromString(HttpHeaders.ETAG)).readAttribute(exchange)
 * request list ExchangeAttributes.requestList().readAttribute(exchange) request
 * method ExchangeAttributes.requestMethod().readAttribute(exchange) request
 * protocol ExchangeAttributes.requestProtocol().readAttribute(exchange)
 * response code ExchangeAttributes.responseCode() Location header
 * ExchangeAttributes.responseHeader(HttpString.tryFromString(HttpHeaders.LOCATION)).readAttribute(exchange)
 */
public interface Checker {
    enum PHASE {
        BEFORE_WRITE,
        AFTER_WRITE // for optimistic checks, i.e. document is inserted and in case rolled back
    };
    
    boolean check(
            HttpServerExchange exchange,
            RequestContext context,
            BasicDBObject contentToCheck,
            DBObject args);

    /**
     * Specify when the checker should be performed: with BEFORE_WRITE the check
     * will get the request content; with AFTER_WRITE it gets the data actually
     * optimistally written to the db (and rolled back eventually)
     *
     * @return BEFORE_WRITE or AFTER_WRITE
     */
    PHASE getPhase();

    /**
     *
     * @param context
     * @return true if the checker supports the requests
     */
    boolean doesSupportRequests(RequestContext context);

    /**
     *
     * Specify if the check should fail if this checker does not support the
     * request
     *
     * @param args
     * @return true if the check should fail if this checker does not support it
     */
    boolean shouldCheckFailIfNotSupported(DBObject args);
}
