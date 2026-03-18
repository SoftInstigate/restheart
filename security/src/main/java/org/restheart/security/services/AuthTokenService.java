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

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.exchange.Request;
import org.restheart.plugins.*;
import org.restheart.security.ACLRegistry;
import org.restheart.security.BaseAccount;
import org.restheart.security.JwtAccount;
import org.restheart.security.tokens.JwtConfigProvider;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.handlers.form.FormDataParser;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.restheart.plugins.security.TokenManager.*;

/**
 * OAuth 2.0-inspired token endpoint for authentication.
 * Supports both /token (returns token in response) and /token/cookie (sets cookie).
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
	name = "authTokenService",
	description = "OAuth 2.0 token endpoint (password, client_credentials, authorization_code grants)",
	secure = false,
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

	@Inject("jwtConfigProvider")
	private JwtConfigProvider.JwtConfig jwtConfig;

	/**
	 * Verifier used to validate authorization-code JWTs (issued by OAuthAuthorizationService).
	 */
	private JWTVerifier authCodeVerifier;

	@Inject("acl-registry")
	ACLRegistry aclRegistry;

	@OnInit
	public void init() {
		// allow authenticated requests to /token and /token/cookie;
		// also allow unauthenticated POST to /token for the authorization_code grant (PKCE flow)
		aclRegistry.registerAllow(req ->
			(req.isAuthenticated() && req.getPath().startsWith(TOKEN_ENDPOINT)) ||
				(req.getMethod() == org.restheart.exchange.ExchangeKeys.METHOD.POST && TOKEN_ENDPOINT.equals(req.getPath()))
		);

		// Build a verifier for authorization-code JWTs using the shared signing key.
		// Uses the same key as jwtTokenManager → works across all nodes in a cluster.
		var algo = OAuthAuthorizationService.buildAlgorithm(jwtConfig);
		this.authCodeVerifier = JWT.require(algo)
			.withIssuer(jwtConfig.issuer())
			.build();
	}

	/**
	 * @param request  ByteArrayRequest
	 * @param response ByteArrayResponse
	 * @throws Exception in case of any error
	 */
	@Override
	public void handle(ByteArrayRequest request, ByteArrayResponse response) throws Exception {
		var path = request.getPath();

		var isCookieEndpoint = TOKEN_COOKIE_ENDPOINT.equals(path);

		switch (request.getMethod()) {
			case OPTIONS -> handleOptions(request);
			case GET -> {
				if (isCookieEndpoint) {
					// GET not supported on /token/cookie
					response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
				} else {
					handleGet(request, response);
				}
			}
			case POST -> handlePost(request, response, isCookieEndpoint);
			case DELETE -> {
				if (isCookieEndpoint) {
					// DELETE not supported on /token/cookie (use AuthCookieRemover instead)
					response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
				} else {
					handleDelete(request, response);
				}
			}
			default -> response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
		}
		;
	}

	/**
	 * Handle POST request - issue new token.
	 * Supports three grant types:
	 * <ul>
	 *   <li>Authenticated request (Basic Auth / token auth) → issue token for the authenticated user</li>
	 *   <li>{@code grant_type=authorization_code} → validate PKCE and exchange authorization code for token</li>
	 * </ul>
	 */
	private void handlePost(ByteArrayRequest request, ByteArrayResponse response, boolean isCookieEndpoint) throws Exception {
		// --- unauthenticated request ---
		if (!request.isAuthenticated()) {
			// authorization_code grant is the only unauthenticated POST to /token (not /token/cookie)
			if (!isCookieEndpoint) {
				var bodyParams = parseFormBody(request);
				if ("authorization_code".equals(bodyParams.get("grant_type"))) {
					handleAuthorizationCodeGrant(bodyParams, response);
					return;
				}
			}
			response.getHeaders().put(HttpString.tryFromString("WWW-Authenticate"), "Basic realm=\"RESTHeart\"");
			response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
			return;
		}

		// --- standard authenticated flow ---
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
	 * Handles the {@code grant_type=authorization_code} flow.
	 *
	 * <p>The authorization code is a short-lived JWT signed with the shared
	 * {@link JwtConfigProvider} key (issued by {@link OAuthAuthorizationService}).
	 * Verifying the JWT signature and expiry is sufficient to authenticate the code —
	 * no shared storage is needed, making this fully stateless and safe in multi-instance
	 * deployments.
	 *
	 * <p>After signature/expiry verification, PKCE S256 is checked:
	 * {@code BASE64URL(SHA-256(code_verifier)) == code_challenge}.
	 */
	private void handleAuthorizationCodeGrant(Map<String, String> bodyParams, ByteArrayResponse response) {
		var code = bodyParams.get("code");
		var codeVerifier = bodyParams.get("code_verifier");

		if (code == null || code.isBlank()) {
			sendTokenError(response, HttpStatus.SC_BAD_REQUEST, "invalid_request", "code is required");
			return;
		}
		if (codeVerifier == null || codeVerifier.isBlank()) {
			sendTokenError(response, HttpStatus.SC_BAD_REQUEST, "invalid_request", "code_verifier is required");
			return;
		}

		// Verify the authorization-code JWT (signature + expiry).
		// The JWT was signed with the shared key → any cluster node can verify it.
		com.auth0.jwt.interfaces.DecodedJWT decoded;
		try {
			decoded = authCodeVerifier.verify(code);
		} catch (Exception e) {
			LOGGER.debug("Authorization code verification failed: {}", e.getMessage());
			sendTokenError(response, HttpStatus.SC_BAD_REQUEST, "invalid_grant", "authorization code is invalid or expired");
			return;
		}

		var codeChallenge = decoded.getClaim(OAuthAuthorizationService.CLAIM_CODE_CHALLENGE).asString();

		// verify PKCE S256: BASE64URL(SHA-256(ASCII(code_verifier))) == code_challenge
		if (!verifyPkceS256(codeVerifier, codeChallenge)) {
			sendTokenError(response, HttpStatus.SC_BAD_REQUEST, "invalid_grant", "code_verifier does not match code_challenge");
			return;
		}

		var username = decoded.getSubject();
		var rolesArr = decoded.getClaim(OAuthAuthorizationService.CLAIM_ROLES).asArray(String.class);
		var roles = rolesArr != null ? Set.of(rolesArr) : Set.<String>of();

		// Reconstruct account from the full auth-code JWT payload (WithProperties) so that
		// jwtTokenManager.get(account) can apply account-properties-claims filtering normally.
		var authCodePayload = new String(
			java.util.Base64.getUrlDecoder().decode(decoded.getPayload()), StandardCharsets.UTF_8);
		var account = new JwtAccount(username, roles, authCodePayload);

		// issue access token via the configured token manager
		var tokenManagerRecord = this.registry.getTokenManager();
		if (tokenManagerRecord == null) {
			LOGGER.error("No token manager configured; cannot issue token for authorization_code grant");
			sendTokenError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, "server_error", "token manager not available");
			return;
		}

		var credential = tokenManagerRecord.getInstance().get(account);

		if (credential == null) {
			LOGGER.error("Token manager returned null credential for user '{}'", username);
			sendTokenError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, "server_error", "failed to issue token");
			return;
		}

		var tokenString = new String(credential.getPassword());
		var expiresIn = expiresInFromJwt(tokenString);

		var resp = new JsonObject();
		resp.add("access_token", new JsonPrimitive(tokenString));
		resp.add("token_type", new JsonPrimitive("Bearer"));
		if (expiresIn != null) {
			resp.add("expires_in", new JsonPrimitive(expiresIn));
		}

		response.getHeaders().put(HttpString.tryFromString("Cache-Control"), "no-store");
		response.getHeaders().put(HttpString.tryFromString("Pragma"), "no-cache");
		response.setContentTypeAsJson();
		response.setContent(resp.toString());
		response.setStatusCode(HttpStatus.SC_OK);

		LOGGER.debug("Token issued via authorization_code grant for user '{}'", username);
	}

	/**
	 * Verifies PKCE S256: {@code BASE64URL(SHA-256(ASCII(code_verifier))) == code_challenge}.
	 */
	private boolean verifyPkceS256(String codeVerifier, String codeChallenge) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			var hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			var computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
			return computed.equals(codeChallenge);
		} catch (Exception e) {
			LOGGER.error("PKCE S256 verification failed", e);
			return false;
		}
	}

	/**
	 * Decodes a JWT (without verification) and returns the remaining seconds until expiry.
	 * Returns {@code null} if the token is not a JWT or has no expiry claim.
	 */
	private Integer expiresInFromJwt(String token) {
		try {
			var decoded = JWT.decode(token);
			var expiresAt = decoded.getExpiresAt();
			if (expiresAt == null) return null;
			var remaining = (expiresAt.getTime() - System.currentTimeMillis()) / 1000;
			return (int) Math.max(0, remaining);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Parses form parameters from a request into a key→value map.
	 * First checks Undertow's FormData attachment (set by {@code FormDataToBasicAuthInterceptor},
	 * which consumes the body), then falls back to parsing the raw body bytes.
	 */
	private Map<String, String> parseFormBody(ByteArrayRequest request) {
		// FormDataToBasicAuthInterceptor may have already parsed the body and stored it as an attachment
		var formData = request.getExchange().getAttachment(FormDataParser.FORM_DATA);
		if (formData != null) {
			var result = new HashMap<String, String>();
			for (var name : formData) {
				var field = formData.getFirst(name);
				if (field != null && !field.isFileItem()) {
					result.put(name, field.getValue());
				}
			}
			return result;
		}
		// Fall back: parse raw body bytes (no FormDataToBasicAuthInterceptor in the pipeline)
		var body = request.getContent();
		var result = new HashMap<String, String>();
		if (body == null || body.length == 0) return result;
		var bodyStr = new String(body, StandardCharsets.UTF_8);
		for (var pair : bodyStr.split("&")) {
			var idx = pair.indexOf('=');
			if (idx > 0) {
				try {
					var key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
					var value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
					result.put(key, value);
				} catch (Exception ignored) { /* skip malformed pairs */ }
			}
		}
		return result;
	}

	/**
	 * Writes an OAuth 2.0 error response to the token endpoint.
	 */
	private void sendTokenError(ByteArrayResponse response, int status, String error, String description) {
		LOGGER.warn("Token error {}: {} - {}", status, error, description);
		var body = "{\"error\":\"" + error + "\",\"error_description\":\"" + description + "\"}";
		response.setContentTypeAsJson();
		response.setContent(body);
		response.setStatusCode(status);
	}

	/**
	 * Handle GET request - return current token info
	 */
	private void handleGet(ByteArrayRequest request, ByteArrayResponse response) {
		if (!request.isAuthenticated()) {
			response.getHeaders().put(HttpString.tryFromString("WWW-Authenticate"), "Basic realm=\"RESTHeart\"");
			response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
			return;
		}

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

	private static final String ACCESS_CONTROL_EXPOSE_HEADERS =
		AUTH_TOKEN_HEADER.toString()
		+ ", " + AUTH_TOKEN_VALID_HEADER.toString()
		+ ", " + AUTH_TOKEN_LOCATION_HEADER.toString();

	@Override
	public String accessControlExposeHeaders(Request<?> r) {
		return ACCESS_CONTROL_EXPOSE_HEADERS;
	}
}
