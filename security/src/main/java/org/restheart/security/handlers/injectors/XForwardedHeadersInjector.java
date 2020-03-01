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
package org.restheart.security.handlers.injectors;

import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.util.List;
import java.util.Map;
import org.restheart.handlers.exchange.ByteArrayRequest;
import org.restheart.security.handlers.PipedHttpHandler;
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

            // remove sensitive X-Forwarded headers from request
            // to avoid clients to control headers such as X-Forwarded-Account-Id
            exchange.getRequestHeaders().remove(getXForwardedAccountIdHeaderName());
            exchange.getRequestHeaders().remove(getXForwardedRolesHeaderName());

            Map<String, List<String>> xfhs = ByteArrayRequest.wrap(exchange)
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
