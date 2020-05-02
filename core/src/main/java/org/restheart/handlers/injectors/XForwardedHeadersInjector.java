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
package org.restheart.handlers.injectors;

import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.List;
import java.util.Map;
import org.restheart.exchange.ByteArrayProxyRequest;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.security.plugins.authenticators.BaseAccount;

/**
 * Adds the following X-Forwarded custom headers to the proxied request:
 *
 * 'X-Forwarded-Account-Id', 'X-Forwarded-Account-Roles' and other headers set
 * with Response.addXForwardedHeader()
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 */
public class XForwardedHeadersInjector extends PipelinedHandler {

    public static HttpString getXForwardedHeaderName(String suffix) {
        return HttpString.tryFromString("X-Forwarded-".concat(suffix));
    }

    public static HttpString getXForwardedAccountIdHeaderName() {
        return getXForwardedHeaderName("Account-Id");
    }

    public static HttpString getXForwardedRolesHeaderName() {
        return getXForwardedHeaderName("Account-Roles");
    }

    /**
     * Creates a new instance of AccountHeadersInjector
     *
     * @param next
     */
    public XForwardedHeadersInjector(PipelinedHandler next) {
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

            // remove sensitive X-Forwarded headers from request
            // to avoid clients to control headers such as X-Forwarded-Account-Id
            exchange.getRequestHeaders().remove(getXForwardedAccountIdHeaderName());
            exchange.getRequestHeaders().remove(getXForwardedRolesHeaderName());

            Map<String, List<String>> xfhs = ByteArrayProxyRequest.of(exchange)
                    .getXForwardedHeaders();

            if (xfhs != null) {
                xfhs.entrySet().stream()
                        .forEachOrdered(m -> exchange.getRequestHeaders()
                        .remove(m.getKey()));
            }

            // add X-Forwarded headers
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

            if (xfhs != null) {
                xfhs.entrySet().stream()
                        .forEachOrdered(m -> {
                            m.getValue().stream().forEachOrdered(v
                                    -> exchange.getRequestHeaders().put(
                                            getXForwardedHeaderName(m.getKey()),
                                            v));
                        });
            }
        }

        next(exchange);
    }
}
