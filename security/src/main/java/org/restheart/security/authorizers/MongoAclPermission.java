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
package org.restheart.security.authorizers;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.security.AclVarsInterpolator;
import org.restheart.security.BaseAclPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class MongoAclPermission extends BaseAclPermission {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoAclPermission.class);

    private final BsonValue _id;

    MongoAclPermission(BsonValue _id, String predicate, Set<String> roles, int priority, BsonDocument raw) {
        super(predicate, roles, priority, raw);

        this._id = _id;
    }

    /**
     * build acl permission from MongoDb document
     *
     * @param doc
     */
    static MongoAclPermission build(BsonDocument doc) throws ConfigurationException {
        BsonValue _id = doc.get("_id");

        var _roles = doc.get("roles");

        if (_roles == null || !_roles.isArray() || _roles.asArray().isEmpty()) {
            throw new ConfigurationException("Wrong permission: roles must be an not empty array of strings");
        }

        if (StreamSupport.stream(_roles.asArray().spliterator(), true).anyMatch(el -> el == null || !el.isString())) {
            throw new ConfigurationException("Wrong permission: roles must be an not empty array of strings");
        }

        var roles = StreamSupport.stream(_roles.asArray().spliterator(), true).map(role -> role.asString())
                .map(role -> role.getValue()).collect(Collectors.toSet());

        String predicate;
        var _predicate = doc.get("predicate");

        if (_predicate == null || !_predicate.isString()) {
            throw new ConfigurationException("Wrong permission: predicate must be a string");
        }

        try {
            // check predicate
            PredicateParser.parse(_predicate.asString().getValue(), MongoAclPermission.class.getClassLoader());
            predicate = _predicate.asString().getValue();
        } catch (Throwable t) {
            throw new ConfigurationException("Wrong permission: invalid predicate " + _predicate, t);
        }

        int priority;
        var _priority = doc.get("priority");

        if (_priority == null || _priority.isNull() || !_priority.isNumber()) {
            priority = Integer.MAX_VALUE; // very low priority

            LOGGER.warn("predicate {} doesn't have priority; setting it to very low priority", _id);
        } else {
            priority = _priority.asNumber().intValue();
        }

        return new MongoAclPermission(_id, predicate, roles, priority, doc);
    }

    /**
     * @return the _id
     */
    public BsonValue getId() {
        return _id;
    }

    @Override
    public boolean allow(final HttpServerExchange exchange) {
        if (getPredicate() == null) {
            return false;
        } else {
            return AclVarsInterpolator.interpolatePredicate(Request.of(exchange), getPredicate()).resolve(exchange);
        }
    }
}
