/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
package org.restheart.mongodb.interceptors;

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
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BsonUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * addRequestProperties adds properties to write requests on documents of
 * collections that have the following metadata:
 *
 * { "addRequestProperties": { "log": [ "userName", "remoteIp" ] } } ] }
 *
 * This is an example on how to refactor an old (before v5) Transformer as an
 * Interceptor. The Transformer was applied using collection metatada; here the
 * resolve() method checks if the collection properties obtained via the method
 * request.getCollectionProps() contains the expected metadata object.
 *
 * the addRequestProperties metadata object can be either an array of strings,
 * each specifiying the properties to add, or an object with a single property,
 * as in the above example where the properties are added to the request in the
 * nested "log" subdocument.
 *
 * the properties that can be added are: userName, userRoles, epochTimeStamp,
 * dateTime, localIp, localPort, localServerName, queryString, relativePath,
 * remoteIp, requestMethod, requestProtocol
 *
 */
@RegisterPlugin(name = "addRequestProperties",
        description = "Adds properties to write requests on documents of collections with 'addRequestProperties' metadata")
public class AddRequestProperties implements MongoInterceptor {
    /**
     *
     */
    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var args = request.getCollectionProps().get("addRequestProperties");

        var content = request.getContent() != null
                ? request.getContent()
                : new BsonDocument();

        if (content.isDocument()) {
            addProps(content.asDocument(), args, request, response);
        } else if (content.isArray()) {
            content.asArray()
                    .stream()
                    .filter(doc -> doc.isDocument())
                    .map(doc -> doc.asDocument())
                    .forEach(doc -> addProps(doc, args, request, response));
        }

        request.setContent(content);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return request.isHandledBy("mongo")
                && !request.isGet()
                && !request.isOptions()
                && !request.isDelete()
                && request.getCollectionProps() != null
                && request.getCollectionProps()
                        .containsKey("addRequestProperties")
                && (request.getCollectionProps()
                        .get("addRequestProperties").isArray()
                || request.getCollectionProps()
                        .get("addRequestProperties").isDocument());
    }

    private void addProps(BsonDocument doc, BsonValue propNames,
            MongoRequest request, MongoResponse response) {
        BsonDocument injected = new BsonDocument();

        if (propNames.isDocument()) {
            BsonDocument _args = propNames.asDocument();

            HashMap<String, BsonValue> propValues
                    = getPropsValues(request.getExchange());

            String firstKey = _args.keySet().iterator().next();

            BsonValue _toinject = _args.get(firstKey);

            if (_toinject.isArray()) {

                BsonArray toinject = _toinject.asArray();

                toinject.forEach(_el -> {
                    if (_el.isString()) {
                        String el = _el.asString().getValue();

                        BsonValue value = propValues.get(el);

                        if (value != null) {
                            injected.put(el, value);
                        }
                    }
                });

                doc.put(firstKey, injected);
            } else {
                response.addWarning("addRequestProperties wrong definition: "
                        + "must be an object with an array field containing "
                        + "the properties to inject. got "
                        + _args.toJson());
            }
        } else if (propNames.isArray()) {
            HashMap<String, BsonValue> properties = getPropsValues(request.getExchange());

            BsonArray toinject = propNames.asArray();

            toinject.forEach(_el -> {
                if (_el.isString()) {
                    String el = _el.asString().getValue();

                    BsonValue value = properties.get(el);

                    if (value != null) {
                        injected.put(el, value);
                    }
                }
            });

            doc.putAll(injected);
        } else {
            response.addWarning("addRequestProperties wrong definition: "
                    + "must be an array containing "
                    + "the properties to inject. got "
                    + BsonUtils.toJson(propNames));
        }
    }

    private HashMap<String, BsonValue> getPropsValues(
            final HttpServerExchange exchange) {
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
