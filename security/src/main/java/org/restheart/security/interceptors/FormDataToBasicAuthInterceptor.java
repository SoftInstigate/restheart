/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.security.interceptors;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.exchange.UninitializedRequest;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.Headers;

/**
 * Converts OAuth 2.0 form data (application/x-www-form-urlencoded) to Basic Auth header
 * for /token endpoints. This allows RESTHeart to accept both HTTP Basic Auth and
 * OAuth 2.0 Resource Owner Password Credentials Grant format.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
    name = "formDataToBasicAuthInterceptor",
    description = "Converts OAuth 2.0 form data to Basic Auth header for /token endpoints",
    interceptPoint = InterceptPoint.REQUEST_BEFORE_EXCHANGE_INIT,
    enabledByDefault = true
)
public class FormDataToBasicAuthInterceptor implements WildcardInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(FormDataToBasicAuthInterceptor.class);

    private static final String TOKEN_ENDPOINT = "/token";
    private static final String TOKEN_COOKIE_ENDPOINT = "/token/cookie";
    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";
    private static final String GRANT_TYPE_PASSWORD = "password";
    private static final FormParserFactory FORM_PARSER = FormParserFactory.builder().build();

    @Inject("config")
    private Map<String, Object> config;

    private boolean enabled = true;

    @OnInit
    public void init() {
        this.enabled = argOrDefault(config, "enabled", true);
    }

    @Override
    public void handle(ServiceRequest<?> req, ServiceResponse<?> res) throws Exception {
        if (!enabled) {
            return;
        }

        // At REQUEST_BEFORE_EXCHANGE_INIT, request is UninitializedRequest
        if (!(req instanceof UninitializedRequest)) {
            return;
        }

        var uninitReq = (UninitializedRequest) req;
        var path = uninitReq.getPath();
        var contentType = uninitReq.getContentType();
        var hasAuthHeader = uninitReq.getHeaders().contains(Headers.AUTHORIZATION);

        // Only process /token or /token/cookie endpoints with form data
        if ((!TOKEN_ENDPOINT.equals(path) && !TOKEN_COOKIE_ENDPOINT.equals(path)) ||
            contentType == null ||
            !contentType.startsWith(FORM_CONTENT_TYPE)) {
            return;
        }

        // Only process POST requests
        if (!uninitReq.isPost()) {
            return;
        }

        // Set custom request initializer to parse form data
        // This is needed even if auth header exists, to properly consume the form data body
        uninitReq.setCustomRequestInitializer(e -> {
            try {
                // Parse form data using Undertow's FormParserFactory
                var parser = FORM_PARSER.createParser(e);
                
                if (parser == null) {
                    LOGGER.debug("Could not create form parser for {}", path);
                    throw new IllegalArgumentException("Invalid form data");
                }

                FormData formData = parser.parseBlocking();
                
                // Only convert to Basic Auth if no auth header exists
                if (!hasAuthHeader) {
                    // Extract form parameters
                    var grantType = getFormValue(formData, "grant_type");
                    var username = getFormValue(formData, "username");
                    var password = getFormValue(formData, "password");

                    // Validate OAuth 2.0 form data
                    if (grantType == null || !GRANT_TYPE_PASSWORD.equals(grantType)) {
                        LOGGER.debug("Invalid or missing grant_type for {}, expected 'password', got '{}'", 
                            path, grantType);
                        throw new IllegalArgumentException("Invalid grant_type. Must be 'password' for Resource Owner Password Credentials Grant");
                    }

                    if (username == null || username.isEmpty()) {
                        LOGGER.debug("Missing username parameter for {}", path);
                        throw new IllegalArgumentException("Missing required parameter: username");
                    }

                    if (password == null || password.isEmpty()) {
                        LOGGER.debug("Missing password parameter for {}", path);
                        throw new IllegalArgumentException("Missing required parameter: password");
                    }

                    // Convert to Basic Auth format
                    var credentials = username + ":" + password;
                    var encodedCredentials = Base64.getEncoder()
                        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                    var authHeader = "Basic " + encodedCredentials;

                    // Inject Authorization header
                    e.getRequestHeaders().put(Headers.AUTHORIZATION, authHeader);

                    LOGGER.debug("Converted OAuth 2.0 form data to Basic Auth for user '{}' on {}", 
                        username, path);
                } else {
                    LOGGER.debug("Auth header already present, form data parsed but not used for authentication on {}", path);
                }

                // Initialize the ByteArrayRequest after processing form data
                ByteArrayRequest.init(e);

            } catch (Exception ex) {
                LOGGER.error("Error processing form data for {}: {}", path, ex.getMessage());
                // Initialize request even on error
                ByteArrayRequest.init(e);
                // Set error status
                e.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            }
        });
    }

    /**
     * Get first value from FormData for a given field name
     */
    private String getFormValue(FormData formData, String fieldName) {
        var field = formData.getFirst(fieldName);
        if (field != null && !field.isFileItem()) {
            return field.getValue();
        }
        return null;
    }

    @Override
    public boolean resolve(ServiceRequest<?> req, ServiceResponse<?> res) {
        if (!enabled) {
            return false;
        }

        // At REQUEST_BEFORE_EXCHANGE_INIT, request is UninitializedRequest
        if (!(req instanceof UninitializedRequest)) {
            return false;
        }

        var uninitReq = (UninitializedRequest) req;
        var path = uninitReq.getPath();
        var contentType = uninitReq.getContentType();
        var hasAuthHeader = uninitReq.getHeaders().contains(Headers.AUTHORIZATION);

        // Only resolve for /token or /token/cookie endpoints with form data and no auth header
        return uninitReq.isPost() &&
               (TOKEN_ENDPOINT.equals(path) || TOKEN_COOKIE_ENDPOINT.equals(path)) &&
               contentType != null &&
               contentType.startsWith(FORM_CONTENT_TYPE) &&
               !hasAuthHeader;
    }
}
