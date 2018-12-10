/*
 * uIAM - the IAM for microservices
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
package io.uiam.handlers.injectors;

import io.uiam.handlers.PipedHttpHandler;
import io.uiam.handlers.RequestContext;
import io.uiam.plugins.authentication.impl.BaseAccount;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * It injects the X-Powered-By response header
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 */
public class AccountHeadersInjector extends PipedHttpHandler {
    /**
     * Creates a new instance of XPoweredByInjector
     *
     * @param next
     */
    public AccountHeadersInjector(PipedHttpHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of XPoweredByInjector
     *
     */
    public AccountHeadersInjector() {
        super(null);
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        if (exchange != null && exchange.getSecurityContext() != null
                && exchange.getSecurityContext().getAuthenticatedAccount() != null) {
            Account a = exchange.getSecurityContext().getAuthenticatedAccount();

            if (a.getPrincipal() != null) {
                exchange.getRequestHeaders().add(getHeaderForPrincipalName(), a.getPrincipal().getName());
            }

            StringBuffer rolesBS = new StringBuffer();

            if (a instanceof BaseAccount && ((BaseAccount) a).getRoles() != null) {

                ((BaseAccount) a).getRoles().stream().forEachOrdered(role -> rolesBS.append(role.concat(",")));

                if (rolesBS.length() > 1) {
                    exchange.getRequestHeaders().add(getHeaderForPrincipalRoles(),
                            rolesBS.substring(0, rolesBS.length() - 1));
                }
            }
        }

        if (getNext() != null) {
            getNext().handleRequest(exchange, context);
        }
    }

    private HttpString getHeaderForPrincipalName() {
        return HttpString.tryFromString("X-Forwarded-Account-Id");
    }

    private HttpString getHeaderForPrincipalRoles() {
        return HttpString.tryFromString("X-Forwarded-Account-Roles");
    }
}
