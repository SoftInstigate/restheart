package org.restheart.accounts.util;

import com.google.gson.JsonObject;

import io.undertow.util.HttpString;
import org.restheart.plugins.accounts.AccountsConfigData;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.security.services.TokenRedirectHelper;

/**
 * Single, uniform way to hand a freshly issued JWT to the client across all
 * {@code restheart-accounts} auto-login flows (email verification, invite
 * activation, password reset, team switch, OAuth callback).
 *
 * <p>Three delivery modes, selected by the {@code delivery} query parameter:
 * <ul>
 *   <li>{@link Mode#COOKIE} — sets the canonical {@code Bearer_<jwt>} HttpOnly cookie
 *       (same-origin / shared registrable domain).</li>
 *   <li>{@link Mode#BODY} — returns the token in the JSON response body
 *       ({@code access_token}, {@code token_type}, {@code expires_in}) and in the
 *       {@code Auth-Token} header (cross-origin SPAs using Bearer auth via {@code fetch()}).</li>
 *   <li>{@link Mode#FRAGMENT} — appends {@code #access_token=…} to a redirect URL
 *       (browser-navigation flows: email links, OAuth callback).</li>
 * </ul>
 *
 * <p>Each endpoint restricts the modes it accepts to those meaningful for its transport
 * (e.g. a {@code fetch()}-based PATCH accepts {@code cookie|body}, a browser-navigation GET
 * accepts {@code cookie|fragment}) and picks a sensible default when the parameter is absent.
 */
public final class TokenDelivery {

    /** Token delivery mechanism. */
    public enum Mode { COOKIE, BODY, FRAGMENT }

    private static final HttpString SET_COOKIE = HttpString.tryFromString("Set-Cookie");
    private static final HttpString AUTH_TOKEN = HttpString.tryFromString("Auth-Token");

    private TokenDelivery() {
        // utility class — no instances
    }

    /**
     * Parses the {@code delivery} query-parameter value into a {@link Mode}.
     *
     * @param raw         the raw parameter value (may be {@code null}/blank)
     * @param defaultMode the mode to use when {@code raw} is absent or unrecognised
     */
    public static Mode resolve(String raw, Mode defaultMode) {
        if (raw == null || raw.isBlank()) {
            return defaultMode;
        }
        return switch (raw.trim().toLowerCase()) {
            case "cookie"                  -> Mode.COOKIE;
            case "body", "bearer", "token" -> Mode.BODY;
            case "fragment"                -> Mode.FRAGMENT;
            default                        -> defaultMode;
        };
    }

    /**
     * Sets the canonical auth cookie ({@code Bearer_<jwt>}) on the response, resolving
     * cookie name, domain, {@code Secure} and {@code Max-Age} (= JWT TTL) from config/overrides.
     */
    public static void cookie(ServiceResponse<?> res, ServiceRequest<?> req, AccountsConfigData conf, String jwt) {
        var header = JwtHelper.setCookieHeader(
                jwt,
                conf.cookieName(),
                RequestOverrides.cookieDomain(req, conf),
                conf.jwtTtl(),
                RequestOverrides.cookieSecure(req, conf));
        res.getHeaders().add(SET_COOKIE, header);
    }

    /**
     * Delivers the token in bearer style: the {@code Auth-Token} header plus the OAuth-style
     * fields ({@code access_token}, {@code token_type}, {@code expires_in}) added to {@code responseBody}.
     *
     * @param responseBody the JSON object the caller will set as the response content
     */
    public static void body(ServiceResponse<?> res, JsonObject responseBody, AccountsConfigData conf, String jwt) {
        res.getHeaders().put(AUTH_TOKEN, jwt);
        responseBody.addProperty("access_token", jwt);
        responseBody.addProperty("token_type", "Bearer");
        if (conf.jwtTtl() > 0) {
            responseBody.addProperty("expires_in", conf.jwtTtl() * 60);
        }
    }

    /**
     * Builds a redirect URL carrying the token as a URL fragment
     * ({@code #access_token=…&token_type=Bearer&expires_in=…}), same mechanism as
     * {@code GET /token/redirect}.
     */
    public static String fragmentUrl(String baseUrl, AccountsConfigData conf, String jwt) {
        Integer expiresIn = conf.jwtTtl() > 0 ? conf.jwtTtl() * 60 : null;
        return TokenRedirectHelper.appendTokenFragment(baseUrl, jwt, "Bearer", expiresIn);
    }
}
