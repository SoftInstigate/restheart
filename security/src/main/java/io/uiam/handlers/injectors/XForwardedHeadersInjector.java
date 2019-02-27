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
import io.uiam.handlers.exchange.ByteArrayRequest;
import io.uiam.plugins.authentication.impl.BaseAccount;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.List;
import java.util.Map;

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
                        .add(getXForwardedAccountIdHeaderName(),
                                a.getPrincipal().getName());
            }

            if (a instanceof BaseAccount && ((BaseAccount) a).getRoles() != null) {
                ((BaseAccount) a).getRoles()
                        .stream().forEachOrdered(role -> exchange
                        .getRequestHeaders()
                        .add(getXForwardedRolesHeaderName(), role));

            }

            Map<String, List<String>> xfhs = ByteArrayRequest.wrap(exchange)
                    .getXForwardedHeaders();

            if (xfhs != null) {
                xfhs.entrySet().stream()
                        .forEachOrdered(m -> {
                            m.getValue().stream().forEachOrdered(v
                                    -> exchange.getRequestHeaders()
                                            .add(getXForwardedHeaderName(m.getKey()),
                                                    v));
                        });
            }
        }

        next(exchange);
    }

    public static HttpString getXForwardedHeaderName(String suffix) {
        return HttpString.tryFromString("X-Forwarded-".concat(suffix));
    }

    public static HttpString getXForwardedAccountIdHeaderName() {
        return getXForwardedHeaderName("Account-Id");
    }

    public static HttpString getXForwardedRolesHeaderName() {
        return getXForwardedHeaderName("Account-Roles");
    }
}
