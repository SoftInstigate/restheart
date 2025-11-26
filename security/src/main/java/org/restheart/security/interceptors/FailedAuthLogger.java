/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2025 SoftInstigate
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
package org.restheart.security.interceptors;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.HttpHeaders;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Example interceptor that logs failed authentication attempts.
 * This demonstrates the use of the REQUEST_AFTER_FAILED_AUTH intercept point.
 *
 * When enabled, this interceptor logs details about each failed authentication
 * attempt including:
 * - Remote IP address
 * - X-Forwarded-For header value
 * - Request method and URL
 * - User-Agent header
 * - Attempted username (if available in Authorization header)
 *
 * This can be useful for:
 * - Security monitoring and auditing
 * - Identifying brute force attack patterns
 * - Collecting metrics on authentication failures
 * - Alerting on suspicious activity
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
    name = "failedAuthLogger",
    description = "Logs details about failed authentication attempts for security monitoring",
    interceptPoint = InterceptPoint.REQUEST_AFTER_FAILED_AUTH,
    enabledByDefault = false
)
public class FailedAuthLogger implements WildcardInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(FailedAuthLogger.class);

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        var exchange = request.getExchange();

        // Gather information about the failed authentication attempt
        var remoteIp = ExchangeAttributes.remoteIp().readAttribute(exchange);
        var xff = getHeaderValue(exchange, HttpHeaders.X_FORWARDED_FOR);
        var userAgent = getHeaderValue(exchange, HttpHeaders.USER_AGENT);
        var requestMethod = ExchangeAttributes.requestMethod().readAttribute(exchange);
        var requestUrl = ExchangeAttributes.requestURL().readAttribute(exchange);
        var attemptedUsername = extractUsername(exchange);

        // Log the failed authentication attempt
        if (attemptedUsername != null) {
            LOGGER.warn("Failed authentication attempt - User: {}, Remote IP: {}, X-Forwarded-For: {}, Method: {}, URL: {}, User-Agent: {}",
                attemptedUsername, remoteIp, xff, requestMethod, requestUrl, userAgent);
        } else {
            LOGGER.warn("Failed authentication attempt - Remote IP: {}, X-Forwarded-For: {}, Method: {}, URL: {}, User-Agent: {}",
                remoteIp, xff, requestMethod, requestUrl, userAgent);
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        // Apply to all failed authentication requests except OPTIONS
        return !request.isOptions();
    }

    /**
     * Helper method to safely read a header value
     */
    private String getHeaderValue(HttpServerExchange exchange, String headerName) {
        var value = ExchangeAttributes.requestHeader(HttpString.tryFromString(headerName))
            .readAttribute(exchange);
        return value != null ? value : "N/A";
    }

    /**
     * Attempts to extract the username from the Authorization header.
     * This works for Basic authentication. For other authentication mechanisms,
     * this may not extract the username.
     *
     * @param exchange The HTTP server exchange
     * @return The attempted username or null if not available
     */
    private String extractUsername(HttpServerExchange exchange) {
        var authHeader = ExchangeAttributes.requestHeader(HttpString.tryFromString(HttpHeaders.AUTHORIZATION))
            .readAttribute(exchange);

        if (authHeader == null) {
            return null;
        }

        // Handle Basic authentication
        if (authHeader.startsWith("Basic ")) {
            try {
                var base64Credentials = authHeader.substring("Basic ".length()).trim();
                var credentials = new String(java.util.Base64.getDecoder().decode(base64Credentials));
                var parts = credentials.split(":", 2);
                return parts.length > 0 ? parts[0] : null;
            } catch (Exception e) {
                // If we can't decode, just return null
                return null;
            }
        }

        // For other auth types (Bearer, etc.), we can't easily extract the username
        // without accessing the security context or auth mechanism internals
        return null;
    }
}
