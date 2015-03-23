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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import java.util.HashMap;
import java.util.Objects;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 *
 * this transformer adds log info to the properties of the resource
 * representation.
 *
 * for instance, log properties are: log: { userName:"andrea",
 * remoteIp:"127.0.0.1"}
 *
 * it is intended to be applied on REQUEST phase
 *
 * the properties to log are passed in the args argumenet as an array of
 * strings, each sting specifiying the property name:
 *
 * userName, userRoles, dateTime, localIp, localPort, localServerName,
 * queryString, relativePath, remoteIp, requestMethod, requestProtocol
 *
 */
public class RequetPropsInjecterTransformer implements Transformer {
    static final Logger LOGGER = LoggerFactory.getLogger(RequetPropsInjecterTransformer.class);

    /**
     *
     * @param exchange
     * @param context
     * @param contentToTransform
     * @param args properties to add
     */
    @Override
    public void tranform(final HttpServerExchange exchange, final RequestContext context, DBObject contentToTransform, final DBObject args) {
        BasicDBObject log = new BasicDBObject();

        if (args instanceof BasicDBList) {
            HashMap<String, String> properties = getPropsValues(exchange, context);

            BasicDBList toinject = (BasicDBList) args;

            toinject.forEach(_el -> {
                if (_el instanceof String) {
                    String el = (String) _el;

                    String value = properties.get(el);

                    log.put(el, value);

                } else {
                    context.addWarning("property in the args list is not a string: " + _el);
                }
            });

            contentToTransform.put("_log", log);
        } else {
            context.addWarning("transformer wrong definition: args property must be an arrary of string property names.");
        }

    }

    HashMap<String, String> getPropsValues(final HttpServerExchange exchange, final RequestContext context) {
        HashMap<String, String> properties = new HashMap<>();

        // remote user
        properties.put("userName", ExchangeAttributes.remoteUser().readAttribute(exchange));

        // user roles
        if (Objects.nonNull(exchange.getSecurityContext())
                && Objects.nonNull(exchange.getSecurityContext().getAuthenticatedAccount())
                && Objects.nonNull(exchange.getSecurityContext().getAuthenticatedAccount().getRoles())) {
            properties.put("userRoles", exchange.getSecurityContext().getAuthenticatedAccount().getRoles().toString());
        } else {
            properties.put("userRoles", null);
        }

        // dateTime
        properties.put("dateTime", ExchangeAttributes.dateTime().readAttribute(exchange));

        // local ip
        properties.put("localIp", ExchangeAttributes.localIp().readAttribute(exchange));

        // local port
        properties.put("localPort", ExchangeAttributes.localPort().readAttribute(exchange));

        // local server name
        properties.put("localServerName", ExchangeAttributes.localServerName().readAttribute(exchange));

        // request query string
        properties.put("queryString", ExchangeAttributes.queryString().readAttribute(exchange));

        // request relative path
        properties.put("relativePath", ExchangeAttributes.relativePath().readAttribute(exchange));

        // remote ip
        properties.put("remoteIp", ExchangeAttributes.remoteIp().readAttribute(exchange));

        // request method
        properties.put("requestMethod", ExchangeAttributes.requestMethod().readAttribute(exchange));

        // request protocol
        properties.put("requestProtocol", ExchangeAttributes.requestProtocol().readAttribute(exchange));

        return properties;
    }
}
