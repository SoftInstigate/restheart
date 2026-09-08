/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
 * %%
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
 * =========================LICENSE_END==================================
 */
package org.restheart.plugins.security;

import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import org.restheart.security.BaseAccount;

import io.undertow.server.HttpServerExchange;

/**
 * A plain, protocol-agnostic description of one operation that needs authorizing — everything a
 * request-shaped {@link Authorizer} (see {@link DescriptorAwareAuthorizer}) might reasonably need,
 * without being tied to any particular transport.
 *
 * <p>Produced by {@link org.restheart.plugins.Service#operationsToAuthorize(HttpServerExchange)}
 * — the default implementation describes the real incoming exchange as-is via {@link #of}; a
 * single-endpoint protocol service (e.g. GraphQL, MCP — see restheart#722) overrides it to
 * describe the real, underlying REST-shaped operation its request actually performs instead,
 * which its own method/path never reveals.
 *
 * <p>No URI fragment: HTTP never transmits it to the server, so it would always be empty for
 * {@link #of} and isn't worth carrying.
 *
 * @param principal the authenticated account performing the operation, or {@code null} if
 *                  unauthenticated
 * @param method the HTTP method (e.g. {@code GET})
 * @param path the request path
 * @param queryParameters query string parameters, name to values
 * @param headers request headers, name to values
 * @param cookies cookies, name to value
 * @param remoteAddress the client's address, or {@code null} if not applicable
 * @param scheme the request scheme (e.g. {@code https}), or {@code null} if not applicable
 */
public record RequestDescriptor(
        BaseAccount principal,
        String method,
        String path,
        Map<String, Deque<String>> queryParameters,
        Map<String, Deque<String>> headers,
        Map<String, String> cookies,
        String remoteAddress,
        String scheme) {

    /** Identity: describes the real incoming exchange as-is — the default case for every ordinary REST service. */
    public static RequestDescriptor of(HttpServerExchange exchange) {
        var headers = new LinkedHashMap<String, Deque<String>>();
        exchange.getRequestHeaders().forEach(hv -> headers.put(hv.getHeaderName().toString(), hv));

        var cookies = new LinkedHashMap<String, String>();
        exchange.getRequestCookies().forEach((name, cookie) -> cookies.put(name, cookie.getValue()));

        var peerAddress = exchange.getSourceAddress();

        var securityContext = exchange.getSecurityContext();
        var account = securityContext == null ? null : securityContext.getAuthenticatedAccount();

        return new RequestDescriptor(
                account instanceof BaseAccount ba ? ba : null,
                exchange.getRequestMethod().toString(),
                exchange.getRequestPath(),
                exchange.getQueryParameters(),
                headers,
                cookies,
                peerAddress == null ? null : peerAddress.getAddress() == null ? null : peerAddress.getAddress().getHostAddress(),
                exchange.getRequestScheme());
    }
}
