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
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class TestTransformer implements Transformer {
    static final Logger LOGGER = LoggerFactory.getLogger(TestTransformer.class);

    @Override
    public void tranform(final HttpServerExchange exchange, final RequestContext context, DBObject contentToTransform, final DBObject args) {
        LOGGER.info("adding user name {}" + ExchangeAttributes.remoteUser().readAttribute(exchange));
        
        contentToTransform.put("user", ExchangeAttributes.remoteUser().readAttribute(exchange));
    }
    
}

/*
        // bind the request and response content json
        bindings.put("$content", context.getContent());
        bindings.put("$responseContent", context.getResponseContent());
        
        bindings.put("$user", ExchangeAttributes.remoteUser().readAttribute(exchange));
        
        if (exchange.getSecurityContext() != null && 
                exchange.getSecurityContext().getAuthenticatedAccount() != null &&
                exchange.getSecurityContext().getAuthenticatedAccount().getRoles() != null)
            bindings.put("$userRoles", exchange.getSecurityContext().getAuthenticatedAccount().getRoles().toArray());
        else
            bindings.put("$userRoles", new String[0]);
        
        bindings.put("$resourceType", context.getType().name());

        // add request and response attributes
        bindings.put("$dateTime", ExchangeAttributes.dateTime().readAttribute(exchange));
        bindings.put("$localIp", ExchangeAttributes.localIp().readAttribute(exchange));
        bindings.put("$localPort", ExchangeAttributes.localPort().readAttribute(exchange));
        bindings.put("$localServerName", ExchangeAttributes.localServerName().readAttribute(exchange));
        bindings.put("$queryString", ExchangeAttributes.queryString().readAttribute(exchange));
        bindings.put("$relativePath", ExchangeAttributes.relativePath().readAttribute(exchange));
        bindings.put("$remoteIp", ExchangeAttributes.requestHeader(HttpString.EMPTY).readAttribute(exchange));
        // TODO add more headers
        bindings.put("$etag", ExchangeAttributes.requestHeader(HttpString.tryFromString(HttpHeaders.ETAG)).readAttribute(exchange));
        
        bindings.put("$requestList", ExchangeAttributes.requestList().readAttribute(exchange));
        bindings.put("$requestMethod", ExchangeAttributes.requestMethod().readAttribute(exchange));
        bindings.put("$requestProtocol", ExchangeAttributes.requestProtocol().readAttribute(exchange));
        bindings.put("$requestURL", ExchangeAttributes.requestURL().readAttribute(exchange));
        bindings.put("$responseCode", ExchangeAttributes.responseCode().readAttribute(exchange));
        // TODO add more headers
        bindings.put("$location", ExchangeAttributes.responseHeader(HttpString.tryFromString(HttpHeaders.LOCATION)).readAttribute(exchange));

        // bing usefull objects
        bindings.put("$timestamp", new org.bson.types.BSONTimestamp());
        bindings.put("$currentDate", new Date());
        
        return bindings;
*/
