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
package org.restheart.mongodb.interceptors;

import com.google.common.collect.Lists;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.mongodb.handlers.metadata.InvalidMetadataException;
import org.restheart.plugins.BsonInterceptor;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.JsonUtils;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * addRequestProperties adds properties to write requests on documents of
 * collections that have the following metadata:
 *
 * { "rts": [ { "name":"addRequestProperties", "args": {"log": ["userName",
 * "remoteIp"] } } ] }
 *
 * This is an example on how to refactor an old (<v5) Transformer as an
 * Interceptor. The Transformer was applied using collection metatada; here the
 * resolve() method checks if the collection properties obtained via the method
 * request.getCollectionProps() contains the expected metadata object.
 *
 * args can be either an array of strings, each specifiying the properties to
 * add, or an object with a single property, as in the above example where the
 * properties are added to the request in the nested "log" subdocument.
 *
 * the properties that can be added are: userName, userRoles, epochTimeStamp,
 * dateTime, localIp, localPort, localServerName, queryString, relativePath,
 * remoteIp, requestMethod, requestProtocol
 *
 */
@RegisterPlugin(name = "addRequestProperties",
        description = "Adds properties to write requests on documents of collections with special metadata")
public class AddRequestProperties implements BsonInterceptor {
    /**
     *
     */
    @Override
    public void handle(BsonRequest request, BsonResponse response) throws Exception {
        var args = Metadata
                .getFromJson(request.getCollectionProps())
                .stream()
                .filter(rt -> "addRequestProperties".equals(rt.getName()))
                .findFirst()
                .get()
                .getArgs();

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
    public boolean resolve(BsonRequest request, BsonResponse response) {
        return !request.isGet()
                && !request.isOptions()
                && !request.isDelete()
                && Metadata
                        .getFromJson(request.getCollectionProps())
                        .stream()
                        .filter(rt -> "addRequestProperties".equals(rt.getName()))
                        .findFirst().isPresent();
    }

    private void addProps(BsonDocument doc, BsonValue args, BsonRequest request, BsonResponse response) {
        BsonDocument injected = new BsonDocument();

        if (args.isDocument()) {
            BsonDocument _args = args.asDocument();

            HashMap<String, BsonValue> properties
                    = getPropsValues(request.getExchange());

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
                            response.addWarning("property in the args list "
                                    + "does not have a value: " + _el);
                        }
                    } else {
                        response.addWarning("property in the args list "
                                + "is not a string: " + _el);
                    }
                });

                doc.put(firstKey, injected);
            } else {
                response.addWarning("transformer wrong definition: "
                        + "args must be an object with a array containing "
                        + "the names of the properties to inject. got "
                        + _args.toJson());
            }
        } else if (args.isArray()) {
            HashMap<String, BsonValue> properties = getPropsValues(request.getExchange());

            BsonArray toinject = args.asArray();

            toinject.forEach(_el -> {
                if (_el.isString()) {
                    String el = _el.asString().getValue();

                    BsonValue value = properties.get(el);

                    if (value != null) {
                        injected.put(el, value);
                    } else {
                        response.addWarning("property in the args list does not have a value: " + _el);
                    }
                } else {
                    response.addWarning("property in the args list is not a string: " + _el);
                }
            });

            doc.putAll(injected);
        } else {
            response.addWarning("transformer wrong definition: "
                    + "args must be an object with a array containing "
                    + "the names of the properties to inject. got "
                    + JsonUtils.toJson(args));
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

class Metadata {

    /**
     *
     */
    public static final String RTS_ELEMENT_NAME = "rts";

    /**
     *
     */
    public static final String RT_NAME_ELEMENT_NAME = "name";

    /**
     *
     */
    public static final String RT_ARGS_ELEMENT_NAME = "args";

    /**
     *
     * @param props
     * @return
     * @throws InvalidMetadataException
     */
    public static List<Metadata> getFromJson(BsonDocument props) {
        BsonValue _rts = props.get(RTS_ELEMENT_NAME);

        if (_rts == null || !_rts.isArray()) {
            return Lists.newArrayList();
        }

        BsonArray rts = _rts.asArray();

        List<Metadata> ret = new ArrayList<>();

        rts.getValues().stream()
                .filter(o -> (o.isDocument()))
                .forEachOrdered(o -> ret.add(getSingleFromJson(o.asDocument())));

        return ret;
    }

    private static Metadata getSingleFromJson(BsonDocument props) {
        BsonValue _name = props.get(RT_NAME_ELEMENT_NAME);
        BsonValue _args = props.get(RT_ARGS_ELEMENT_NAME);

        String name;

        if (_name == null || !_name.isString()) {
            name = null;
        } else {
            name = _name.asString().getValue();
        }

        return new Metadata(name, _args);
    }

    private final String name;
    private final BsonValue args;

    /**
     *
     * @param phase
     * @param scope
     * @param name the name of the transfromer as specified in the yml
     * configuration file
     * @param args
     */
    public Metadata(String name, BsonValue args) {
        this.name = name;
        this.args = args;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the args
     */
    public BsonValue getArgs() {
        return args;
    }
}
