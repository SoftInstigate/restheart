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
package com.restheart.security.predicates;

import com.google.gson.JsonObject;
import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import org.restheart.security.ConfigurationException;
import org.restheart.security.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * DenyFilterOnUserPasswordPredicate checks if a request has a filter involving
 * thepassword field
 *
 */
public class DenyFilterOnUserPwdPredicate implements Predicate {
    static final Logger LOGGER = LoggerFactory.getLogger(DenyFilterOnUserPwdPredicate.class);

    private final String usersUri;
    private final String propPassword;

    /**
     *
     * @param usersUri the URI of the users collection resource
     * @param propPassword the property holding the password
     * in the user document
     * @throws org.restheart.security.ConfigurationException
     */
    public DenyFilterOnUserPwdPredicate (
            String usersUri,
            String propPassword) throws ConfigurationException {
        if (usersUri == null) {
            throw new ConfigurationException(
                    "missing users-collection-uri property");
        }
        
        this.usersUri = URLUtils.removeTrailingSlashes(usersUri);
        if (propPassword == null ||
                propPassword.contains(".")) {
            throw new ConfigurationException("prop-password must be "
                    + "a root level property and cannot contain the char '.'");
        }
        
        this.propPassword = propPassword;
    }

    @Override
    final public boolean resolve(HttpServerExchange exchange) {
        var requestPath = URLUtils.removeTrailingSlashes(exchange.getRequestPath());

        // return false to deny the request
        if (requestPath.equals(usersUri)
                || requestPath.startsWith(usersUri + "/")) {

            try {
                JsonObject filters = RHRequest.wrap(exchange).getFiltersAsJson();
                return !hasFilterOnPassword(filters);
            } catch (BadRequestException bre) {
                return true;
            }
        }

        return true;
    }

    private boolean hasFilterOnPassword(JsonObject filters) {
        if (filters == null || filters.keySet().isEmpty()) {
            return false;
        } else {
            return filters.keySet().contains(propPassword)
                    || filters
                            .keySet().stream()
                            .filter(key -> filters.get(key).isJsonObject())
                            .map(key -> filters.get(key).getAsJsonObject())
                            .anyMatch(doc
                                    -> hasFilterOnPassword(doc));
        }
    }
}
