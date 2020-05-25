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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import static org.restheart.security.plugins.authorizers.MongoAclAuthorizer.MATCHING_ACL_PREDICATE;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;
import io.undertow.server.HttpServerExchange;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class FilterPredicate {
    private final BsonValue _id;
    private final Set<String> roles;
    private final Predicate predicate;
    private final BsonDocument readFilter;
    private final BsonDocument writeFilter;
    private final int priority;

    private static final Logger LOGGER
            = LoggerFactory.getLogger(FilterPredicate.class);

    FilterPredicate(BsonValue _id,
            Set<String> roles,
            Predicate predicate,
            BsonDocument readFilter,
            BsonDocument writeFilter,
            int priority) {
        this._id = _id;
        this.roles = roles;
        this.predicate = predicate;
        this.readFilter = readFilter;
        this.writeFilter = writeFilter;
        this.priority = priority;
    }

    FilterPredicate(BsonDocument doc) {
        this._id = doc.get("_id");

        var _roles = doc.get("roles");

        if (_roles == null
                || !_roles.isArray()
                || _roles.asArray().isEmpty()) {
            throw new IllegalArgumentException("roles must be an not empty array of strings");
        }

        if (StreamSupport.stream(_roles.asArray().spliterator(), true)
                .anyMatch(el -> el == null
                || !el.isString())) {
            throw new IllegalArgumentException("roles must be an not empty array of strings");
        }

        this.roles
                = StreamSupport.stream(_roles.asArray().spliterator(), true)
                        .map(role -> role.asString())
                        .map(role -> role.getValue())
                        .collect(Collectors.toSet());

        var _predicate = doc.get("predicate");

        if (_predicate == null || !_predicate.isString()) {
            throw new IllegalArgumentException("_predicate must be a string");
        }

        try {
            this.predicate = PredicateParser.parse(
                    _predicate.asString().getValue(),
                    this.getClass().getClassLoader());
        } catch (Throwable t) {
            throw new IllegalArgumentException("wrong predicate " + _predicate, t);
        }

        var _readFilter = doc.get("readFilter");

        if (!(_readFilter == null || _readFilter.isNull())
                && !_readFilter.isDocument()) {
            throw new IllegalArgumentException("readFilter must be a JSON object or null");
        }

        this.readFilter = _readFilter == null
                ? null
                : _readFilter.isNull()
                ? null : JsonUtils.escapeKeys(_readFilter.asDocument(), true)
                        .asDocument();

        var _writeFilter = doc.get("writeFilter");

        if (!(_writeFilter == null || _writeFilter.isNull())
                && !_writeFilter.isDocument()) {
            throw new IllegalArgumentException("writeFilter must be a JSON object or null");
        }

        this.writeFilter = _writeFilter == null
                ? null
                : _writeFilter.isNull()
                ? null : JsonUtils.escapeKeys(_writeFilter.asDocument(), true)
                        .asDocument();

        var _priority = doc.get("priority");

        if (_priority == null
                || _priority.isNull()
                || !_priority.isNumber()) {
            this.priority = Integer.MAX_VALUE; // very low priority

            LOGGER.warn("predicate {} doesn't have priority; setting it to 0",
                    this._id);
        } else {
            this.priority = _priority.asNumber().intValue();
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
        return readFilter == null || readFilter.isNull()
                ? null
                : JsonUtils.unescapeKeys(readFilter).asDocument();
    }

    /**
     * @return the writeFilter
     */
    public BsonDocument getWriteFilter() {
        return writeFilter == null || writeFilter.isNull()
                ? writeFilter
                : JsonUtils.unescapeKeys(writeFilter).asDocument();
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
    public BsonValue getId() {
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
            final BsonDocument filter) {
        if (Objects.isNull(filter) || filter.isNull()) {
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
