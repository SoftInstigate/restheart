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

/**
 * Builds redirect URLs that carry an auth token as a URL fragment
 * ({@code #access_token=...}) rather than a query parameter.
 *
 * <p>A fragment is never sent to any server — not on the redirect response
 * itself, not on any subsequent request the browser makes to that URL, and
 * not in the {@code Referer} header. A query parameter, by contrast,
 * travels with every future navigation from that URL and routinely ends up
 * in server access logs, reverse-proxy logs, and analytics — a real leak
 * vector for a bearer token. This mirrors OAuth 2.0's own long-established
 * Implicit Flow convention ({@code #access_token=...}).
 *
 * <p>Used by {@code AuthTokenService}'s {@code GET /token/redirect} and by
 * {@code restheart-accounts}' {@code OAuthCallback}, so both flows that
 * hand a token to a frontend via a real browser redirect build the
 * fragment the same way.
 */
public final class TokenRedirectHelper {

    private TokenRedirectHelper() {
    }

    /**
     * Appends {@code access_token} (and, if provided, {@code token_type} and
     * {@code expires_in}) as a URL fragment to {@code baseUrl}.
     *
     * @param baseUrl   the target URL, with any query string already applied
     *                  (e.g. {@code https://app.example.com?flow=signin})
     * @param token     the token value; must not be {@code null}
     * @param tokenType e.g. {@code "Bearer"}; may be {@code null} to omit
     * @param expiresIn seconds until expiry; may be {@code null} to omit
     * @return {@code baseUrl} with the fragment appended
     */
    public static String appendTokenFragment(String baseUrl, String token, String tokenType, Integer expiresIn) {
        var fragment = new StringBuilder("access_token=").append(urlEncode(token));

        if (tokenType != null && !tokenType.isBlank()) {
            fragment.append("&token_type=").append(urlEncode(tokenType));
        }

        if (expiresIn != null) {
            fragment.append("&expires_in=").append(expiresIn);
        }

        return baseUrl + "#" + fragment;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
