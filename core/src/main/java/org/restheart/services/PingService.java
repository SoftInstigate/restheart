/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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
package org.restheart.services;

import java.util.Map;

import org.restheart.exchange.ByteArrayRequest;
import org.restheart.exchange.ByteArrayResponse;
import org.restheart.plugins.ByteArrayService;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "ping", description = "simple ping service", secure = false, blocking = false)
public class PingService implements ByteArrayService {
    private static final String VERSION = PingService.class.getPackage().getImplementationVersion();
    private String msg = null;
    private boolean isExtendedResponseEnabled = true;

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void setup() {
        this.msg = argOrDefault(this.config, "msg", "Greetings from RESTHeart!");
        this.isExtendedResponseEnabled = argOrDefault(this.config, "enable-extended-response", true);
    }

    @Override
    public void handle(final ByteArrayRequest request, final ByteArrayResponse response) throws Exception {
        if (request.isGet()) {
            final var pingMessageBuilder = new StringBuilder();
            if (this.isExtendedResponseEnabled) {
                pingMessageBuilder.append("{\"message\": \"")
                    .append(msg)
                    .append("\", \"client_ip\": \"")
                    .append(getClientIp(request.getExchange()))
                    .append("\", \"host\": \"")
                    .append(getHostHeader(request.getExchange()))
                    .append("\", \"version\": \"")
                    .append(VERSION)
                    .append("\"}");
            } else {
                pingMessageBuilder.append("{\"message\": \"")
                    .append(msg)
                    .append("\"}");
            }

            final String pingMessage = pingMessageBuilder.toString();
            response.setContentType("application/json");
            response.setContent(pingMessage.getBytes());
        } else if (request.isOptions()) {
            handleOptions(request);
        } else {
            response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        }
    }

    private String getHostHeader(final HttpServerExchange exchange) {
        return exchange.getRequestHeaders().getFirst("Host");
    }

    private String getClientIp(final HttpServerExchange exchange) {
        // Get the X-Forwarded-For header from the request
        final String forwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // The first IP in X-Forwarded-For is typically the client's IP
            return forwardedFor.split(",")[0].trim();
        } else {
            // Fallback to the remote address from the exchange
            return exchange.getSourceAddress().getAddress().getHostAddress();
        }
    }
}
