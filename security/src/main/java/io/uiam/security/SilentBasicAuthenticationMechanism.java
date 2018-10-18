/*
 * Copyright 2014 - 2015 SoftInstigate.
 *
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
 */
package io.uiam.security;

import io.undertow.security.api.SecurityContext;
import io.undertow.security.impl.BasicAuthenticationMechanism;
import io.undertow.server.HttpServerExchange;
import static io.undertow.util.StatusCodes.UNAUTHORIZED;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * this extends the undertow BasicAuthenticationMechanism setting it to silent
 * and avoiding to send the Authorization header when authentication fails this
 * is required to avoid the basic authentication popup in web applications
 *
 * If silent is true then this mechanism will only take effect if there is an
 * Authorization header.
 *
 * This allows you to combine basic auth with form auth, so human users will use
 * form based auth, but allows programmatic clients to login using basic auth.
 *
 *
 */
public class SilentBasicAuthenticationMechanism extends BasicAuthenticationMechanism {

    /**
     *
     * @param realmName
     */
    public SilentBasicAuthenticationMechanism(String realmName) {
        super(realmName, "BASIC", true);
    }

    @Override
    public ChallengeResult sendChallenge(HttpServerExchange exchange, SecurityContext securityContext) {
        return new ChallengeResult(true, UNAUTHORIZED);
    }
}
