/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.plugins.security.Authenticator;
import org.restheart.security.authenticators.MongoRealmAuthenticator;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.undertow.UndertowMessages.MESSAGES;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.PasswordCredential;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.FlexBase64;
import static io.undertow.util.Headers.AUTHORIZATION;
import static io.undertow.util.Headers.BASIC;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;


/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "basicAuthMechanism",
                description = "handles the basic authentication scheme",
                enabledByDefault = false)
public class BasicAuthMechanism extends io.undertow.security.impl.BasicAuthenticationMechanism implements AuthMechanism {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicAuthMechanism.class);
    public static final String SILENT_HEADER_KEY = "No-Auth-Challenge";
    public static final String SILENT_QUERY_PARAM_KEY = "noauthchallenge";

    private Authenticator authenticator;

    public BasicAuthMechanism() throws ConfigurationException {
        super("RESTHeart Realm", "basicAuthMechanism", false);
    }

    @Inject("config")
    private Map<String, Object> config;

    @Inject("registry")
    private PluginsRegistry registry;

    @OnInit
    public void init() throws ConfigurationException {
        // the authenticator specified in auth mechanism configuration
        String authenticatorName = arg(config, "authenticator");
        
        LOGGER.debug("Initializing BasicAuthMechanism with authenticator: {}", authenticatorName);

        try {
            var authenticatorRecord = registry.getAuthenticator(authenticatorName);

            if (authenticatorRecord != null) {
                this.authenticator = authenticatorRecord.getInstance();
                var authenticatorClass = this.authenticator.getClass().getSimpleName();
                
                LOGGER.debug("Found authenticator: {} ({}) - Setting as identity manager", 
                    authenticatorName, authenticatorClass);
                    
                setIdentityManager(this.authenticator);
                
                LOGGER.debug("BasicAuthMechanism initialization completed with authenticator: {} ({})", 
                    authenticatorName, authenticatorClass);
            } else {
                var availableAuthenticators = registry.getAuthenticators().stream()
                    .map(a -> a.getName())
                    .collect(java.util.stream.Collectors.toList());
                    
                LOGGER.error("Authenticator '{}' not found. Available authenticators: {}", 
                    authenticatorName, availableAuthenticators);
                    
                throw new ConfigurationException("authenticator " + authenticatorName + " is not available");
            }
        } catch(ConfigurationException ce) {
            LOGGER.error("Configuration error loading authenticator '{}': {}", authenticatorName, ce.getMessage());
            throw new ConfigurationException("authenticator " + authenticatorName + " is not available. check configuration option /basicAuthMechanism/authenticator");
        }
    }

    private void setIdentityManager(IdentityManager idm) {
        try {
            var clazz = Class.forName("io.undertow.security.impl.BasicAuthenticationMechanism");
            var idmF = clazz.getDeclaredField("identityManager");
            idmF.setAccessible(true);

            idmF.set(this, idm);
        } catch (ClassNotFoundException | SecurityException | NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException("Error setting identity manager", ex);
        }
    }

    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        if (exchange.getRequestHeaders().contains(SILENT_HEADER_KEY) || exchange.getQueryParameters().containsKey(SILENT_QUERY_PARAM_KEY)) {
            return new ChallengeResult(true, UNAUTHORIZED);
        } else {
            return super.sendChallenge(exchange, securityContext);
        }
    }

    private static final String BASIC_PREFIX = BASIC + " ";
    private static final String LOWERCASE_BASIC_PREFIX = BASIC_PREFIX.toLowerCase(Locale.ENGLISH);
    private static final int PREFIX_LENGTH = BASIC_PREFIX.length();
    private static final String COLON = ":";

    /**
     * @param exchange
     * @param securityContext
     * @return
     * @see io.undertow.server.HttpHandler#handleRequest(io.undertow.server.HttpServerExchange)
     */
    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {

        var authHeaders = exchange.getRequestHeaders().get(AUTHORIZATION);
        if (authHeaders != null) {
            for (String current : authHeaders) {
                if (current.toLowerCase(Locale.ENGLISH).startsWith(LOWERCASE_BASIC_PREFIX)) {

                    String base64Challenge = current.substring(PREFIX_LENGTH);
                    String plainChallenge;
                    try {
                        var decode = FlexBase64.decode(base64Challenge);

                        //TODO check the charset from superclass

                        plainChallenge = new String(decode.array(), decode.arrayOffset(), decode.limit(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        LOGGER.debug("failed to decode challenge");
                        plainChallenge = null;
                    }
                    int colonPos;
                    if (plainChallenge != null && (colonPos = plainChallenge.indexOf(COLON)) > -1) {
                        String userName = plainChallenge.substring(0, colonPos);
                        char[] password = plainChallenge.substring(colonPos + 1).toCharArray();

                        var credential = new PasswordCredential(password);
                        try {
                            var authenticatorStartTime = System.currentTimeMillis();
                            var authenticatorName = PluginUtils.name(this.authenticator);
                            
                            final AuthenticationMechanismOutcome result;
                            final Account account;
                            if (authenticator instanceof MongoRealmAuthenticator mauth) {
                                account = mauth.verify(Request.of(exchange), userName, credential);
                            } else {
                                account = this.authenticator.verify(userName, credential);
                            }
                            
                            var authenticatorDuration = System.currentTimeMillis() - authenticatorStartTime;
                            
                            if (account != null) {
                                var accountPrincipal = account.getPrincipal().getName();
                                var accountRoles = account.getRoles().stream().collect(java.util.stream.Collectors.toSet());
                                LOGGER.debug("Authenticator {} verified user '{}' ({}ms) - Roles: {}", 
                                    authenticatorName, userName, authenticatorDuration, accountRoles);
                                    
                                securityContext.authenticationComplete(account, getMechanismName(), false);
                                result = AuthenticationMechanismOutcome.AUTHENTICATED;
                            } else {
                                LOGGER.debug("Authenticator {} failed to verify user '{}' ({}ms)", 
                                    authenticatorName, userName, authenticatorDuration);
                                    
                                securityContext.authenticationFailed(MESSAGES.authenticationFailed(userName), getMechanismName());
                                result = AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                            }
                            return result;
                        } catch (Exception ex) {
                            LOGGER.error("Error in authenticator {} ({}) for user '{}' - {} {}", 
                                PluginUtils.name(this.authenticator), this.authenticator.getClass().getSimpleName(), 
                                userName, exchange.getRequestMethod().toString(), exchange.getRequestPath(), ex);
                            throw ex;
                        } finally {
                            clear(password);
                        }
                    }

                    // By this point we had a header we should have been able to verify but for some reason
                    // it was not correctly structured.
                    return AuthenticationMechanismOutcome.NOT_AUTHENTICATED;
                }
            }
        }

        // No suitable header has been found in this request,
        return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
    }

    private static void clear(final char[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = 0x00;
        }
    }
}
