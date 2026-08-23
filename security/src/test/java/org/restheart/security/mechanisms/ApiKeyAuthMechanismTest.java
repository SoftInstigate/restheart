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
package org.restheart.security.mechanisms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.restheart.plugins.security.Authenticator;
import org.restheart.security.ApiKeyCredential;
import org.restheart.security.BaseAccount;

import io.undertow.security.api.AuthenticationMechanism.AuthenticationMechanismOutcome;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;

/**
 * The property under test is the one that makes this mechanism additive: enabling
 * it must never change the outcome of a request that does not present a key with
 * its prefix. Everything else it does follows from that.
 */
public class ApiKeyAuthMechanismTest {

    private static final String PREFIX = "rhak_";
    private static final String GOOD_KEY = PREFIX + "7f2c9a";

    /** Accepts exactly one key and records what it was asked. */
    private static class StubAuthenticator implements Authenticator {
        private final Account account = new BaseAccount("robot", Set.of("cli"));
        String seen = null;

        @Override
        public Account verify(final Credential credential) {
            this.seen = new String(((ApiKeyCredential) credential).getKey());
            return GOOD_KEY.equals(this.seen) ? this.account : null;
        }

        @Override
        public Account verify(final String id, final Credential credential) {
            return null;
        }

        @Override
        public Account verify(final Account account) {
            return account;
        }
    }

    private ApiKeyAuthMechanism mechanism;
    private StubAuthenticator authenticator;
    private SecurityContext securityContext;

    @BeforeEach
    void setUp() throws Exception {
        this.authenticator = new StubAuthenticator();
        this.securityContext = mock(SecurityContext.class);
        this.mechanism = new ApiKeyAuthMechanism();

        // init() resolves the authenticator through the plugins registry, which
        // is not available here; set what it would have produced.
        set(this.mechanism, "prefix", PREFIX);
        set(this.mechanism, "authenticator", this.authenticator);
    }

    private static void set(final Object target, final String field, final Object value) throws Exception {
        final var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private AuthenticationMechanismOutcome authenticate(final String authorization) {
        final var exchange = mock(HttpServerExchange.class);
        final var headers = new HeaderMap();

        if (authorization != null) {
            headers.put(Headers.AUTHORIZATION, authorization);
        }

        when(exchange.getRequestHeaders()).thenReturn(headers);

        return this.mechanism.authenticate(exchange, this.securityContext);
    }

    @Test
    void noAuthorizationHeaderIsNotAttempted() {
        assertEquals(AuthenticationMechanismOutcome.NOT_ATTEMPTED, authenticate(null));
        assertNull(this.authenticator.seen, "the authenticator must not be consulted");
    }

    @Test
    void basicIsNotAttempted() {
        assertEquals(AuthenticationMechanismOutcome.NOT_ATTEMPTED, authenticate("Basic dXNlcjpwd2Q="));
        assertNull(this.authenticator.seen);
    }

    @Test
    void aBearerJwtIsLeftToTheJwtMechanism() {
        // The whole reason the prefix is required. A JWT under Bearer must reach
        // jwtAuthenticationMechanism untouched, so this has to decline rather
        // than fail — a failure would end the chain before it got there.
        final var jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSJ9.c2ln";

        assertEquals(AuthenticationMechanismOutcome.NOT_ATTEMPTED, authenticate("Bearer " + jwt));
        assertNull(this.authenticator.seen);
        verify(this.securityContext, never())
            .authenticationFailed(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
    }

    @Test
    void aMalformedBearerValueIsAlsoLeftAlone() {
        // Not a JWT either, but still not ours — jwtAuthenticationMechanism owns
        // the answer, and it is the one that can say "malformed JWT".
        assertEquals(AuthenticationMechanismOutcome.NOT_ATTEMPTED, authenticate("Bearer not-a-token"));
        assertNull(this.authenticator.seen);
    }

    @Test
    void aPrefixedKeyIsVerified() {
        assertEquals(AuthenticationMechanismOutcome.AUTHENTICATED, authenticate("Bearer " + GOOD_KEY));
        assertEquals(GOOD_KEY, this.authenticator.seen, "the prefix is part of the key, not stripped");
        verify(this.securityContext)
            .authenticationComplete(ArgumentMatchers.any(Account.class), ArgumentMatchers.anyString(),
                ArgumentMatchers.anyBoolean());
    }

    @Test
    void anUnknownPrefixedKeyFailsRatherThanFallingThrough() {
        // We claimed it by prefix, so we own the failure. Falling through would
        // gain nothing — it is definitely not a JWT — and would replace a
        // truthful answer with a confusing one.
        assertEquals(AuthenticationMechanismOutcome.NOT_AUTHENTICATED, authenticate("Bearer " + PREFIX + "unknown"));
        assertEquals(PREFIX + "unknown", this.authenticator.seen);
    }

    @Test
    void theChallengeIsNotInteractive() {
        // An API key is not something a human types into a browser dialog, so
        // this must not produce a WWW-Authenticate.
        final var exchange = mock(HttpServerExchange.class);
        final var result = this.mechanism.sendChallenge(exchange, this.securityContext);

        assertTrue(result.isChallengeSent());
        // No WWW-Authenticate is set: the mechanism touches no response header
        // at all, which is what keeps a browser from popping a native dialog.
        assertEquals(200, result.getDesiredResponseCode());
    }
}
