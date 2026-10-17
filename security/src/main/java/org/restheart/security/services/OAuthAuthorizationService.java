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
import java.util.Optional;
import java.util.regex.Pattern;

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.Request;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.accounts.OAuthProviderRegistry;
import org.restheart.plugins.security.OAuthTokenIssuer;
import org.restheart.security.ACLRegistry;
import org.restheart.security.WithProperties;
import org.restheart.security.interceptors.FormDataToBasicAuthInterceptor;
import org.restheart.security.tokens.JwtConfigProvider;
import org.restheart.security.tokens.DefaultJwtIssuer;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
 *   <li>{@code GET /authorize/offer} — called by the login UI with the credentials in
 *       {@code Authorization: Basic …}, before it submits them, to learn what the registered
 *       {@link OAuthTokenIssuer} lets this account choose: {@code 204} nothing, submit at once;
 *       {@code 200} an offer to render as a second step; {@code 403} a refusal, stop there.
 *       Wrong credentials are a {@code 401}, which is what makes it the page's first step.</li>
 * </ul>
 *
 * <h2>Which token the code stands for</h2>
 * <p>By itself the code stands for the account's own JWT. When an {@link OAuthTokenIssuer} is
 * registered, {@code POST /authorize} passes it the user's choice — the {@code choice.<name>}
 * query parameters, preselected by {@code scope} — and seals what it returns in the code, under
 * the claim {@value OAuthTokenIssuers#CODE_CLAIM}. From then on that code is the issuer's:
 * {@code POST /token} asks it for the token and never falls back to the JWT.
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
 *   <li>{@code iss} — issuer (from jwtConfigProvider)</li>
 *   <li>{@code aud} — audience (from jwtConfigProvider, when configured)</li>
 *   <li>{@code jti} — unique JWT identifier</li>
 *   <li>{@code iat} — issued-at timestamp</li>
 *   <li>{@code exp} — expiry ({@value #CODE_TTL_MINUTES} minutes)</li>
 *   <li>{@code roles} — array of roles</li>
 *   <li>{@code cc} — code_challenge (PKCE)</li>
 *   <li>{@code ccm} — code_challenge_method</li>
 *   <li>{@code ruri} — redirect_uri</li>
 *   <li>{@code cid} — client_id</li>
 *   <li>account-properties-claims (from {@link DefaultJwtIssuer})</li>
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
    static final String CLAIM_CODE_CHALLENGE = "cc";
    static final String CLAIM_CODE_CHALLENGE_METHOD = "ccm";
    static final String CLAIM_REDIRECT_URI = "ruri";
    static final String CLAIM_CLIENT_ID = "cid";
    static final String CLAIM_ROLES = "roles";

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuthAuthorizationService.class);

    @Inject("config")
    private Map<String, Object> config;

    @Inject("acl-registry")
    private ACLRegistry aclRegistry;

    @Inject("jwtConfigProvider")
    private JwtConfigProvider.JwtConfig jwtConfig;

    @Inject("registry")
    private PluginsRegistry registry;

    private String loginUrl;
    private List<String> allowedRedirectUris;

    /**
     * Per-request override of {@code login-url}, for an instance serving more than one tenant.
     *
     * <p>The configured value is one page for the whole node — the right shape for a single
     * deployment, the wrong one where each tenant has its own sign-in page with its own brand on
     * it. An interceptor that knows which tenant a request belongs to attaches this; the configured
     * value stays the fallback.
     */
    public static final String OVERRIDE_LOGIN_URL = "override-oauth-login-url";

    /**
     * Per-request override of {@code allowed-redirect-uris}, same reasoning: one tenant's clients
     * are not another's. Attached as a {@code List<String>}.
     */
    public static final String OVERRIDE_ALLOWED_REDIRECT_URIS = "override-oauth-allowed-redirect-uris";
    private volatile DefaultJwtIssuer jwtIssuer;

    static final String AUTHORIZE_URI = "/authorize";
    static final String OFFER_URI = "/authorize/offer";
    static final String PROVIDERS_URI = "/authorize/providers";

    /** The registered {@link OAuthTokenIssuer}, resolved on first use; {@code null} until then. */
    private volatile Optional<OAuthTokenIssuer> tokenIssuer;

    @OnInit
    public void init() {
        this.loginUrl = argOrDefault(config, "login-url", null);
        this.allowedRedirectUris = argOrDefault(config, "allowed-redirect-uris", List.of());

        // allow unauthenticated GET (redirect to login) and authenticated POST (issue code);
        // /authorize/offer answers 401 itself when no credentials come with it
        aclRegistry.registerAllow(req -> AUTHORIZE_URI.equals(req.getPath()) || OFFER_URI.equals(req.getPath())
                || PROVIDERS_URI.equals(req.getPath()));
    }

    /**
     * The deployment's {@link OAuthTokenIssuer}, or empty. Providers may initialise after this
     * service does, so the lookup waits for the first request that needs it.
     */
    private Optional<OAuthTokenIssuer> tokenIssuer() {
        var local = this.tokenIssuer;

        if (local == null) {
            synchronized (this) {
                local = this.tokenIssuer;
                if (local == null) {
                    local = OAuthTokenIssuers.discover(registry, this);
                    local.ifPresent(i -> LOGGER.info("Authorization Code flow issues tokens through '{}'", i.getClass().getName()));
                    this.tokenIssuer = local;
                }
            }
        }

        return local;
    }

    /**
     * The shared JWT issuance policy. Built lazily: resolving the password property name
     * needs {@code mongoRealmAuthenticator}, which may not be initialized when this plugin's
     * {@code @OnInit} runs.
     */
    private DefaultJwtIssuer issuer() {
        var local = this.jwtIssuer;

        if (local == null) {
            synchronized (this) {
                local = this.jwtIssuer;
                if (local == null) {
                    var algo = buildAlgorithm(jwtConfig);
                    local = new DefaultJwtIssuer(algo, jwtConfig.issuer(), jwtConfig.audience(),
                            jwtConfig.accountPropertiesClaims(),
                            jwtConfig.requiredAccountPropertiesClaims(),
                            DefaultJwtIssuer.resolvePasswordProperty(registry));
                    this.jwtIssuer = local;
                }
            }
        }

        return local;
    }

    @Override
    public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
        if (PROVIDERS_URI.equals(request.getPath())) {
            switch (request.getMethod()) {
                case GET -> handleProviders(request, response);
                case OPTIONS -> handleOptions(request);
                default -> response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
            return;
        }

        if (OFFER_URI.equals(request.getPath())) {
            switch (request.getMethod()) {
                case GET -> handleOffer(request, response);
                case OPTIONS -> handleOptions(request);
                default -> response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            }
            return;
        }

        switch (request.getMethod()) {
            case GET -> handleGet(request, response);
            case POST -> handlePost(request, response);
            case OPTIONS -> handleOptions(request);
            default -> response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    // -------------------------------------------------------------------------
    // GET /authorize/providers
    // -------------------------------------------------------------------------

    /**
     * Which social providers this deployment can sign the person in with, for the sign-in page to
     * offer as buttons: {@code {"providers":["google"]}}, and {@code []} when there are none or
     * when nothing implements {@link OAuthProviderRegistry} on this instance.
     *
     * <p>Unauthenticated, like {@code GET /authorize}: it is asked before anyone has signed in, and
     * it says nothing a person could not learn by looking at the page they were sent to. The
     * answer is per request because a deployment configures providers per tenant.
     */
    private void handleProviders(ByteArrayRequest request, ByteArrayResponse response) {
        var providers = providerRegistry()
                .map(registry -> registry.availableProviders(request))
                .orElseGet(List::of);

        var array = new StringBuilder();
        providers.forEach(name -> array.append(array.isEmpty() ? "" : ",").append('"').append(name.replace("\"", "")).append('"'));

        response.setContent(("{\"providers\":[" + array + "]}").getBytes(StandardCharsets.UTF_8));
        response.setContentTypeAsJson();
        response.setStatusCode(HttpStatus.SC_OK);
    }

    /**
     * The registry of social providers, found by type among the registered providers exactly as
     * the token issuer is: nothing has to agree on a name, and an instance without the accounts
     * module simply has none. Resolved on every call rather than cached, because it is asked once
     * per page load and a cached empty answer would outlive a module initialising after this one.
     */
    private Optional<OAuthProviderRegistry> providerRegistry() {
        for (var record : registry.getProviders()) {
            var instance = record.getInstance();

            if (OAuthProviderRegistry.class.isAssignableFrom(instance.rawType())
                    && instance.get(null) instanceof OAuthProviderRegistry found) {
                return Optional.of(found);
            }
        }

        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // GET /authorize/offer
    // -------------------------------------------------------------------------

    /**
     * What the sign-in page may offer the account whose credentials it is about to submit.
     *
     * <p>Three answers, and the page acts on each: {@code 204}, no issuer or nothing to choose,
     * submit at once; {@code 200}, an offer to render; {@code 403}, a refusal to show and stop
     * at. Unauthenticated is {@code 401}, so the page learns about a wrong password here rather
     * than after a redirect.
     */
    private void handleOffer(ByteArrayRequest request, ByteArrayResponse response) {
        var account = request.getAuthenticatedAccount();

        if (account == null) {
            if (!request.getExchange().getRequestHeaders().contains("No-Auth-Challenge")
                    && !request.getExchange().getQueryParameters().containsKey("noauthchallenge")) {
                response.getHeaders().put(
                        HttpString.tryFromString("WWW-Authenticate"),
                        "Basic realm=\"RESTHeart\"");
            }
            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            return;
        }

        response.getHeaders().put(HttpString.tryFromString("Cache-Control"), "no-store");

        var issuer = tokenIssuer();

        if (issuer.isEmpty()) {
            response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            return;
        }

        Optional<OAuthTokenIssuer.Offer> offer;

        try {
            offer = issuer.get().offer(account, request);
        } catch (OAuthTokenIssuer.Refusal r) {
            LOGGER.debug("Token issuer refused an offer to '{}': {} - {}", account.getPrincipal().getName(), r.error(), r.description());
            sendError(response, HttpStatus.SC_FORBIDDEN, r.error(), r.description());
            return;
        }

        if (offer == null || offer.isEmpty()) {
            response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            return;
        }

        response.setContent(toJson(offer.get()).toString().getBytes(StandardCharsets.UTF_8));
        response.setContentTypeAsJson();
        response.setStatusCode(HttpStatus.SC_OK);
    }

    /** The offer as the page reads it. Names are the JSON the page's script expects. */
    static JsonObject toJson(OAuthTokenIssuer.Offer offer) {
        var json = new JsonObject();

        if (offer.title() != null) {
            json.addProperty("title", offer.title());
        }

        if (offer.message() != null) {
            json.addProperty("message", offer.message());
        }

        var choices = new JsonArray();

        for (var choice : offer.choices()) {
            var c = new JsonObject();
            c.addProperty("name", choice.name());
            c.addProperty("label", choice.label());
            c.addProperty("type", choice.type().name().toLowerCase());

            if (choice.type() == OAuthTokenIssuer.Type.SELECT) {
                var options = new JsonArray();

                for (var option : choice.options()) {
                    var o = new JsonObject();
                    o.addProperty("value", option.value());
                    o.addProperty("label", option.label() != null ? option.label() : option.value());
                    options.add(o);
                }

                c.add("options", options);
            }

            if (choice.min() != null) {
                c.addProperty("min", choice.min());
            }

            if (choice.max() != null) {
                c.addProperty("max", choice.max());
            }

            if (choice.value() != null) {
                c.addProperty("value", choice.value());
            }

            choices.add(c);
        }

        json.add("choices", choices);

        return json;
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
        var loginUrl = loginUrl(request);

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
        if (!isAllowedRedirectUri(redirectUri, request)) {
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
        var separator = loginUrl.contains("?") ? "&" : "?";
        var location = (queryString != null && !queryString.isBlank())
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
            // If credentials came from form body (browser login UI), redirect back to login with error
            var fromForm = request.getExchange()
                    .getAttachment(FormDataToBasicAuthInterceptor.FORM_CREDENTIALS_FOR_AUTHORIZE);
            var loginUrl = loginUrl(request);
            if (Boolean.TRUE.equals(fromForm) && loginUrl != null && !loginUrl.isBlank()) {
                var queryString = request.getExchange().getQueryString();
                var separator = loginUrl.contains("?") ? "&" : "?";
                var sb = new StringBuilder(loginUrl).append(separator).append("error=invalid_credentials");
                if (queryString != null && !queryString.isBlank()) {
                    sb.append("&").append(queryString);
                }
                response.getHeaders().put(Headers.LOCATION, sb.toString());
                response.setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);
                return;
            }
            if (!request.getExchange().getRequestHeaders().contains("No-Auth-Challenge")
                    && !request.getExchange().getQueryParameters().containsKey("noauthchallenge")) {
                response.getHeaders().put(
                        HttpString.tryFromString("WWW-Authenticate"),
                        "Basic realm=\"RESTHeart\"");
            }
            response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            return;
        }

        var account = request.getAuthenticatedAccount();
        var params = request.getExchange().getQueryParameters();

        var redirectUri = firstParam(params, "redirect_uri");
        var codeChallenge = firstParam(params, "code_challenge");
        var codeChallengeMethod = firstParam(params, "code_challenge_method");
        var state = firstParam(params, "state");
        var clientId = firstParam(params, "client_id");

        if (redirectUri == null || redirectUri.isBlank()) {
            sendError(response, HttpStatus.SC_BAD_REQUEST,
                    "invalid_request", "redirect_uri is required");
            return;
        }
        if (!isAllowedRedirectUri(redirectUri, request)) {
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

        // What the registered issuer seals in the code, if there is one and it has something to
        // say: from here on the code is the issuer's, and /token will not answer it with the JWT.
        Map<String, Object> sealed = Map.of();

        var tokenIssuer = tokenIssuer();

        if (tokenIssuer.isPresent()) {
            var choice = OAuthTokenIssuers.choiceOf(params);

            try {
                var claims = tokenIssuer.get().claims(account, request, choice);
                sealed = claims == null ? Map.of() : claims;
            } catch (OAuthTokenIssuer.Refusal r) {
                LOGGER.debug("Token issuer refused the choice {} of '{}': {} - {}", choice,
                        account.getPrincipal().getName(), r.error(), r.description());
                refuse(request, response, redirectUri, state, r);
                return;
            }
        }

        // Issue authorization code as a short-lived signed JWT.
        // Stateless: any node sharing the same JWT key can later verify it.
        var jwtIssuer = issuer();
        var codeBuilder = jwtIssuer.newBuilder(
                account.getPrincipal().getName(),
                account.getRoles(),
                Date.from(Instant.now().plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES)))
                .withIssuedAt(Instant.now())
                .withClaim(CLAIM_CODE_CHALLENGE, codeChallenge)
                .withClaim(CLAIM_CODE_CHALLENGE_METHOD, codeChallengeMethod)
                .withClaim(CLAIM_REDIRECT_URI, redirectUri)
                .withClaim(CLAIM_CLIENT_ID, clientId);

        // Propagate account-properties-claims so the access token (later built from this
        // code's payload) carries the same claims. The request carries the effective claim
        // list on a multi-tenant deployment.
        if (account instanceof WithProperties<?> awp) {
            codeBuilder = jwtIssuer.applyAccountClaims(codeBuilder, awp.propertiesAsMap(),
                    DefaultJwtIssuer.claimsOverride(request));
        }

        if (!sealed.isEmpty()) {
            // Signed, not encrypted, and about to travel in a redirect URL: the issuer was told
            // never to put a secret here.
            codeBuilder = codeBuilder.withClaim(OAuthTokenIssuers.CODE_CLAIM, sealed);
        }

        var code = jwtIssuer.sign(codeBuilder);

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

    private boolean isAllowedRedirectUri(String uri, Request<?> request) {
        for (var pattern : allowedRedirectUris(request)) {
            if (matchesPattern(pattern, uri)) return true;
        }
        return false;
    }

    /** The tenant's sign-in page when one was attached, the configured one otherwise. */
    private String loginUrl(Request<?> request) {
        return request != null && request.attachedParam(OVERRIDE_LOGIN_URL) instanceof String s && !s.isBlank()
                ? s
                : loginUrl;
    }

    @SuppressWarnings("unchecked")
    private List<String> allowedRedirectUris(Request<?> request) {
        return request != null && request.attachedParam(OVERRIDE_ALLOWED_REDIRECT_URIS) instanceof List<?> l && !l.isEmpty()
                ? (List<String>) l
                : allowedRedirectUris;
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

    /**
     * A refusal from the issuer takes the route wrong credentials take: back to the sign-in page
     * when the credentials came from its form, so the person sees why and can choose again; to
     * the client's {@code redirect_uri} otherwise, as an OAuth error, because there is no page.
     */
    private void refuse(ByteArrayRequest request, ByteArrayResponse response, String redirectUri,
                        String state, OAuthTokenIssuer.Refusal refusal) {
        var fromForm = request.getExchange()
                .getAttachment(FormDataToBasicAuthInterceptor.FORM_CREDENTIALS_FOR_AUTHORIZE);
        var loginUrl = loginUrl(request);

        if (Boolean.TRUE.equals(fromForm) && loginUrl != null && !loginUrl.isBlank()) {
            var queryString = request.getExchange().getQueryString();
            var separator = loginUrl.contains("?") ? "&" : "?";
            var sb = new StringBuilder(loginUrl).append(separator)
                    .append("error=").append(encode(refusal.error()))
                    .append("&error_description=").append(encode(refusal.description()));
            if (queryString != null && !queryString.isBlank()) {
                sb.append("&").append(queryString);
            }
            response.getHeaders().put(Headers.LOCATION, sb.toString());
            response.setStatusCode(HttpStatus.SC_MOVED_TEMPORARILY);
            return;
        }

        redirectWithError(response, redirectUri, state, refusal.error(), refusal.description());
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
