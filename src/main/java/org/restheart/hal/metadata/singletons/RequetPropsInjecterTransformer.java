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
import org.bson.BSONObject;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 *
 * this transformer adds properties to the resource representation. usually it
 * is applied on REQUEST phase to store properties evaluated server side
 *
 * the properties to add are passed in the args argumenet
 *
 * ags can be an array of strings, each specifiying the names of the properties
 * to add or an object with a single property of an array of strings. in the
 * latter case, the properties are added to the representation nested in the
 * passed object property key.
 *
 * the properties that can be added are: userName, userRoles, dateTime, localIp,
 * localPort, localServerName, queryString, relativePath, remoteIp,
 * requestMethod, requestProtocol
 *
 * <br>for instance, with the following definition:
 * <br>{name:"addRequestProperties", "phase":"REQUEST", "scope":"CHILDREN",
 * args:{"log": ["userName", "remoteIp"]}}
 * <br>injected properties are: log: { userName:"andrea", remoteIp:"127.0.0.1"}
 *
 * <br>for instance, with the following definition:
 * <br>{name:"addRequestProperties", "phase":"REQUEST", "scope":"CHILDREN",
 * args:["userName", "remoteIp"]}
 * <br>injected properties are: userName:"andrea", remoteIp:"127.0.0.1"
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
        BasicDBObject injected = new BasicDBObject();

        if (args instanceof BasicDBObject) {
            HashMap<String, String> properties = getPropsValues(exchange, context);

            String firstKey = args.keySet().iterator().next();

            Object _toinject = args.get(firstKey);

            if (_toinject instanceof BasicDBList) {

                BasicDBList toinject = (BasicDBList) _toinject;

                toinject.forEach(_el -> {
                    if (_el instanceof String) {
                        String el = (String) _el;

                        String value = properties.get(el);

                        injected.put(el, value);

                    } else {
                        context.addWarning("property in the args list is not a string: " + _el);
                    }
                });

                contentToTransform.put(firstKey, injected);
            } else {
                context.addWarning("transformer wrong definition: args must be an object with a array containing the names of the properties to inject. got " + JsonUtils.serialize(args));
            }
        } else if (args instanceof BasicDBList) {
            HashMap<String, String> properties = getPropsValues(exchange, context);
            
            BasicDBList toinject = (BasicDBList) args;

            toinject.forEach(_el -> {
                if (_el instanceof String) {
                    String el = (String) _el;

                    String value = properties.get(el);

                    injected.put(el, value);

                } else {
                    context.addWarning("property in the args list is not a string: " + _el);
                }
            });

            contentToTransform.putAll((BSONObject)injected);
        } else {
            context.addWarning("transformer wrong definition: args must be an object with a array containing the names of the properties to inject. got " + JsonUtils.serialize(args));
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
