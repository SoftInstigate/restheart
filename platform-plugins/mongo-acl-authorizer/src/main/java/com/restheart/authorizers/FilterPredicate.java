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
package com.restheart.authorizers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.restheart.authorizers.RHAuthorizer.MATCHING_ACL_PREDICATE;
import com.softinstigate.restheart.utils.JsonUtils;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class FilterPredicate {
    private final JsonElement _id;
    private final Set<String> roles;
    private final Predicate predicate;
    private final JsonObject readFilter;
    private final JsonObject writeFilter;
    private final int priority;

    private static final Logger LOGGER
            = LoggerFactory.getLogger(FilterPredicate.class);

    FilterPredicate(JsonElement _id,
            Set<String> roles,
            Predicate predicate,
            JsonObject readFilter,
            JsonObject writeFilter,
            int priority) {
        this._id = _id;
        this.roles = roles;
        this.predicate = predicate;
        this.readFilter = readFilter;
        this.writeFilter = writeFilter;
        this.priority = priority;
    }

    FilterPredicate(JsonObject doc) {
        this._id = doc.get("_id");

        var _roles = doc.get("roles");

        if (_roles == null
                || !_roles.isJsonArray()
                || _roles.getAsJsonArray().size() == 0) {
            throw new IllegalArgumentException("roles must be an not empty array of strings");
        }

        if (StreamSupport.stream(_roles.getAsJsonArray().spliterator(), true)
                .anyMatch(el -> el == null
                || !el.isJsonPrimitive()
                || !el.getAsJsonPrimitive().isString())) {
            throw new IllegalArgumentException("roles must be an not empty array of strings");
        }

        this.roles
                = StreamSupport.stream(_roles.getAsJsonArray().spliterator(), true)
                        .map(role -> role.getAsString())
                        .collect(Collectors.toSet());

        var _predicate = doc.get("predicate");

        if (_predicate == null
                || !_predicate.isJsonPrimitive()
                || !_predicate.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("_predicate must be a string");
        }

        try {
            this.predicate = PredicateParser.parse(
                    _predicate.getAsString(),
                    this.getClass().getClassLoader());
        } catch (Throwable t) {
            throw new IllegalArgumentException("wrong predicate " + _predicate, t);
        }

        var _readFilter = doc.get("readFilter");

        if (!(_readFilter == null || _readFilter.isJsonNull())
                && !_readFilter.isJsonObject()) {
            throw new IllegalArgumentException("readFilter must be a JSON object or null");
        }

        this.readFilter = _readFilter == null
                ? null
                : _readFilter.isJsonNull()
                ? null : JsonUtils.escapeKeys(_readFilter.getAsJsonObject(), true)
                        .getAsJsonObject();

        var _writeFilter = doc.get("writeFilter");

        if (!(_writeFilter == null || _writeFilter.isJsonNull())
                && !_writeFilter.isJsonObject()) {
            throw new IllegalArgumentException("writeFilter must be a JSON object or null");
        }

        this.writeFilter = _writeFilter == null
                ? null
                : _writeFilter.isJsonNull()
                ? null : JsonUtils.escapeKeys(_writeFilter.getAsJsonObject(), true)
                        .getAsJsonObject();

        var _priority = doc.get("priority");

        if (_priority == null
                || _priority.isJsonNull()
                || !_priority.getAsJsonPrimitive().isNumber()) {
            this.priority = _priority.getAsNumber().intValue();

            LOGGER.warn("predicate {} doesn't have priority; setting it to 0",
                    this._id);
        } else {
            this.priority = _priority.getAsInt();
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
    public JsonObject getReadFilter() {
        return readFilter == null || readFilter.isJsonNull()
                ? null
                : JsonUtils.unescapeKeys(readFilter).getAsJsonObject();
    }

    /**
     * @return the writeFilter
     */
    public JsonObject getWriteFilter() {
        return writeFilter == null || writeFilter.isJsonNull()
                ? writeFilter
                : JsonUtils.unescapeKeys(writeFilter).getAsJsonObject();
    }

    /**
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * @return the _id
     */
    public JsonElement getId() {
        return _id;
    }

    /**
     *
     * @param exchange
     * @return the acl predicate associated with this request
     */
    public static FilterPredicate from(final HttpServerExchange exchange) {
        return exchange.getAttachment(MATCHING_ACL_PREDICATE);
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
    public static JsonObject interpolateFilterVars(final HttpServerExchange exchange,
            final JsonObject filter) {
        if (Objects.isNull(filter) || filter.isJsonNull()) {
            return null;
        }

        String ret = filter.toString();

        String username = ExchangeAttributes
                .remoteUser()
                .readAttribute(exchange);

        if (username != null) {
            ret = ret.replace("%USER", username);
        }

        // user roles
        if (Objects.nonNull(exchange.getSecurityContext())
                && Objects.nonNull(
                        exchange.getSecurityContext()
                                .getAuthenticatedAccount())
                && Objects.nonNull(exchange
                        .getSecurityContext()
                        .getAuthenticatedAccount().getRoles())) {
            String roles = exchange
                    .getSecurityContext()
                    .getAuthenticatedAccount()
                    .getRoles()
                    .toString();

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
