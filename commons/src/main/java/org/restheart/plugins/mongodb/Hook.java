/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins.mongodb;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonValue;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.plugins.Plugin;

/**
 * An Hook is executed after requests completes.
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
 * @deprecated use org.restheart.plugins.Interceptor with
 * interceptPoint=RESPONSE_ASYNC instead
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 */
@Deprecated
public interface Hook extends Plugin {
    /**
     *
     * @param exchange the server exchange
     * @param context the request context
     * @param args the args specified in the collection metadata via args
     * property
     * @return true if completed successfully
     */
    default boolean hook(
            HttpServerExchange exchange,
            RequestContext context,
            BsonValue args) {
        return hook(exchange, context, args, null);
    }

    /**
     *
     * @param exchange the server exchange
     * @param context the request context
     * @param args the args specified in the collection metadata via args
     * property
     * @param confArgs args specified in the configuration file via args
     * property
     * @return true if completed successfully
     */
    default boolean hook(
            HttpServerExchange exchange,
            RequestContext context,
            BsonValue args,
            BsonValue confArgs) {
        return hook(exchange, context, args);
    }

    /**
     *
     * @param context
     * @return true if the hook supports the requests
     */
    boolean doesSupportRequests(RequestContext context);
}
