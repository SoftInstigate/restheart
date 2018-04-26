/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.security.impl;

import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.restheart.handlers.RequestContext;
import org.restheart.security.RequestContextPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * DenyFilterOnUserPasswordPredicate checks if a request has a filter involving
 * thepassword field
 *
 */
public class DenyFilterOnUserPasswordPredicate implements RequestContextPredicate {
    static final Logger LOGGER = LoggerFactory.getLogger(
            DenyFilterOnUserPasswordPredicate.class);

    private final String db;
    private final String coll;
    private final String propNamePassword;

    public DenyFilterOnUserPasswordPredicate(
            String db,
            String coll,
            String propNamePassword) {
        this.db = db;
        this.coll = coll;
        this.propNamePassword = propNamePassword;
    }

    public DenyFilterOnUserPasswordPredicate(
            String db,
            String propNamePassword) {
        this.db = db;
        this.coll = null;
        this.propNamePassword = propNamePassword;
    }

    public DenyFilterOnUserPasswordPredicate(
            String propNamePassword) {
        this.db = null;
        this.coll = null;
        this.propNamePassword = propNamePassword;
    }

    @Override
    public boolean resolve(HttpServerExchange hse, RequestContext context) {
        if (doesAppy(context) && hasFilterOnPassword(context.getFiltersDocument())) {
            context.addWarning(
                    "cannot execute request with filter on password property");
            return false;
        }

        return true;
    }

    private boolean hasFilterOnPassword(BsonDocument filters) {
        if (filters == null || filters.isEmpty()) {
            return false;
        } else {
            if (filters.isDocument()) {
                return filters.keySet().contains(propNamePassword)
                        || filters
                                .keySet().stream()
                                .filter(key -> filters.get(key).isDocument())
                                .map(key -> filters.get(key).asDocument())
                                .anyMatch(doc
                                        -> hasFilterOnPassword(doc));
            } else {
                return true;
            }
        }
    }

    public boolean doesAppy(RequestContext context) {
        if (context == null) {
            return false;
        } else {
            return context.isGet()
                    && this.db == null
                            ? true
                            : this.db.equals(context.getDBName())
                            && this.coll == null
                                    ? true
                                    : this.coll.equals(context.getCollectionName());
        }
    }
}
