/*
 * uIAM - the IAM for microservices
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
package io.uiam.plugins.authentication.impl;

import static io.uiam.plugins.ConfigurablePlugin.argValue;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;

import java.util.Map;

import io.uiam.plugins.PluginsRegistry;
import io.uiam.plugins.PluginConfigurationException;
import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class BasicAuthenticationMechanism extends io.undertow.security.impl.BasicAuthenticationMechanism
        implements PluggableAuthenticationMechanism {

    private final String mechanismName;

    public static final String SILENT_HEADER_KEY = "No-Auth-Challenge";
    public static final String SILENT_QUERY_PARAM_KEY = "noauthchallenge";

    public BasicAuthenticationMechanism(final String mechanismName, final Map<String, Object> args)
            throws PluginConfigurationException {
        super(argValue(args, "realm"), mechanismName, false,
                PluginsRegistry.getInstance().getIdentityManager(argValue(args, "idm")));

        this.mechanismName = mechanismName;
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
        return mechanismName;
    }
}
