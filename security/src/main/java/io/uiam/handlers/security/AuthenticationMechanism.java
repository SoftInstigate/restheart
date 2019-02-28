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
package io.uiam.handlers.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uiam.plugins.authentication.PluggableAuthenticationMechanism;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;

/**
 * log PluggableAuthenticationMechanism outcomes and makes sure that the wrapped
 * PluggableAuthenticationMechanism can't fail the whole authetication process
 * if it doesn't authenticate the request.
 *
 * when multiple AuthenticationMechanisms are defined, the standard undertow
 * authentication process is:
 *
 * As an in-bound request is received the authenticate method is called on each
 * mechanism in turn until one of the following occurs: - A mechanism
 * successfully authenticates the incoming request. - A mechanism attempts but
 * fails to authenticate the request. - The list of mechanisms is exhausted.
 *
 * * @see
 * http://undertow.io/javadoc/2.0.x/io/undertow/security/api/AuthenticationMechanism.html
 *
 * The uIAM process is:
 *
 * As an in-bound request is received the authenticate method is called on each
 * mechanism in turn until one of the following occurs: - A mechanism
 * successfully authenticates the incoming request. - The list of mechanisms is
 * exhausted.
 *
 * this is achieved avoiding the wrapper AuthenticationMechanism to return
 * NOT_AUTHENTICATED replacing the return value to NOT_ATTEMPTED
 *
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class AuthenticationMechanism implements PluggableAuthenticationMechanism {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationMechanism.class);

    private final PluggableAuthenticationMechanism wrapped;

    public AuthenticationMechanism(PluggableAuthenticationMechanism wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public AuthenticationMechanismOutcome authenticate(HttpServerExchange exchange, SecurityContext securityContext) {
        AuthenticationMechanismOutcome outcome = wrapped.authenticate(exchange, securityContext);

        LOGGER.debug(wrapped.getMechanismName() + " -> " + outcome.name());

        switch (outcome) {
        case NOT_AUTHENTICATED:
            return AuthenticationMechanismOutcome.NOT_ATTEMPTED;
        default:
            return outcome;
        }
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        return wrapped.sendChallenge(exchange, securityContext);
    }

    @Override
    public String getMechanismName() {
        return wrapped.getMechanismName();
    }
}
