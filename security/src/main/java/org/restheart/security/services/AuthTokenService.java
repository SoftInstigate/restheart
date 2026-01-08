/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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
package org.restheart.security.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.security.BaseAccount;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.plugins.security.TokenManager.AUTH_TOKEN_VALID_HEADER;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OAuth 2.0-inspired token endpoint for authentication.
 * Supports both /token (returns token in response) and /token/cookie (sets cookie).
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "authTokenService",
        description = "OAuth 2.0-inspired token endpoint for authentication",
        secure = true,
        enabledByDefault = true,
        defaultURI = "/token"
)
public class AuthTokenService implements ByteArrayService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthTokenService.class);

    private static final String TOKEN_ENDPOINT = "/token";
    private static final String TOKEN_COOKIE_ENDPOINT = "/token/cookie";
    private static final String ISO8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    @Inject("registry")
    private PluginsRegistry registry;

    @Inject("config")
    private Map<String, Object> config;

    @Override
    public Consumer<HttpServerExchange> requestInitializer() {
        return e -> ByteArrayRequest.init(e);
    }

    @Override
    public Consumer<HttpServerExchange> responseInitializer() {
        return e -> ByteArrayResponse.init(e);
    }

    @Override
    public Function<HttpServerExchange, ByteArrayRequest> request() {
        return e -> ByteArrayRequest.of(e);
    }

    @Override
    public Function<HttpServerExchange, ByteArrayResponse> response() {
        return e -> ByteArrayResponse.of(e);
    }

    /**
     * @param request ByteArrayRequest
     * @param response ByteArrayResponse
     * @throws Exception in case of any error
     */
    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        var exchange = request.getExchange();
        var path = request.getPath();

        // Handle OPTIONS for CORS
        if (Methods.OPTIONS.equals(exchange.getRequestMethod())) {
            handleOptions(response);
            return;
        }

        // Verify authentication
        if (request.getAuthenticatedAccount() == null || request.getAuthenticatedAccount().getPrincipal() == null) {
            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            return;
        }

        var isCookieEndpoint = TOKEN_COOKIE_ENDPOINT.equals(path);
        // Also accept /token/{id} paths for GET/DELETE (token resource location)
        var isTokenResource = path.startsWith(TOKEN_ENDPOINT + "/") && !path.startsWith(TOKEN_COOKIE_ENDPOINT);

        if (Methods.POST.equals(exchange.getRequestMethod())) {
            handlePost(request, response, isCookieEndpoint);
        } else if (Methods.GET.equals(exchange.getRequestMethod())) {
            if (isCookieEndpoint) {
                // GET not supported on /token/cookie
                response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            } else {
                handleGet(request, response);
            }
        } else if (Methods.DELETE.equals(exchange.getRequestMethod())) {
            if (isCookieEndpoint) {
                // DELETE not supported on /token/cookie (use AuthCookieRemover instead)
                response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            } else {
                handleDelete(request, response);
            }
        } else {
            response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    /**
     * Handle OPTIONS request for CORS
     */
    private void handleOptions(ByteArrayResponse response) {
        response.getHeaders().put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, POST, DELETE")
                .put(HttpString.tryFromString("Access-Control-Allow-Headers"),
                    "Accept, Accept-Encoding, Authorization, Content-Length, "
                    + "Content-Type, Host, Origin, X-Requested-With, "
                    + "User-Agent, No-Auth-Challenge, Cookie");
        response.setStatusCode(HttpStatus.SC_OK);
    }

    /**
     * Handle POST request - issue new token
     */
    private void handlePost(ByteArrayRequest request, ByteArrayResponse response, boolean isCookieEndpoint) throws Exception {
        var account = request.getAuthenticatedAccount();
        var authTokenHeader = response.getHeader(AUTH_TOKEN_HEADER);
        var authTokenValidHeader = response.getHeader(AUTH_TOKEN_VALID_HEADER);

        if (authTokenHeader == null) {
            LOGGER.error("Auth-Token header not found. Ensure TokenInjector is configured correctly.");
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        var resp = new JsonObject();
        var tokenType = getTokenType();

        if (isCookieEndpoint) {
            // Cookie endpoint: NO access_token in body (security - prevent JavaScript access)
            resp.add("authenticated", new JsonPrimitive(true));
            resp.add("username", new JsonPrimitive(account.getPrincipal().getName()));
            resp.add("roles", rolesAsJsonArray(account));
            
            LOGGER.debug("Token issued via cookie for user '{}'", account.getPrincipal().getName());
        } else {
            // Token endpoint: Include access_token in body (OAuth 2.0 format)
            resp.add("access_token", new JsonPrimitive(authTokenHeader));
            resp.add("token_type", new JsonPrimitive(tokenType));
            
            var expiresIn = calculateExpiresIn(authTokenValidHeader);
            if (expiresIn != null) {
                resp.add("expires_in", new JsonPrimitive(expiresIn));
            }
            
            resp.add("username", new JsonPrimitive(account.getPrincipal().getName()));
            resp.add("roles", rolesAsJsonArray(account));
            
            LOGGER.debug("Token issued for user '{}', type: {}, expires_in: {}s", 
                account.getPrincipal().getName(), tokenType, expiresIn);
        }

        // Set Cache-Control: no-store (OAuth 2.0 security requirement)
        response.getHeaders().put(HttpString.tryFromString("Cache-Control"), "no-store");
        response.getHeaders().put(HttpString.tryFromString("Pragma"), "no-cache");
        response.setContentTypeAsJson();

        response.setContent(resp.toString());
        response.setStatusCode(HttpStatus.SC_OK);
    }

    /**
     * Handle GET request - return current token info
     */
    private void handleGet(ByteArrayRequest request, ByteArrayResponse response) {
        var account = request.getAuthenticatedAccount();
        var authTokenHeader = response.getHeader(AUTH_TOKEN_HEADER);
        var authTokenValidHeader = response.getHeader(AUTH_TOKEN_VALID_HEADER);

        if (authTokenHeader == null) {
            LOGGER.error("Auth-Token header not found for GET request");
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        var resp = new JsonObject();
        var tokenType = getTokenType();
        
        // OAuth 2.0 format
        resp.add("access_token", new JsonPrimitive(authTokenHeader));
        resp.add("token_type", new JsonPrimitive(tokenType));
        
        var expiresIn = calculateExpiresIn(authTokenValidHeader);
        if (expiresIn != null) {
            resp.add("expires_in", new JsonPrimitive(expiresIn));
        }
        
        resp.add("username", new JsonPrimitive(account.getPrincipal().getName()));
        resp.add("roles", rolesAsJsonArray(account));

        response.setContentTypeAsJson();
        response.setContent(resp.toString());
        response.setStatusCode(HttpStatus.SC_OK);
    }

    /**
     * Handle DELETE request - invalidate token
     */
    private void handleDelete(ByteArrayRequest request, ByteArrayResponse response) {
        var account = new BaseAccount(
            request.getAuthenticatedAccount().getPrincipal().getName(), 
            null
        );

        invalidate(account);
        removeAuthTokens(request.getExchange());
        
        LOGGER.debug("Token invalidated for user '{}'", account.getPrincipal().getName());
        response.setStatusCode(HttpStatus.SC_NO_CONTENT);
    }

    /**
     * Get the token type based on the active TokenManager
     * Returns "Bearer" for JWT tokens, "Basic-Password" for RND tokens
     */
    private String getTokenType() {
        var tokenManager = this.registry.getTokenManager();
        
        if (tokenManager != null) {
            var tokenManagerName = tokenManager.getName();
            
            // JWT tokens are Bearer tokens
            if ("jwtTokenManager".equals(tokenManagerName)) {
                return "Bearer";
            }
            
            // RND tokens must be used as password in Basic Auth
            if ("rndTokenManager".equals(tokenManagerName)) {
                return "Basic-Password";
            }
        }
        
        // Default to Bearer for unknown token managers
        return "Bearer";
    }

    /**
     * Calculate seconds until token expiration
     */
    private Integer calculateExpiresIn(String authTokenValidHeader) {
        if (authTokenValidHeader == null || authTokenValidHeader.isEmpty()) {
            return null;
        }

        try {
            var dateFormat = new SimpleDateFormat(ISO8601_PATTERN);
            var expirationDate = dateFormat.parse(authTokenValidHeader);
            var now = new Date();
            var expiresInMillis = expirationDate.getTime() - now.getTime();
            var expiresInSeconds = (int) (expiresInMillis / 1000);
            
            // Return 0 if already expired, otherwise return remaining seconds
            return Math.max(0, expiresInSeconds);
        } catch (ParseException ex) {
            LOGGER.warn("Failed to parse Auth-Token-Valid-Until header: {}", authTokenValidHeader, ex);
            return null;
        }
    }

    /**
     * Convert account roles to JSON array
     */
    private JsonArray rolesAsJsonArray(Account account) {
        var roles = new JsonArray();
        if (account.getRoles() != null) {
            account.getRoles().forEach(role -> roles.add(new JsonPrimitive(role)));
        }
        return roles;
    }

    /**
     * Invalidate token using TokenManager
     */
    private void invalidate(Account account) {
        var tokenManager = this.registry.getTokenManager();

        if (tokenManager == null) {
            throw new IllegalStateException("Error, cannot invalidate, token manager not active");
        }

        tokenManager.getInstance().invalidate(account);
    }

    /**
     * Remove auth token headers from response
     */
    private void removeAuthTokens(HttpServerExchange exchange) {
        exchange.getResponseHeaders().remove(AUTH_TOKEN_HEADER);
        exchange.getResponseHeaders().remove(AUTH_TOKEN_VALID_HEADER);
        exchange.getResponseHeaders().remove(AUTH_TOKEN_LOCATION_HEADER);
    }

    /**
     * Get configured URI or default to /token
     */
    private String getUri() {
        if (config == null) {
            return TOKEN_ENDPOINT;
        }

        try {
            return arg(config, "uri");
        } catch (ConfigurationException ex) {
            return TOKEN_ENDPOINT;
        }
    }
}
