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
package com.restheart.security.plugins.interceptors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.undertow.server.HttpServerExchange;
import org.mindrot.jbcrypt.BCrypt;
import org.restheart.security.ConfigurationException;
import org.restheart.security.handlers.exchange.JsonRequest;
import org.restheart.security.plugins.RequestInterceptor;
import org.restheart.security.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class UserPwdHasher implements RequestInterceptor {

    static final Logger LOGGER = LoggerFactory.getLogger(UserPwdHasher.class);

    private final String usersUri;
    private final String propNamePassword;
    private final Integer complexity;

    /**
     *
     * @param usersUri the URI of the users collection resource
     * @param propPassword the property holding the password in the user
     * document
     */
    public UserPwdHasher(
            String usersUri,
            String propPassword,
            int complexity) throws ConfigurationException {

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

        this.propNamePassword = propPassword;

        this.complexity = complexity;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = JsonRequest.wrap(exchange);

        var content = request.readContent();

        if (content.isJsonArray() && JsonRequest.wrap(exchange).isPost()) {
            // POST collection with array of documents
            JsonArray passwords = JsonPath.read(content,
                    "$.[*].".concat(this.propNamePassword));

            int[] iarr = {0};

            passwords.forEach(plain -> {
                if (plain != null && plain.isJsonPrimitive()
                        && plain.getAsJsonPrimitive().isString()) {
                    String hashed = BCrypt.hashpw(
                            plain.getAsJsonPrimitive().getAsString(),
                            BCrypt.gensalt(complexity));

                    content.getAsJsonArray().get(iarr[0]).getAsJsonObject()
                            .addProperty(this.propNamePassword, hashed);
                }

                iarr[0]++;
            });
        } else if (content.isJsonObject()) {
            // PUT/PATCH document or bulk PATCH
            JsonElement plain;
            try {
                plain = JsonPath.read(content,
                        "$.".concat(this.propNamePassword));

                if (plain != null && plain.isJsonPrimitive()
                        && plain.getAsJsonPrimitive().isString()) {
                    String hashed = BCrypt.hashpw(
                            plain.getAsJsonPrimitive().getAsString(),
                            BCrypt.gensalt(complexity));

                    content.getAsJsonObject()
                            .addProperty(this.propNamePassword, hashed);
                }
            } catch (PathNotFoundException pnfe) {
                return;
            }
        }

        request.writeContent(content);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange
    ) {
        var requestPath = URLUtils.removeTrailingSlashes(exchange.
                getRequestPath());

        var request = JsonRequest.wrap(exchange);

        return (request.isPost()
                && request.isContentTypeJson()
                && (requestPath.equals(usersUri)
                || requestPath.equals(usersUri.concat("/"))))
                || ((request.isPatch() || request.isPut())
                && (requestPath.startsWith(usersUri.concat("/"))
                && requestPath.length() > usersUri.concat("/").length()));
    }

    @Override
    public boolean requiresContent() {
        return true;
    }
}
