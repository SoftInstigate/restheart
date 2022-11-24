/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2022 SoftInstigate
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

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.FlexBase64;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import java.io.IOException;
import java.nio.charset.Charset;

import org.restheart.configuration.ConfigurationException;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.TokenManager;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * this extends the undertow BasicAuthenticationMechanism and authenticates
 * requests using AuthTokenIdentityManager.
 *
 * if user already authenticated via a different mechanism, a token is
 * generated so that later calls can be use the token instead of the actual
 * password
 *
 */
@RegisterPlugin(name = "tokenBasicAuthMechanism",
                description = "authenticates the requests using the configured Token Manager",
                enabledByDefault = false,
                priority = Integer.MIN_VALUE)
public class TokenBasicAuthMechanism extends BasicAuthenticationMechanism implements AuthMechanism {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static final String BASIC_PREFIX = BASIC + " ";
    private static final int PREFIX_LENGTH = BASIC_PREFIX.length();
    private static final String COLON = ":";

    private TokenManager tokenManager = null;

    @Inject("registry")
    private PluginsRegistry registry;

    private static void clear(final char[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = 0x00;
        }
    }

    /**
     *
     * @throws org.restheart.configuration.ConfigurationException
     */
    public TokenBasicAuthMechanism() throws ConfigurationException {
        super("RESTHeart Realm", "tokenBasicAuthMechanism", true);
    }

    @OnInit
    public void init() throws ConfigurationException {
        this.tokenManager = registry.getTokenManager() != null
            ? registry.getTokenManager().getInstance()
            : null;
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange, final SecurityContext securityContext) {
        var authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
        if (authHeaders != null) {
            for (var current : authHeaders) {
                if (current.startsWith(BASIC_PREFIX)) {
                    var base64Challenge = current.substring(PREFIX_LENGTH);
                    String plainChallenge = null;
                    try {
                        var decode = FlexBase64.decode(base64Challenge);
                        plainChallenge = new String(decode.array(), decode.arrayOffset(), decode.limit(), UTF_8);
                    } catch (IOException e) {
                        // nothing to do
                    }
                    int colonPos;
                    if (plainChallenge != null && (colonPos = plainChallenge.indexOf(COLON)) > -1) {
                        var userName = plainChallenge.substring(0, colonPos);
                        var password = plainChallenge.substring(colonPos + 1).toCharArray();

                        var credential = new PasswordCredential(password);
                        try {
                            final AuthenticationMechanismOutcome result;
                            // this is where the token cache comes into play
                            var account = tokenManager.verify(userName, credential);
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
    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        var authHeader = exchange.getRequestHeaders().getFirst(AUTHORIZATION);

        if (authHeader == null) {
            return new ChallengeResult(false); // --> FORBIDDEN
        } else {
            return new ChallengeResult(true, UNAUTHORIZED);
        }
    }
}
