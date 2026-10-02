/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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

import java.util.concurrent.TimeUnit;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.metrics.Metrics;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.plugins.security.Authenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Slf4jReporter;
import com.codahale.metrics.Slf4jReporter.LoggingLevel;

import io.undertow.server.HttpServerExchange;


/**
 * Collects metrics for failed authentication attempts.
 * <p>
 * This interceptor runs at the REQUEST_AFTER_FAILED_AUTH intercept point and maintains
 * statistics about failed authentication attempts using Dropwizard metrics. It tracks
 * failures in a sliding time window (10 seconds) to enable detection of brute force attacks.
 * </p>
 * <p>
 * The metrics are stored per source (IP address or X-Forwarded-For header) and can be
 * consumed by other interceptors like BruteForceAttackGuard to implement rate limiting
 * and attack prevention.
 * </p>
 * <p>
 * This interceptor is enabled by default and should run before other security interceptors
 * that need access to failed authentication metrics.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(
        name = "failedAuthMetricsCollector",
        description = "Collects metrics for failed authentication attempts in a sliding time window",
        interceptPoint = InterceptPoint.REQUEST_AFTER_FAILED_AUTH,
        enabledByDefault = true,
        priority = Integer.MAX_VALUE // Run first to ensure metrics are available for other interceptors
)
public class FailedAuthMetricsCollector implements WildcardInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(FailedAuthMetricsCollector.class);

    private static final MetricRegistry AUTH_METRIC_REGISTRY = SharedMetricRegistries.getOrCreate("AUTH");
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

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        // Only update metrics for authentication failures (401), not authorization failures (403)
        // Authorization failures are permission issues, not brute force attack attempts
        var statusCode = response.getStatusCode();
        if (statusCode < 0 || statusCode == 401) {
            // Status code -1 means not set yet (will become 401)
            // Status code 401 is Unauthorized (authentication failure)
            updateFailedAuthMetrics(request.getExchange());
        }
        // If status code is 403 (Forbidden), it's an authorization failure - don't count it
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        // Apply to all failed authentication/authorization requests except OPTIONS
        return !request.isOptions();
    }

    /**
     * Counts the failure for the source of the request, the remote ip or the tracked value of
     * X-Forwarded-For: one entry in its 10 seconds window, see {@link Metrics#failedAuth}.
     *
     * @param exchange
     */
    private void updateFailedAuthMetrics(HttpServerExchange exchange) {
        Metrics.failedAuth(exchange);

        // every 100 failed requests, prune metrics
        tryPruneMetrics();
    }

    /**
     * Cleanup metrics to avoid memory leaks when an attacker sends
     * many requests with rotating ips or X-Forwarded-For headers
     * generating many dropwizard's meters.
     *
     * Every 100 failed requests, the histograms with nothing in their window are removed:
     * the sources quiet for the last 10 seconds. The others are kept, or a source would
     * restart from zero every hundred failures overall, and a threshold of 100 or more
     * could never be reached.
     */
    private void tryPruneMetrics() {
        var total = AUTH_METRIC_REGISTRY.counter(MetricRegistry.name(Authenticator.class, "_total"));
        total.inc();
        if (total.getCount() % 100 == 0) {
            total.dec(total.getCount());
            LOGGER.trace("Pruning auth metrics");
            pruneIdle(AUTH_METRIC_REGISTRY);
        }
    }

    /** Removes the failed auth histograms whose 10 seconds window is empty. */
    static void pruneIdle(MetricRegistry registry) {
        registry.removeMatching((name, metric) ->
                name.startsWith(MetricRegistry.name(Authenticator.class, FAILED_AUTH_METRIC_PREFIX))
                        && metric instanceof Histogram histogram
                        && histogram.getSnapshot().size() == 0);
    }
}
