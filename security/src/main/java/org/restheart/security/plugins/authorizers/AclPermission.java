/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package org.restheart.security.plugins.authorizers;

import static org.restheart.plugins.ConfigurablePlugin.argValue;
import static org.restheart.security.plugins.authorizers.MongoAclAuthorizer.MATCHING_ACL_PERMISSION;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.json.JsonParseException;
import org.restheart.ConfigurationException;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;
import io.undertow.server.HttpServerExchange;

/**
 * ACL Permission that specifies the conditions that are necessary to perform
 * the request
 *
 * The request is authorized if AclPermission.resolve() to true
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AclPermission {
    private final BsonValue _id;
    private final Set<String> roles;
    private final Predicate predicate;
    private final BsonDocument readFilter;
    private final BsonDocument writeFilter;
    private final int priority;

    private static final Logger LOGGER = LoggerFactory.getLogger(AclPermission.class);

    AclPermission(BsonValue _id, Set<String> roles, Predicate predicate, BsonDocument readFilter,
            BsonDocument writeFilter, int priority) {
        this._id = _id;
        this.roles = roles;
        this.predicate = predicate;
        this.readFilter = readFilter;
        this.writeFilter = writeFilter;
        this.priority = priority;
    }

    /**
     * Constructor from MongoDb document
     * 
     * @param doc
     */
    AclPermission(BsonDocument doc) throws ConfigurationException {
        this._id = doc.get("_id");

        var _roles = doc.get("roles");

        if (_roles == null || !_roles.isArray() || _roles.asArray().isEmpty()) {
            throw new ConfigurationException("roles must be an not empty array of strings");
        }

        if (StreamSupport.stream(_roles.asArray().spliterator(), true).anyMatch(el -> el == null || !el.isString())) {
            throw new ConfigurationException("roles must be an not empty array of strings");
        }

        this.roles = StreamSupport.stream(_roles.asArray().spliterator(), true).map(role -> role.asString())
                .map(role -> role.getValue()).collect(Collectors.toSet());

        var _predicate = doc.get("predicate");

        if (_predicate == null || !_predicate.isString()) {
            throw new ConfigurationException("_predicate must be a string");
        }

        try {
            this.predicate = PredicateParser.parse(_predicate.asString().getValue(), this.getClass().getClassLoader());
        } catch (Throwable t) {
            throw new ConfigurationException("wrong predicate " + _predicate, t);
        }

        var _readFilter = doc.get("readFilter");

        if (!(_readFilter == null || _readFilter.isNull()) && !_readFilter.isDocument()) {
            throw new ConfigurationException("readFilter must be a JSON object or null");
        }

        this.readFilter = _readFilter == null ? null
                : _readFilter.isNull() ? null : JsonUtils.escapeKeys(_readFilter.asDocument(), true).asDocument();

        var _writeFilter = doc.get("writeFilter");

        if (!(_writeFilter == null || _writeFilter.isNull()) && !_writeFilter.isDocument()) {
            throw new ConfigurationException("writeFilter must be a JSON object or null");
        }

        this.writeFilter = _writeFilter == null ? null
                : _writeFilter.isNull() ? null : JsonUtils.escapeKeys(_writeFilter.asDocument(), true).asDocument();

        var _priority = doc.get("priority");

        if (_priority == null || _priority.isNull() || !_priority.isNumber()) {
            this.priority = Integer.MAX_VALUE; // very low priority

            LOGGER.warn("predicate {} doesn't have priority; setting it to 0", this._id);
        } else {
            this.priority = _priority.asNumber().intValue();
        }
    }

    /**
     * Constructor from file definition
     *
     * @param seq
     * @param args
     * @throws ConfigurationException
     */
    AclPermission(Map<String, Object> args) throws ConfigurationException {
        this._id = null;

        this.roles = new LinkedHashSet<String>();

        if (args.containsKey("role") && args.containsKey("roles")) {
            throw new ConfigurationException(
                    "Wrong permission: it specifies both 'role' and 'roles'; it requires just one or the other.");
        } else if (args.containsKey("role")) {
            this.roles.add(argValue(args, "role"));
        } else if (args.containsKey("roles")) {
            this.roles.addAll(argValue(args, "roles"));
        } else {
            throw new ConfigurationException("Wrong permission: does not specify 'role' or 'roles'.");
        }

        if (!args.containsKey("predicate")) {
            throw new ConfigurationException("Wrong permission: missing 'predicate'");
        }

        String _predicate = argValue(args, "predicate");

        if (_predicate == null) {
            throw new ConfigurationException("Wrong permission: 'predicate' cannot be null");
        }

        try {
            this.predicate = PredicateParser.parse(_predicate, this.getClass().getClassLoader());
        } catch (Throwable t) {
            throw new ConfigurationException("Wrong permission: invalid predicate: " + _predicate, t);
        }

        if (args.containsKey("readFilter")) {
            try {
                String __readFilter = argValue(args, "readFilter");

                var _readFilter = BsonDocument.parse(__readFilter);

                if (!(_readFilter == null || _readFilter.isNull()) && !_readFilter.isDocument()) {
                    throw new IllegalArgumentException("Wrong permission: readFilter must be a JSON object or null");
                }

                this.readFilter = _readFilter == null ? null
                        : _readFilter.isNull() ? null
                                : JsonUtils.escapeKeys(_readFilter.asDocument(), true).asDocument();
            } catch (ClassCastException | JsonParseException jpe) {
                throw new ConfigurationException(
                        "Wrong permission: the readFilter is not a string containing a JSON Object", jpe);
            }
        } else {
            this.readFilter = null;
        }

        if (args.containsKey("writeFilter")) {
            try {
                String __writeFilter = argValue(args, "writeFilter");

                var _writeFilter = BsonDocument.parse(__writeFilter);

                if (!(_writeFilter == null || _writeFilter.isNull()) && !_writeFilter.isDocument()) {
                    throw new ConfigurationException("writeFilter must be a JSON object or null");
                }

                this.writeFilter = _writeFilter == null ? null
                        : _writeFilter.isNull() ? null
                                : JsonUtils.escapeKeys(_writeFilter.asDocument(), true).asDocument();
            } catch (ClassCastException | JsonParseException jpe) {
                throw new ConfigurationException(
                        "Wrong permission: the writeFilter is not a string containing a JSON Object", jpe);
            }
        } else {
            this.writeFilter = null;
        }

        if (args.containsKey("priority")) {
            this.priority = argValue(args, "priority");
        } else {
            LOGGER.warn("Predicate {} {} doesn't have priority; setting it to 0", this.roles, this.predicate);
            this.priority = Integer.MAX_VALUE; // very low priority
        }
    }

    /**
     * @return the roles
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * @return the predicate
     */
    public Predicate getPredicate() {
        return predicate;
    }

    /**
     * @return the readFilter
     */
    public BsonDocument getReadFilter() {
        return readFilter == null || readFilter.isNull() ? null : JsonUtils.unescapeKeys(readFilter).asDocument();
    }

    /**
     * @return the writeFilter
     */
    public BsonDocument getWriteFilter() {
        return writeFilter == null || writeFilter.isNull() ? writeFilter
                : JsonUtils.unescapeKeys(writeFilter).asDocument();
    }

    /**
     * lesser is higher priority
     *
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * @return the _id
     */
    public BsonValue getId() {
        return _id;
    }

    /**
     *
     * @param exchange
     * @return the acl predicate associated with this request
     */
    public static AclPermission from(final HttpServerExchange exchange) {
        return exchange.getAttachment(MATCHING_ACL_PERMISSION);
    }

    public boolean resolve(final HttpServerExchange exchange) {
        if (this.predicate == null) {
            return false;
        } else {
            return this.predicate.resolve(exchange);
        }
    }

    /**
     * resolves the a filter variables such as %USER, %ROLES, and %NOW
     *
     * @param exchange
     * @param filter
     * @return the filter with interpolated variables
     */
    public static JsonObject interpolateFilterVars(final HttpServerExchange exchange, final BsonDocument filter) {
        if (Objects.isNull(filter) || filter.isNull()) {
            return null;
        }

        String ret = filter.toString();

        String username = ExchangeAttributes.remoteUser().readAttribute(exchange);

        if (username != null) {
            ret = ret.replace("%USER", username);
        }

        // user roles
        if (Objects.nonNull(exchange.getSecurityContext())
                && Objects.nonNull(exchange.getSecurityContext().getAuthenticatedAccount())
                && Objects.nonNull(exchange.getSecurityContext().getAuthenticatedAccount().getRoles())) {
            String roles = exchange.getSecurityContext().getAuthenticatedAccount().getRoles().toString();

            ret = ret.replace("%ROLES", roles);
        } else {
            ret = ret.replace("%ROLES", "[]");
        }

        // now
        long now = Instant.now().getEpochSecond() * 1000;
        ret = ret.replace("%NOW", "{'$date':" + now + "}");

        return JsonParser.parseString(ret).getAsJsonObject();
    }
}
