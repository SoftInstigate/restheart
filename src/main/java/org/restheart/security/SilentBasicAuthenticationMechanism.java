/*
 * RESTHeart - the Web API for MongoDB
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
package org.restheart.security;

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
