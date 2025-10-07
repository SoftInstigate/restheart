/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import io.undertow.security.api.AuthenticationMechanismContext;
import io.undertow.server.HttpServerExchange;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.security.AuthMechanism;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the PipelinedHandler version of
 * io.undertow.security.handlers.AuthenticationMechanismsHandler that adds one
 * or more authenticator mechanisms to the security context
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthenticatorMechanismsHandler extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatorMechanismsHandler.class);
    
    private final Set<AuthenticatorMechanismWrapper> wrappedAuthenticatorMechanisms;

    public AuthenticatorMechanismsHandler(final PipelinedHandler next, final Set<PluginRecord<AuthMechanism>> authenticatorMechanisms) {
        super(next);
        this.wrappedAuthenticatorMechanisms = authenticatorMechanisms.stream()
            .map(mechanism -> new AuthenticatorMechanismWrapper(mechanism.getInstance()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
            
        LOGGER.debug("Initialized AuthenticatorMechanismsHandler with {} mechanisms: {}", 
            authenticatorMechanisms.size(), 
            authenticatorMechanisms.stream()
                .map(m -> PluginUtils.name(m.getInstance()) + " (" + m.getInstance().getMechanismName() + ")")
                .collect(Collectors.joining(", ")));
    }

    public AuthenticatorMechanismsHandler(final Set<PluginRecord<AuthMechanism>> authenticatorMechanisms) {
        this.wrappedAuthenticatorMechanisms = authenticatorMechanisms.stream()
            .map(mechanism -> new AuthenticatorMechanismWrapper(mechanism.getInstance()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
            
        LOGGER.debug("Initialized AuthenticatorMechanismsHandler with {} mechanisms: {}", 
            authenticatorMechanisms.size(), 
            authenticatorMechanisms.stream()
                .map(m -> PluginUtils.name(m.getInstance()) + " (" + m.getInstance().getMechanismName() + ")")
                .collect(Collectors.joining(", ")));
    }


    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final var sc = exchange.getSecurityContext();
        var requestPath = exchange.getRequestPath();
        var requestMethod = exchange.getRequestMethod().toString();
        var registrationStartTime = System.currentTimeMillis();

        if (wrappedAuthenticatorMechanisms.isEmpty()) {
            LOGGER.debug("┌── AUTHENTICATION - no mechanisms");
        } else {
            LOGGER.debug("┌── AUTHENTICATION - {} mechanisms", wrappedAuthenticatorMechanisms.size());
        }

        if (sc != null && sc instanceof AuthenticationMechanismContext amc) {
            wrappedAuthenticatorMechanisms.stream().forEachOrdered(wrappedMechanism -> {
                amc.addAuthenticationMechanism(wrappedMechanism);
            });

            var mechanismRegistrationDuration = System.currentTimeMillis() - registrationStartTime;
            LOGGER.debug("└── AUTHENTICATION MECHANISMS REGISTERED in {}ms", mechanismRegistrationDuration);

            next(exchange);
        } else {
            LOGGER.error("The SecurityContext does not support authentication mechanisms for {} {} - Context type: {}",
                requestMethod, requestPath, sc != null ? sc.getClass().getSimpleName() : "null");
            throw new IllegalStateException("The SecurityContext does not support authentication mechanisms!");
        }
    }
}
