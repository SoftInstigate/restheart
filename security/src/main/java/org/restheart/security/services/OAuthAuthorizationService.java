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
package org.restheart.security.services;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.security.ACLRegistry;
import org.restheart.security.tokens.JwtConfigProvider;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * OAuth 2.1 Authorization Code + PKCE flow endpoint.
 *
 * <p>Implements the authorization endpoint required by MCP OAuth 2.1:
 * <ul>
 *   <li>{@code GET /authorize} — validates the request and redirects the user-agent to the
 *       configured {@code login-url}, forwarding all OAuth parameters as query string.</li>
 *   <li>{@code POST /authorize} — called by the login UI after the user authenticates
 *       (credentials must be in the {@code Authorization: Basic …} header). Verifies
 *       authentication, issues a short-lived authorization code, and redirects to
 *       {@code redirect_uri?code=…[&state=…]}.</li>
 * </ul>
 *
 * <h2>Stateless authorization codes</h2>
 * <p>The authorization code is a short-lived JWT signed with the shared
 * {@link JwtConfigProvider} key. This makes the flow fully stateless and compatible
 * with multi-instance deployments: any node that shares the same JWT signing key can
 * validate the code at the {@code POST /token} endpoint without requiring a shared store.
 *
 * <p>The code JWT carries the following claims:
 * <ul>
 *   <li>{@code sub} — username</li>
 *   <li>{@code roles} — array of roles</li>
 *   <li>{@code cc} — code_challenge (PKCE)</li>
 *   <li>{@code ccm} — code_challenge_method</li>
 *   <li>{@code ruri} — redirect_uri</li>
 *   <li>{@code cid} — client_id</li>
 *   <li>{@code exp} — expiry ({@value #CODE_TTL_MINUTES} minutes)</li>
 * </ul>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7636">RFC 7636 – PKCE</a>
 */
@RegisterPlugin(
    name = "oauthAuthorizationService",
    description = "OAuth 2.1 Authorization Code + PKCE endpoint (/authorize)",
    secure = false,
    enabledByDefault = false,
    defaultURI = "/authorize"
)
public class OAuthAuthorizationService implements ByteArrayService {

    static final int CODE_TTL_MINUTES = 5;

    // Claims used inside the authorization-code JWT
    static final String CLAIM_CODE_CHALLENGE        = "cc";
    static final String CLAIM_CODE_CHALLENGE_METHOD = "ccm";
    static final String CLAIM_REDIRECT_URI          = "ruri";
    static final String CLAIM_CLIENT_ID             = "cid";
    static final String CLAIM_ROLES                 = "roles";

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthAuthorizationService.class);

    @Inject("config")
    private Map<String, Object> config;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("jwtConfigProvider")
    private JwtConfigProvider.JwtConfig jwtConfig;

    private String loginUrl;
    private List<String> allowedRedirectUris;
    private Algorithm signingAlgo;

    @OnInit
    public void init() {
        this.loginUrl            = argOrDefault(config, "login-url", null);
        this.allowedRedirectUris = argOrDefault(config, "allowed-redirect-uris", List.of());
        this.signingAlgo         = buildAlgorithm(jwtConfig);

        // allow unauthenticated GET (redirect to login) and authenticated POST (issue code)
        aclRegistry.registerAllow(req -> "/authorize".equals(req.getPath()));
    }

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        switch (request.getMethod()) {
            case GET     -> handleGet(request, response);
            case POST    -> handlePost(request, response);
            case OPTIONS -> handleOptions(request);
            default      -> response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    // -------------------------------------------------------------------------
    // GET /authorize
    // -------------------------------------------------------------------------

    /**
     * Redirects the user-agent to the configured login UI, preserving all incoming
     * OAuth query parameters ({@code response_type}, {@code client_id},
     * {@code redirect_uri}, {@code state}, {@code code_challenge},
     * {@code code_challenge_method}).
     */
    private void handleGet(ByteArrayRequest request, ByteArrayResponse response) {
        if (loginUrl == null || loginUrl.isBlank()) {
            sendError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR,
                "server_error", "login-url is not configured");
            return;
        }

        var params = request.getExchange().getQueryParameters();

        if (!"code".equals(firstParam(params, "response_type"))) {
            sendError(response, HttpStatus.SC_BAD_REQUEST,
                "unsupported_response_type", "response_type must be 'code'");
            return;
        }

        var redirectUri = firstParam(params, "redirect_uri");
        if (redirectUri == null || redirectUri.isBlank()) {
            sendError(response, HttpStatus.SC_BAD_REQUEST,
                "invalid_request", "redirect_uri is required");
            return;
        }
        if (!isAllowedRedirectUri(redirectUri)) {
            sendError(response, HttpStatus.SC_BAD_REQUEST,
                "invalid_request", "redirect_uri is not in the allowed list");
            return;
        }

        var codeChallenge = firstParam(params, "code_challenge");
        if (codeChallenge == null || codeChallenge.isBlank()) {
            sendError(response, HttpStatus.SC_BAD_REQUEST,
                "invalid_request", "code_challenge is required (PKCE S256)");
            return;
        }
        if (!"S256".equals(firstParam(params, "code_challenge_method"))) {
            sendError(response, HttpStatus.SC_BAD_REQUEST,
                "invalid_request", "code_challenge_method must be 'S256'");
            return;
        }

        // redirect to login UI forwarding the original query string
        var queryString = request.getExchange().getQueryString();
        var separator   = loginUrl.contains("?") ? "&" : "?";
        var location    = (queryString != null && !queryString.isBlank())
            ? loginUrl + separator + queryString
            : loginUrl;

        response.getHeaders().put(Headers.LOCATION, location);
        response.setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);
    }

    // -------------------------------------------------------------------------
    // POST /authorize
    // -------------------------------------------------------------------------

    /**
     * Issues a signed authorization code for the authenticated user and redirects
     * to the {@code redirect_uri}.
     *
     * <p>Credentials must be provided via {@code Authorization: Basic …} (handled by
     * RESTHeart's authentication pipeline). OAuth parameters are read from the query string.
     */
    private void handlePost(ByteArrayRequest request, ByteArrayResponse response) {
        if (request.getAuthenticatedAccount() == null) {
            response.getHeaders().put(
                HttpString.tryFromString("WWW-Authenticate"),
                "Basic realm=\"RESTHeart\"");
            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            return;
        }

        var account = request.getAuthenticatedAccount();
        var params  = request.getExchange().getQueryParameters();

        var redirectUri         = firstParam(params, "redirect_uri");
        var codeChallenge       = firstParam(params, "code_challenge");
        var codeChallengeMethod = firstParam(params, "code_challenge_method");
        var state               = firstParam(params, "state");
        var clientId            = firstParam(params, "client_id");

        if (redirectUri == null || redirectUri.isBlank()) {
            sendError(response, HttpStatus.SC_BAD_REQUEST,
                "invalid_request", "redirect_uri is required");
            return;
        }
        if (!isAllowedRedirectUri(redirectUri)) {
            sendError(response, HttpStatus.SC_BAD_REQUEST,
                "invalid_request", "redirect_uri is not in the allowed list");
            return;
        }
        if (codeChallenge == null || codeChallenge.isBlank()) {
            redirectWithError(response, redirectUri, state,
                "invalid_request", "code_challenge is required");
            return;
        }
        if (!"S256".equals(codeChallengeMethod)) {
            redirectWithError(response, redirectUri, state,
                "invalid_request", "code_challenge_method must be 'S256'");
            return;
        }

        // Issue authorization code as a short-lived signed JWT.
        // Stateless: any node sharing the same JWT key can later verify it.
        var roles = account.getRoles().toArray(String[]::new);
        var code  = JWT.create()
            .withIssuer(jwtConfig.issuer())
            .withSubject(account.getPrincipal().getName())
            .withExpiresAt(Date.from(Instant.now().plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES)))
            .withArrayClaim(CLAIM_ROLES, roles)
            .withClaim(CLAIM_CODE_CHALLENGE,        codeChallenge)
            .withClaim(CLAIM_CODE_CHALLENGE_METHOD, codeChallengeMethod)
            .withClaim(CLAIM_REDIRECT_URI,          redirectUri)
            .withClaim(CLAIM_CLIENT_ID,             clientId)
            .sign(signingAlgo);

        var sb = new StringBuilder(redirectUri);
        sb.append(redirectUri.contains("?") ? "&" : "?");
        sb.append("code=").append(encode(code));
        if (state != null && !state.isBlank()) {
            sb.append("&state=").append(encode(state));
        }

        response.getHeaders().put(Headers.LOCATION, sb.toString());
        response.setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);

        LOGGER.debug("Authorization code issued for user '{}', client '{}'",
            account.getPrincipal().getName(), clientId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isAllowedRedirectUri(String uri) {
        for (var pattern : allowedRedirectUris) {
            if (matchesPattern(pattern, uri)) return true;
        }
        return false;
    }

    /**
     * Wildcard match: {@code *} in the pattern matches any sequence of characters.
     * Example: {@code http://localhost:*} matches {@code http://localhost:3000/callback}.
     */
    private boolean matchesPattern(String pattern, String uri) {
        if (!pattern.contains("*")) {
            return uri.equals(pattern)
                || uri.startsWith(pattern + "/")
                || uri.startsWith(pattern + "?");
        }
        var regex = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
        return Pattern.matches(regex, uri);
    }

    private void sendError(ByteArrayResponse response, int status, String error, String description) {
        response.setStatusCode(status);
        var body = "{\"error\":\"" + error + "\",\"error_description\":\"" + description + "\"}";
        response.setContent(body.getBytes(StandardCharsets.UTF_8));
        response.setContentTypeAsJson();
    }

    private void redirectWithError(ByteArrayResponse response, String redirectUri,
                                   String state, String error, String description) {
        var sb = new StringBuilder(redirectUri);
        sb.append(redirectUri.contains("?") ? "&" : "?");
        sb.append("error=").append(encode(error));
        sb.append("&error_description=").append(encode(description));
        if (state != null && !state.isBlank()) {
            sb.append("&state=").append(encode(state));
        }
        response.getHeaders().put(Headers.LOCATION, sb.toString());
        response.setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);
    }

    private String firstParam(Map<String, Deque<String>> params, String name) {
        var deque = params.get(name);
        return deque != null ? deque.peekFirst() : null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static Algorithm buildAlgorithm(JwtConfigProvider.JwtConfig cfg) {
        var key = cfg.key().getBytes(StandardCharsets.UTF_8);
        return switch (cfg.algorithm()) {
            case "HMAC256", "HS256" -> Algorithm.HMAC256(key);
            case "HMAC384", "HS384" -> Algorithm.HMAC384(key);
            case "HMAC512", "HS512" -> Algorithm.HMAC512(key);
            default -> throw new IllegalArgumentException(
                "Unsupported JWT algorithm for authorization code: " + cfg.algorithm());
        };
    }
}
