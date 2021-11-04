/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.security.handlers;

import io.undertow.server.HttpServerExchange;
import java.util.Set;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.security.AuthMechanism;

/**
 * This is the PipelinedHandler version of
 * io.undertow.security.handlers.AuthenticationMechanismsHandler that adds one
 * or more authenticator mechanisms to the security context
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthenticatorMechanismsHandler extends PipelinedHandler {

    private final Set<PluginRecord<AuthMechanism>> authenticatorMechanisms;

    public AuthenticatorMechanismsHandler(final PipelinedHandler next, final Set<PluginRecord<AuthMechanism>> authenticatorMechanisms) {
        super(next);
        this.authenticatorMechanisms = authenticatorMechanisms;
    }

    public AuthenticatorMechanismsHandler(final Set<PluginRecord<AuthMechanism>> authenticatorMechanisms) {
        this.authenticatorMechanisms = authenticatorMechanisms;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final var sc = exchange.getSecurityContext();

        if (sc != null) {
            authenticatorMechanisms.stream().forEachOrdered(mechanism -> sc.addAuthenticationMechanism(new AuthenticatorMechanismWrapper(mechanism.getInstance())));
        }

        next(exchange);
    }
}
