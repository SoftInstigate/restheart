/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.security;

/**
 * Canonical builder for the RESTHeart authentication cookie, shared by the core
 * {@code authCookieSetter} ({@code /token/cookie}), by {@code restheart-accounts} and by
 * external plugins.
 *
 * <p>Lives in {@code restheart-commons} (Apache-2.0) precisely so that external plugins may
 * produce a compatible auth cookie without depending on the AGPL {@code restheart-security} module.
 *
 * <p>The cookie <em>value</em> uses an underscore separator ({@code Bearer_<jwt>} or
 * {@code Basic_<...>}) for RFC 6265 compliance; {@code authCookieHandler} turns it back
 * into a standard {@code Authorization} header by replacing the underscore with a space.
 * Every producer of this cookie MUST use this format — a value like {@code "Bearer <jwt>"}
 * (space) is not RFC 6265 compliant and is not recognised by {@code authCookieHandler}.
 *
 * <p>Centralising the format here prevents the attributes ({@code Secure}, {@code SameSite},
 * {@code Max-Age}) and the {@code Bearer_} prefix from drifting apart across producers.
 */
public final class AuthCookie {

    /** Value prefix for a JWT bearer cookie (jwtAuthenticationMechanism). */
    public static final String BEARER_PREFIX = "Bearer_";

    /** Value prefix for a token-basic-auth cookie (rndTokenManager). */
    public static final String BASIC_PREFIX = "Basic_";

    private AuthCookie() {
        // utility class — no instances
    }

    /** Canonical value for a JWT bearer cookie: {@code Bearer_<jwt>}. */
    public static String bearerValue(String jwt) {
        return BEARER_PREFIX + jwt;
    }

    /**
     * Builds the full {@code Set-Cookie} header value in the canonical form
     * {@code <name>=<value>; Domain=…; Path=…[; HttpOnly][; SameSite=…][; Secure][; Max-Age=…]}.
     *
     * @param name           cookie name (e.g. {@code rh_auth})
     * @param value          cookie value, already prefixed (see {@link #bearerValue(String)} / {@link #BASIC_PREFIX})
     * @param domain         cookie {@code Domain}
     * @param path           cookie {@code Path}; blank/{@code null} defaults to {@code /}
     * @param secure         add the {@code Secure} attribute
     * @param httpOnly       add the {@code HttpOnly} attribute
     * @param sameSite       add a {@code SameSite} attribute
     * @param sameSiteMode   SameSite mode; normalised to canonical case ({@code strict} → {@code Strict})
     * @param maxAgeSeconds  if {@code > 0} adds {@code Max-Age}; otherwise the cookie is a session cookie
     */
    public static String header(String name, String value, String domain, String path,
                                boolean secure, boolean httpOnly, boolean sameSite,
                                String sameSiteMode, long maxAgeSeconds) {
        var sb = new StringBuilder()
                .append(name).append('=').append(value)
                .append("; Domain=").append(domain)
                .append("; Path=").append(path == null || path.isBlank() ? "/" : path);
        if (httpOnly) {
            sb.append("; HttpOnly");
        }
        if (sameSite) {
            sb.append("; SameSite=").append(canonicalSameSite(sameSiteMode));
        }
        if (secure) {
            sb.append("; Secure");
        }
        if (maxAgeSeconds > 0) {
            sb.append("; Max-Age=").append(maxAgeSeconds);
        }
        return sb.toString();
    }

    /** Normalises a SameSite mode to its canonical case, e.g. {@code strict} → {@code Strict}. */
    private static String canonicalSameSite(String mode) {
        if (mode == null || mode.isBlank()) {
            return "Strict";
        }
        var m = mode.trim().toLowerCase();
        return Character.toUpperCase(m.charAt(0)) + m.substring(1);
    }
}
