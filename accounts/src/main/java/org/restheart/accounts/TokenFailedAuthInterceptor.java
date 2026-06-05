package org.restheart.accounts;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.metrics.Metrics;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Feeds token-validation failures from restheart-accounts endpoints into the
 * shared {@code AUTH} metric registry so that {@code bruteForceAttackGuard} can
 * detect and block repeated attempts.
 *
 * <h2>Covered endpoints and failure signals</h2>
 * <ul>
 *   <li>{@code PATCH /auth/activate} — 401 response (invalid / expired invite token)</li>
 *   <li>{@code PATCH /auth/reset-password} — 401 response (invalid / expired reset token)</li>
 *   <li>{@code GET  /auth/verify} — 302 redirect whose {@code Location} contains
 *       {@code error=invalid_token} or {@code error=token_expired}
 *       (browser-facing endpoint; redirect behaviour is intentional and must be preserved)</li>
 * </ul>
 *
 * <p>Structural errors (missing fields, malformed JSON) continue to return 400 and are
 * intentionally NOT counted here — only genuine token-guess failures are tracked.</p>
 *
 * <p>The update logic mirrors {@code FailedAuthMetricsCollector} exactly:
 * it uses the same {@code AUTH} shared registry and the same histogram name derived from
 * {@link Metrics#failedAuthHistogramName}, so the counter read by
 * {@code BruteForceAttackGuard} at {@code REQUEST_BEFORE_AUTH} stays accurate.</p>
 */
@RegisterPlugin(
        name             = "tokenFailedAuthInterceptor",
        description      = "Counts token-validation failures into the AUTH metric registry for bruteForceAttackGuard",
        interceptPoint   = InterceptPoint.RESPONSE,
        enabledByDefault = false)
public class TokenFailedAuthInterceptor implements WildcardInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenFailedAuthInterceptor.class);

    private static final MetricRegistry AUTH_REGISTRY = SharedMetricRegistries.getOrCreate("AUTH");

    private static final Set<String> JSON_TOKEN_PATHS = Set.of(
            "/auth/activate",
            "/auth/reset-password"
    );

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        if (request.isOptions()) return false;
        var path = request.getPath();
        var status = response.getStatusCode();

        // JSON endpoints: count 401 responses
        if (JSON_TOKEN_PATHS.contains(path)) {
            return status == 401;
        }

        // Browser-facing verify endpoint: count redirects to error pages
        if ("/auth/verify".equals(path) && status == 302) {
            var location = response.getHeader("Location");
            return location != null
                    && (location.contains("error=invalid_token") || location.contains("error=token_expired"));
        }

        return false;
    }

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) {
        var histoName = Metrics.failedAuthHistogramName(request.getExchange());
        var histo = AUTH_REGISTRY.histogram(histoName,
                () -> new Histogram(new SlidingTimeWindowArrayReservoir(10, TimeUnit.SECONDS)));
        histo.update(histo.getSnapshot().getMax() + 1);

        LOGGER.debug("Token failure counted for bruteForceAttackGuard — path={} status={}",
                request.getPath(), response.getStatusCode());
    }
}
