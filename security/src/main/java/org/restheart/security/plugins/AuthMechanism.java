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
package org.restheart.security.plugins;

import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMechanism.AuthenticationMechanismOutcome;
import io.undertow.security.api.AuthenticationMechanism.ChallengeResult;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import org.restheart.plugins.ConfigurablePlugin;

/**
 * @see https://restheart.org/docs/develop/security-plugins/#authentication-mechanisms
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public interface AuthMechanism extends
        AuthenticationMechanism,
        ConfigurablePlugin {
    @Override
    public AuthenticationMechanismOutcome authenticate(
            final HttpServerExchange exchange,
            final SecurityContext securityContext);

    @Override
    public ChallengeResult sendChallenge(final HttpServerExchange exchange,
            final SecurityContext securityContext);

    public String getMechanismName();
}
