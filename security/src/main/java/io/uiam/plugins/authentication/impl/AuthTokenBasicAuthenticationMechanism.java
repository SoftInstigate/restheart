/*
 * uIAM - the IAM for microservices
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.uiam.plugins.authentication.impl;

import static io.uiam.plugins.ConfigurablePlugin.argValue;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import io.uiam.plugins.IDMCacheSingleton;
import io.uiam.plugins.PluginConfigurationException;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.FlexBase64;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 *         this extends the undertow BasicAuthenticationMechanism and
 *         authenticate the request using the AuthTokenIdentityManager.
 *
 *         if user already authenticated via a different mechanism, that a token
 *         is generated so that later calls can be use the token instead of the
 *         actual password
 *
 */
public class AuthTokenBasicAuthenticationMechanism
        extends BasicAuthenticationMechanism
        implements PluggableAuthenticationMechanism {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final String BASIC_PREFIX = BASIC + " ";
    private static final int PREFIX_LENGTH = BASIC_PREFIX.length();
    private static final String COLON = ":";

    private final String mechanismName;

    private IdentityManager identityManager = null;

    private static void clear(final char[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = 0x00;
        }
    }

    /**
     *
     * @param realmName
     */
    public AuthTokenBasicAuthenticationMechanism(final String mechanismName,
            final Map<String, Object> args)
            throws PluginConfigurationException {
        super(
                argValue(args, "realm"),
                mechanismName,
                true,
                IDMCacheSingleton
                        .getInstance()
                        .getIdentityManager(AuthTokenIdentityManager.NAME));

        this.mechanismName = mechanismName;
        this.identityManager = IDMCacheSingleton
                .getInstance()
                .getIdentityManager(AuthTokenIdentityManager.NAME);
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(
            final HttpServerExchange exchange,
            final SecurityContext securityContext) {

        List<String> authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
        if (authHeaders != null) {
            for (String current : authHeaders) {
                if (current.startsWith(BASIC_PREFIX)) {
                    String base64Challenge = current.substring(PREFIX_LENGTH);
                    String plainChallenge = null;
                    try {
                        ByteBuffer decode = FlexBase64.decode(base64Challenge);
                        plainChallenge = new String(decode.array(), decode.arrayOffset(), decode.limit(), UTF_8);
                    } catch (IOException e) {
                    }
                    int colonPos;
                    if (plainChallenge != null && (colonPos = plainChallenge.indexOf(COLON)) > -1) {
                        String userName = plainChallenge.substring(0, colonPos);
                        char[] password = plainChallenge.substring(colonPos + 1).toCharArray();

                        PasswordCredential credential = new PasswordCredential(password);
                        try {
                            final AuthenticationMechanismOutcome result;
                            // this is where the token cache comes into play
                            Account account = identityManager.verify(userName, credential);
                            if (account != null) {
                                securityContext.authenticationComplete(account, getMechanismName(), false);
                                result = AuthenticationMechanismOutcome.AUTHENTICATED;
                            } else {
                                result = AuthenticationMechanismOutcome.NOT_ATTEMPTED;
                            }
                            return result;
                        } finally {
                            clear(password);
                        }
                    }

                    return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
                }
            }
        }

        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange,
            final SecurityContext securityContext) {
        String authHeader = exchange.getRequestHeaders().getFirst(AUTHORIZATION);

        if (authHeader == null) {
            return new ChallengeResult(false); // --> FORBIDDEN
        } else {
            return new ChallengeResult(true, UNAUTHORIZED);
        }
    }

    /**
     * @return the mechanismName
     */
    public String getMechanismName() {
        return mechanismName;
    }
}
