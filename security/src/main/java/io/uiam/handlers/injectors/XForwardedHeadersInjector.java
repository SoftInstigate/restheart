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
import io.uiam.handlers.Request;
import io.uiam.plugins.authentication.impl.BaseAccount;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Adds the following X-Forwarded custom headers to the proxied request:
 *
 * 'X-Forwarded-Account-Id', 'X-Forwarded-Account-Roles' and other headers set
 * with Response.addXForwardedHeader()
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 */
public class XForwardedHeadersInjector extends PipedHttpHandler {
    /**
     * Creates a new instance of AccountHeadersInjector
     *
     * @param next
     */
    public XForwardedHeadersInjector(PipedHttpHandler next) {
        super(next);
    }

    /**
     * Creates a new instance of AccountHeadersInjector
     *
     */
    public XForwardedHeadersInjector() {
        super(null);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange != null && exchange.getSecurityContext() != null
                && exchange.getSecurityContext()
                        .getAuthenticatedAccount() != null) {
            Account a = exchange.getSecurityContext().getAuthenticatedAccount();

            if (a.getPrincipal() != null) {
                exchange.getRequestHeaders()
                        .add(getHeaderForAccountId(),
                                a.getPrincipal().getName());
            }

            if (a instanceof BaseAccount && ((BaseAccount) a).getRoles() != null) {
                ((BaseAccount) a).getRoles()
                        .stream().forEachOrdered(role -> exchange
                        .getRequestHeaders()
                        .add(getHeaderForAccountRoles(), role));

            }
            
            var xfhs = Request.wrap(exchange).getXForwardedHeaders();

            if (xfhs != null) {
                xfhs.entrySet().stream()
                        .forEachOrdered(m -> {

                            m.getValue().stream().forEachOrdered(v
                                    -> exchange.getRequestHeaders()
                                            .add(getHeaderForXH(m.getKey()),
                                                    v));
                        });
            }
        }

        if (getNext() != null) {
            getNext().handleRequest(exchange);
        }
    }

    private HttpString getHeaderForXH(String suffix) {
        return HttpString.tryFromString("X-Forwarded-".concat(suffix));
    }

    private HttpString getHeaderForAccountId() {
        return getHeaderForXH("Account-Id");
    }

    private HttpString getHeaderForAccountRoles() {
        return getHeaderForXH("Account-Roles");
    }
}
