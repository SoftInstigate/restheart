/*
 * RESTHeart - the data REST API server
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

import com.mongodb.DBObject;
import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 * 
 * some useful info that can be retrived from arguments
 * request content      context.getContent()
 * response content     context.getgetResponseContent()
 * remote user          ExchangeAttributes.remoteUser().readAttribute(exchange)
 * user roles           exchange.getSecurityContext().getAuthenticatedAccount().getRoles()
 * resource type        context.getType()
 * dateTime             ExchangeAttributes.dateTime().readAttribute(exchange)
 * local ip             ExchangeAttributes.localIp().readAttribute(exchange)
 * local port           ExchangeAttributes.localPort().readAttribute(exchange)
 * local server name    ExchangeAttributes.localServerName().readAttribute(exchange)
 * query string         ExchangeAttributes.queryString().readAttribute(exchange)
 * relative path        ExchangeAttributes.relativePath().readAttribute(exchange)
 * remote ip            ExchangeAttributes.remoteIp().readAttribute(exchange)
 * ETag in request      ExchangeAttributes.requestHeader(HttpString.tryFromString(HttpHeaders.ETAG)).readAttribute(exchange)
 * request list         ExchangeAttributes.requestList().readAttribute(exchange)
 * request method       ExchangeAttributes.requestMethod().readAttribute(exchange)
 * request protocol     ExchangeAttributes.requestProtocol().readAttribute(exchange)
 * response code        ExchangeAttributes.responseCode()
 * Location header      ExchangeAttributes.responseHeader(HttpString.tryFromString(HttpHeaders.LOCATION)).readAttribute(exchange)
*/
public interface Transformer {
    void tranform(final HttpServerExchange exchange, final RequestContext context, DBObject contentToTransform, final DBObject args);
}