/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
package org.restheart.plugins.security;

import java.time.Duration;

import org.restheart.security.BaseAccount;

/**
 * Issues signed JWTs on behalf of any plugin that needs one, with the deployment's own single
 * signing policy — the same key, algorithm, issuer, audience and claim selection every other JWT
 * this deployment produces carries, so a token minted here is verifiable by the very same
 * {@code jwtAuthenticationMechanism} that verifies one issued at login or on {@code /token}.
 *
 * <p>Obtained by name from the {@code jwtIssuer} provider:
 * <pre>
 * &#64;Inject("jwtIssuer")
 * private JwtIssuer jwtIssuer;
 * </pre>
 *
 * <p>The contract lives in {@code restheart-commons} while the implementation and the signing key
 * live in {@code restheart-security}: that is what lets a plugin in any module — including one
 * that depends on {@code restheart-commons} alone, and therefore has neither the JWT library nor
 * access to the key — mint a token without carrying its own copy of the issuance logic or a second
 * copy of the key in its configuration.
 *
 * <p>Deliberately distinct from {@link TokenManager}, which is about the deployment's normal
 * <em>session</em> token for a login flow: it is pluggable, at most one is configured, and it need
 * not issue JWTs at all (an opaque random token is a valid token manager). This is the narrower,
 * always-JWT capability, available to callers that specifically need a signed JWT and a lifetime
 * of their own choosing — for example a service composing a request for someone else to execute,
 * which needs to hand over a credential that is worthless minutes later rather than a long-lived
 * one.
 */
public interface JwtIssuer {
    /**
     * Mints a signed JWT for {@code account}, valid for {@code ttl}, carrying the same subject and
     * roles the account already has — never more privilege than it already holds — plus whatever
     * claims the deployment's configured claim policy selects from its properties.
     *
     * @param account the already-authenticated principal to issue the token for
     * @param ttl     how long the token stays valid, from now
     * @return the signed, encoded JWT
     */
    String issue(BaseAccount account, Duration ttl);

    /**
     * Mints a signed JWT for {@code account} using the issuer's own configured default lifetime,
     * for a caller with no particular lifetime requirement of its own.
     *
     * @param account the already-authenticated principal to issue the token for
     * @return the signed, encoded JWT
     */
    String issue(BaseAccount account);

    /**
     * Mints a signed JWT carrying an explicit {@code iss} instead of this deployment's own.
     *
     * <p>For the case where the issuing node is not the node that will verify the token: a
     * control-plane instance minting a token addressed to one of several downstream instances,
     * each configured with its own issuer, has to stamp the <em>destination's</em> issuer on it,
     * chosen per token — its own would be rejected there. Same for reissuing a token (a cookie
     * renewal, say) that must keep the {@code iss} of the one it replaces.
     *
     * <p>Everything else is unchanged: same key, same algorithm, same audience, same claim policy
     * and denylist. Only the {@code iss} claim differs, and only for this one token.
     *
     * @param account the already-authenticated principal to issue the token for
     * @param ttl     how long the token stays valid, from now
     * @param issuer  the {@code iss} claim to stamp; {@code null} falls back to the configured one
     * @return the signed, encoded JWT
     */
    String issue(BaseAccount account, Duration ttl, String issuer);
}
