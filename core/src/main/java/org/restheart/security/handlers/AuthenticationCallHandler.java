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

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.concurrent.TimeUnit;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.codahale.metrics.Slf4jReporter.LoggingLevel;
import static org.restheart.utils.MetricsUtils.unauthHistogramName;

import org.restheart.exchange.Request;
import org.restheart.handlers.CORSHandler;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.plugins.security.Authenticator;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the PipelinedHandler version of
 * io.undertow.security.handlers.AuthenticationCallHandler that is the final
 * {@link HttpHandler} in the security chain, it's purpose is to act as a
 * barrier at the end of the chain to ensure authenticate is called after the
 * mechanisms have been associated with the context and the constraint checked.
 *
 * It also register metrics about failed authentications and blocks requests when
 * the exchange has the attachment BLOCK_AUTH set to true.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthenticationCallHandler extends PipelinedHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationCallHandler.class);
    private static MetricRegistry AUTH_METRIC_REGISTRY = SharedMetricRegistries.getOrCreate("AUTH");

    private static final String BLOCK_AUTH_ERR_MSG = "Request authentication was blocked";

    static {
        if (LOGGER.isTraceEnabled()) {
            Slf4jReporter
                .forRegistry(AUTH_METRIC_REGISTRY)
                .outputTo(LOGGER)
                .filter((name, metric) -> name.startsWith(MetricRegistry.name(Authenticator.class, "unauth")))
                .withLoggingLevel(LoggingLevel.TRACE)
                .build()
                .start(5, TimeUnit.SECONDS);
        }
    }

    public AuthenticationCallHandler(final PipelinedHandler next) {
        super(next);
    }

    /**
     * Only allow the request if successfully authenticated or if
     * authentication is not required.
     *
     * @param exchange
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }

        var sc = exchange.getSecurityContext();

        if (Request.of(exchange).isBlockForTooManyRequests()) {
            // if the request has been blocked because of too many requests
            // return an error
            var remoteIp = ExchangeAttributes.remoteIp().readAttribute(exchange);
            LOGGER.warn(BLOCK_AUTH_ERR_MSG, remoteIp);

            // add CORS headers
            CORSHandler.injectAccessControlAllowHeaders(exchange);
            // set status code and end exchange
            exchange.setStatusCode(HttpStatus.SC_TOO_MANY_REQUESTS);
            exchange.endExchange();
        } else if (sc.authenticate() && (!sc.isAuthenticationRequired() || sc.isAuthenticated())) {
            // 1 authentication is always attempted
            // 2 requests fails if and only if authentication fails
            //   and authentication is required by all enabled authorizers,
            //   since an authorizer that does not require authentication
            //   might authorize the request even if authentication failed

            updateAuthMetrics(exchange, true);

            if (!exchange.isComplete()) {
                next(exchange);
            }
        } else {
            updateAuthMetrics(exchange, false);

            // add CORS headers
            CORSHandler.injectAccessControlAllowHeaders(exchange);
            // set status code and end exchange
            exchange.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            exchange.endExchange();
        }
    }

    /**
     * Registers the percentage of failed authentication in dropwizard's
     * slide time window histograms of 10 seconds.
     *
     * For each request this register one histogram whose name contains the remote ip
     * and one with the value of the header X-Forwarded-For, if present.
     *
     * This method updates the histograms with 1 for each failed authentication
     * and with 0 for successful ones, so that the mean of each histogram is the
     * percentage of the failed authentications in the last 10 seconds.
     *
     * @param exchange
     * @param success
     */
    private void updateAuthMetrics(HttpServerExchange exchange, boolean success) {
        var histoNameWithXFF = unauthHistogramName(exchange, true);

        if (histoNameWithXFF != null) {
            AUTH_METRIC_REGISTRY.histogram(histoNameWithXFF, () -> new Histogram(new SlidingTimeWindowArrayReservoir(10, TimeUnit.SECONDS))).update(success ? 0 : 1);
        }

        AUTH_METRIC_REGISTRY.histogram(unauthHistogramName(exchange, false), () -> new Histogram(new SlidingTimeWindowArrayReservoir(10, TimeUnit.SECONDS))).update(success ? 0 : 1);

        var total = AUTH_METRIC_REGISTRY.counter(MetricRegistry.name(Authenticator.class, "_total"));
        total.inc();

        // every 1000 attemps prune the counters
        // to avoid memory leacks
        if (total.getCount() % 1000 == 0) {
            pruneMetrics();
            total.dec(total.getCount());
        }
    }

    private void pruneMetrics() {
        LOGGER.trace("pruning auth metrics");
        AUTH_METRIC_REGISTRY.removeMatching((name, metric) -> name.startsWith(MetricRegistry.name(Authenticator.class, "unauth-")));
    }
}