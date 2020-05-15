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
package com.restheart.authenticators;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;
import org.bson.BsonDocument;
import org.restheart.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * DenyFilterOnUserPasswordPredicate checks if a request has a filter involving
 * the password field
 *
 */
public class DenyFilterOnUserPwdPredicate implements Predicate {
    static final Logger LOGGER = LoggerFactory.getLogger(DenyFilterOnUserPwdPredicate.class);

    private final String usersUri;
    private final String propPassword;

    /**
     *
     * @param usersUri the URI of the users collection resource
     * @param propPassword the property holding the password in the user
     * document
     * @throws org.restheart.ConfigurationException
     */
    public DenyFilterOnUserPwdPredicate(
            String usersUri,
            String propPassword) throws ConfigurationException {
        if (usersUri == null) {
            throw new ConfigurationException(
                    "missing users-collection-uri property");
        }

        this.usersUri = URLUtils.removeTrailingSlashes(usersUri);
        if (propPassword == null
                || propPassword.contains(".")) {
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
                return !hasFilterOnPassword(MongoRequest.of(exchange)
                        .getFiltersDocument());
        }

        return true;
    }

    private boolean hasFilterOnPassword(BsonDocument filters) {
        if (filters == null || filters.keySet().isEmpty()) {
            return false;
        } else {
            return filters.keySet().contains(propPassword)
                    || filters
                            .keySet().stream()
                            .filter(key -> filters.get(key).isDocument())
                            .map(key -> filters.get(key).asDocument())
                            .anyMatch(doc
                                    -> hasFilterOnPassword(doc));
        }
    }
}
