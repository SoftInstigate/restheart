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

import com.google.gson.JsonElement;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.undertow.server.HttpServerExchange;
import org.restheart.security.ConfigurationException;
import org.restheart.security.handlers.exchange.JsonRequest;
import org.restheart.security.handlers.exchange.JsonResponse;
import org.restheart.security.plugins.ResponseInterceptor;
import org.restheart.security.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class UserPwdRemover implements ResponseInterceptor {
    static final Logger LOGGER = LoggerFactory.getLogger(UserPwdRemover.class);
     
    private final String usersUri;
    private final String propNamePassword;

    /**
     *
     * @param usersUri the URI of the users collection resource
     * @param propPassword the property holding the password
     * in the user document
     * @throws org.restheart.security.ConfigurationException
     */
    public UserPwdRemover(
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
        
        this.propNamePassword = propPassword;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var resp = JsonResponse.wrap(exchange);

        DocumentContext dc = JsonPath.parse(resp.readContent());

        JsonElement content = dc.json();
        
        if (content == null || content.isJsonNull()) {
            return;
        }
        
        if (content.isJsonArray()) {
            // GET collection as array of documents (rep=PJ + 'np' qparam)
            dc.delete("$.[*].".concat(this.propNamePassword));
        } else if (content.isJsonObject()
                && content.getAsJsonObject().keySet().contains("_embedded")) {
            if (content.getAsJsonObject().get("_embedded").isJsonArray()) {
                //  GET collection as a compact HAL document
                dc.delete("$._embedded.*.".concat(this.propNamePassword));
            } else if (content.getAsJsonObject().get("_embedded").isJsonObject()
                    && content.getAsJsonObject()
                            .get("_embedded").getAsJsonObject().keySet().contains("rh:doc")
                    && content.getAsJsonObject()
                            .get("_embedded").getAsJsonObject().get("rh:doc").isJsonArray()) {
                //  GET collection as a full HAL document
                dc.delete("$._embedded.['rh:doc'].*.".concat(this.propNamePassword));
            }

        } else if (content.isJsonObject()) {
            // GET document
            dc.delete("$.".concat(this.propNamePassword));
        }
        
        resp.writeContent(content);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var requestPath = URLUtils.removeTrailingSlashes(exchange.
                getRequestPath());

        return JsonRequest.wrap(exchange).isGet()
                && (requestPath.equals(usersUri)
                || requestPath.startsWith(usersUri + "/"));
    }

    @Override
    public boolean requiresResponseContent() {
        return true;
    }
}
