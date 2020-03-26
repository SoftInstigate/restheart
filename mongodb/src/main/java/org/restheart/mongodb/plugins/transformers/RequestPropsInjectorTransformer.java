/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.plugins.transformers;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.handlers.exchange.RequestContext;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mongodb.Transformer;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
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
 * the properties that can be added are: userName, userRoles, epochTimeStamp,
 * dateTime, localIp, localPort, localServerName, queryString, relativePath,
 * remoteIp, requestMethod, requestProtocol
 *
 * <br>for instance, with the following definition:
 * <br>{"name":"addRequestProperties", "phase":"REQUEST", "args":{"log":
 * ["userName", "remoteIp"]}}
 * <br>injected properties are: log: { userName:"andrea", remoteIp:"127.0.0.1"}
 *
 * <br>for instance, with the following definition:
 * <br>{"name":"addRequestProperties", "phase":"REQUEST", "args":["userName",
 * "remoteIp"]}
 * <br>injected properties are: userName:"andrea", remoteIp:"127.0.0.1"
 *
 */
@RegisterPlugin(name = "addRequestProperties",
        description = "Adds properties to the request body")
@SuppressWarnings("deprecation")
public class RequestPropsInjectorTransformer implements Transformer {
    static final Logger LOGGER
            = LoggerFactory.getLogger(RequestPropsInjectorTransformer.class);

    /**
     *
     * @param exchange
     * @param context
     * @param contentToTransform
     * @param args properties to add
     */
    @Override
    public void transform(
            final HttpServerExchange exchange,
            final RequestContext context,
            BsonValue contentToTransform,
            final BsonValue args) {
        if (context.isGet()) {
            // nothing to do
            return;
        }

        if (contentToTransform == null) {
            // nothing to do
            return;
        }

        if (!contentToTransform.isDocument()) {
            throw new IllegalStateException(
                    "content to transform is not a document");
        }

        BsonDocument _contentToTransform = contentToTransform.asDocument();

        BsonDocument injected = new BsonDocument();

        if (args.isDocument()) {
            BsonDocument _args = args.asDocument();

            HashMap<String, BsonValue> properties
                    = getPropsValues(exchange, context);

            String firstKey = _args.keySet().iterator().next();

            BsonValue _toinject = _args.get(firstKey);

            if (_toinject.isArray()) {

                BsonArray toinject = _toinject.asArray();

                toinject.forEach(_el -> {
                    if (_el.isString()) {
                        String el = _el.asString().getValue();

                        BsonValue value = properties.get(el);

                        if (value != null) {
                            injected.put(el, value);
                        } else {
                            context.addWarning("property in the args list "
                                    + "does not have a value: " + _el);
                        }
                    } else {
                        context.addWarning("property in the args list "
                                + "is not a string: " + _el);
                    }
                });

                _contentToTransform.put(firstKey, injected);
            } else {
                context.addWarning("transformer wrong definition: "
                        + "args must be an object with a array containing "
                        + "the names of the properties to inject. got "
                        + _args.toJson());
            }
        } else if (args.isArray()) {
            HashMap<String, BsonValue> properties
                    = getPropsValues(exchange, context);

            BsonArray toinject = args.asArray();

            toinject.forEach(_el -> {
                if (_el.isString()) {
                    String el = _el.asString().getValue();

                    BsonValue value = properties.get(el);

                    if (value != null) {
                        injected.put(el, value);
                    } else {
                        context.addWarning("property in the args list does not have a value: " + _el);
                    }
                } else {
                    context.addWarning("property in the args list is not a string: " + _el);
                }
            });

            _contentToTransform.putAll(injected);
        } else {
            context.addWarning("transformer wrong definition: "
                    + "args must be an object with a array containing "
                    + "the names of the properties to inject. got "
                    + JsonUtils.toJson(args));
        }
    }

    HashMap<String, BsonValue> getPropsValues(
            final HttpServerExchange exchange,
            final RequestContext context) {
        HashMap<String, BsonValue> properties = new HashMap<>();

        String _userName = ExchangeAttributes
                        .remoteUser()
                        .readAttribute(exchange);
        
        BsonValue userName = _userName != null 
                ? new BsonString(_userName)
                : BsonNull.VALUE;
        
        // remote user
        properties.put("userName", userName);

        // user roles
        if (Objects.nonNull(exchange.getSecurityContext())
                && Objects.nonNull(
                        exchange.getSecurityContext()
                                .getAuthenticatedAccount())
                && Objects.nonNull(exchange
                        .getSecurityContext()
                        .getAuthenticatedAccount().getRoles())) {
            Set<String> roles = exchange
                    .getSecurityContext()
                    .getAuthenticatedAccount().getRoles();
            BsonArray _roles = new BsonArray();

            roles.stream()
                    .map(role -> new BsonString(role))
                    .forEachOrdered(role -> _roles.add(role));

            properties.put("userRoles", _roles);
        } else {
            properties.put("userRoles", new BsonNull());
        }

        // dateTime
        properties.put("epochTimeStamp",
                new BsonDateTime(Instant.now().getEpochSecond() * 1000));

        // dateTime
        properties.put("dateTime", new BsonString(
                ExchangeAttributes.dateTime().readAttribute(exchange)));

        // local ip
        properties.put("localIp", new BsonString(
                ExchangeAttributes.localIp().readAttribute(exchange)));

        // local port
        properties.put("localPort", new BsonString(
                ExchangeAttributes.localPort().readAttribute(exchange)));

        // local server name
        properties.put("localServerName", new BsonString(
                ExchangeAttributes.localServerName().readAttribute(exchange)));

        // request query string
        properties.put("queryString", new BsonString(
                ExchangeAttributes.queryString().readAttribute(exchange)));

        // request relative path
        properties.put("relativePath", new BsonString(
                ExchangeAttributes.relativePath().readAttribute(exchange)));

        // remote ip
        properties.put("remoteIp", new BsonString(
                ExchangeAttributes.remoteIp().readAttribute(exchange)));

        // request method
        properties.put("requestMethod", new BsonString(
                ExchangeAttributes.requestMethod().readAttribute(exchange)));

        // request protocol
        properties.put("requestProtocol", new BsonString(
                ExchangeAttributes.requestProtocol().readAttribute(exchange)));

        return properties;
    }
}
