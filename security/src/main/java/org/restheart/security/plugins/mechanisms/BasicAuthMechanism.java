/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.plugins.mechanisms;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;
import java.util.Map;
import org.restheart.ConfigurationException;
import static org.restheart.plugins.ConfigurablePlugin.argValue;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.security.plugins.PluginsRegistry;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(
        name = "basicAuthMechanism",
        description = "handles the basic authentication scheme",
        enabledByDefault = false)
public class BasicAuthMechanism extends io.undertow.security.impl.BasicAuthenticationMechanism
        implements AuthMechanism {

    public static final String SILENT_HEADER_KEY = "No-Auth-Challenge";
    public static final String SILENT_QUERY_PARAM_KEY = "noauthchallenge";

    @OnInit
    public BasicAuthMechanism(final Map<String, Object> args)
            throws ConfigurationException {
        this("basicAuthMechanism", args);
    }

    public BasicAuthMechanism(final String mechanismName, final Map<String, Object> args)
            throws ConfigurationException {
        super(argValue(args, "realm"),
                mechanismName,
                false,
                // the authenticator specified in auth mechanism configuration
                PluginsRegistry.getInstance()
                        .getAuthenticator(argValue(args, "authenticator"))
                        .getInstance());
    }

    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange, final SecurityContext securityContext) {
        if (exchange.getRequestHeaders().contains(SILENT_HEADER_KEY)
                || exchange.getQueryParameters().containsKey(SILENT_QUERY_PARAM_KEY)) {
            return new ChallengeResult(true, UNAUTHORIZED);
        } else {
            return super.sendChallenge(exchange, securityContext);
        }
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(final HttpServerExchange exchange,
            final SecurityContext securityContext) {
        return super.authenticate(exchange, securityContext);
    }

    @Override
    public String getMechanismName() {
        return "basicAuthMechanism";
    }
}
