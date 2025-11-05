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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.handlers.CORSHandler;
import org.restheart.handlers.PipelinedHandler;
import static org.restheart.metrics.Metrics.failedAuthHistogramName;
import org.restheart.plugins.security.Authenticator;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Slf4jReporter.LoggingLevel;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

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
    private static final MetricRegistry AUTH_METRIC_REGISTRY = SharedMetricRegistries.getOrCreate("AUTH");

    private static final String BLOCK_AUTH_ERR_MSG = "Request authentication was blocked";
    private static final String FAILED_AUTH_METRIC_PREFIX = "failed-auth-";

    static {
        if (LOGGER.isTraceEnabled()) {
            Slf4jReporter
                .forRegistry(AUTH_METRIC_REGISTRY)
                .outputTo(LOGGER)
                .filter((name, metric) ->
                    name.startsWith(MetricRegistry.name(Authenticator.class, FAILED_AUTH_METRIC_PREFIX))
                    && metric instanceof Histogram hist
                    && hist.getSnapshot().getMax() > 0)
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
        var sc = exchange.getSecurityContext();

        if (Request.of(exchange).isBlockForTooManyRequests()) {
            // if the request has been blocked because of too many requests
            // return an error
            var remoteIp = ExchangeAttributes.remoteIp().readAttribute(exchange);
            LOGGER.warn(BLOCK_AUTH_ERR_MSG, remoteIp);

            LOGGER.debug("AUTHENTICATION BLOCKED");

            // add CORS headers
            CORSHandler.injectAccessControlAllowHeaders(exchange);
            // set status code and end exchange
            exchange.setStatusCode(HttpStatus.SC_TOO_MANY_REQUESTS);
            fastEndExchange(exchange);
        } else if (sc.authenticate() && (!sc.isAuthenticationRequired() || sc.isAuthenticated())) {
            // 1 authentication is always attempted
            // 2 requests fails if and only if authentication fails
            //   and authentication is required by all enabled authorizers,
            //   since an authorizer that does not require authentication
            //   might authorize the request even if authentication failed

            if (sc.isAuthenticated()) {
                LOGGER.debug("AUTHENTICATION COMPLETED - User: {}", sc.getAuthenticatedAccount().getPrincipal().getName());
            } else {
                LOGGER.debug("AUTHENTICATION COMPLETED - Anonymous");
            }

            if (!exchange.isComplete()) {
                next(exchange);
            }
        } else {
            LOGGER.debug("AUTHENTICATION FAILED");

            // add CORS headers
            CORSHandler.injectAccessControlAllowHeaders(exchange);
            // update failed auth metrics
            updateFailedAuthMetrics(exchange);
            // set status code and end exchange
            Response.of(exchange).setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            fastEndExchange(exchange);
        }
    }

    private static final HttpString TRANSFER_ENCODING = HttpString.tryFromString("Transfer-Encoding");

    /**
     * shutdowns the request channel and ends the exchange
     *
     * Closing the request channel prevents delays when handling requests
     * with large data payloads.
     *
     * @param exchange
     * @throws java.io.IOException
     */
    private void fastEndExchange(HttpServerExchange exchange) throws IOException {
        var requestChannel = exchange.getRequestChannel();
        var econding = exchange.getRequestHeaders().get(TRANSFER_ENCODING);

        // shutdown channel only if Transfer-Encoding is not chunked
        // otherwise we get error
        // java.io.IOException: UT000029: Channel was closed mid chunk, if you have attempted to write chunked data you cannot shutdown the channel until after it has all been written
        if (econding == null || !econding.getFirst().startsWith("chunked")) {
            if (requestChannel != null) {
                try {
                    requestChannel.shutdownReads();
                } catch(IOException ie) {
                    LOGGER.debug("ingoring error shutting down reads", ie);
                }
            }
        }

        exchange.endExchange();
    }

    /**
     * Registers stats of failed authentication in dropwizard's
     * slide time window histograms of 10 seconds.
     *
     * For each request, register one histogram whose name contains the remote ip
     * and one with the value of the header X-Forwarded-For, if present.
     *
     * This method updates the histograms with with max+1 for failed ones,
     * so that the max of each histogram is the number of failed authentications
     * in the last 10 seconds.
     *
     * @param exchange
     */
    private void updateFailedAuthMetrics(HttpServerExchange exchange) {
        // update the histo
        _update(AUTH_METRIC_REGISTRY.histogram(failedAuthHistogramName(exchange), () -> new Histogram(new SlidingTimeWindowArrayReservoir(10, TimeUnit.SECONDS))));

        // every 100 failed requests, prune metrics
        tryPruneMetrics();
    }

    private void _update(Histogram histo) {
        histo.update(histo.getSnapshot().getMax()+1);
    }

    /**
     * Cleaup metrics to avoid memory leaks when an attacker sends
     * many requests with rotating ips or X-Forwarded-For headers
     * generating many dropwizard's meters
     */
    private void tryPruneMetrics() {
        var total = AUTH_METRIC_REGISTRY.counter(MetricRegistry.name(Authenticator.class, "_total"));
        total.inc();
        if (total.getCount() % 100 == 0) {
            total.dec(total.getCount());
            LOGGER.trace("Pruning auth metrics");
            AUTH_METRIC_REGISTRY.removeMatching((name, metric) -> name.startsWith(MetricRegistry.name(Authenticator.class, FAILED_AUTH_METRIC_PREFIX)));
        }
    }
}