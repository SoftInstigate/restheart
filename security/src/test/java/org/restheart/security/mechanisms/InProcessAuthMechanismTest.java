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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.restheart.security.BaseAccount;
import org.restheart.utils.InProcessDispatcher;

import io.undertow.security.api.AuthenticationMechanism.AuthenticationMechanismOutcome;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;

/**
 * The in-process mechanism signs in exactly one kind of request: one that the dispatcher marked
 * and gave an identity to. Both attachments come from inside the process, never from the wire.
 */
public class InProcessAuthMechanismTest {
    private final InProcessAuthMechanism mechanism = new InProcessAuthMechanism();
    private final Account agent = new BaseAccount("agent", Set.of("reader"));

    @Test
    public void aRequestFromAListener_isNotAttempted_evenWithAPrincipalAttached() {
        // the principal alone is not enough: without the marker the exchange did not come from
        // the dispatcher, and a principal attached by anything else must count for nothing
        var exchange = new HttpServerExchange();
        exchange.putAttachment(InProcessDispatcher.PRINCIPAL, agent);
        var sc = mock(SecurityContext.class);

        assertEquals(AuthenticationMechanismOutcome.NOT_ATTEMPTED, mechanism.authenticate(exchange, sc));
        verify(sc, never()).authenticationComplete(any(), anyString(), anyBoolean());
    }

    @Test
    public void anInProcessRequestWithoutAnIdentity_isNotAttempted() {
        var exchange = new HttpServerExchange();
        exchange.putAttachment(InProcessDispatcher.IN_PROCESS, Boolean.TRUE);
        var sc = mock(SecurityContext.class);

        assertEquals(AuthenticationMechanismOutcome.NOT_ATTEMPTED, mechanism.authenticate(exchange, sc));
        verify(sc, never()).authenticationComplete(any(), anyString(), anyBoolean());
    }

    @Test
    public void anInProcessRequestWithAnIdentity_isSignedInAsThatIdentity() {
        var exchange = new HttpServerExchange();
        exchange.putAttachment(InProcessDispatcher.IN_PROCESS, Boolean.TRUE);
        exchange.putAttachment(InProcessDispatcher.PRINCIPAL, agent);
        var sc = mock(SecurityContext.class);

        assertEquals(AuthenticationMechanismOutcome.AUTHENTICATED, mechanism.authenticate(exchange, sc));
        verify(sc).authenticationComplete(agent, "InProcessAuthMechanism", false);
    }

    @Test
    public void itNeverChallenges() {
        var result = mechanism.sendChallenge(new HttpServerExchange(), mock(SecurityContext.class));

        assertEquals(false, result.isChallengeSent());
    }
}
