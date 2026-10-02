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

import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.metrics.Metrics;
import org.restheart.metrics.Metrics.FAILED_AUTH_KEY;
import org.restheart.metrics.Metrics.XffOverride;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.emails.EmailSender;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.predicate.PredicateParser;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import static org.restheart.metrics.Metrics.collectFailedAuthBy;
import static org.restheart.metrics.Metrics.failedAuthHistogramName;
import static org.restheart.metrics.Metrics.xffValue;
import static org.restheart.metrics.Metrics.xffValueRIndex;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.google.common.net.HttpHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defends against brute force attacks by blocking requests that exceed failed authentication threshold.
 * <p>
 * This interceptor runs at the REQUEST_BEFORE_AUTH intercept point and checks the metrics
 * collected by FailedAuthMetricsCollector. When the number of failed authentication attempts
 * from the same source (IP address or X-Forwarded-For header) exceeds the configured threshold
 * within the sliding time window (10 seconds), it immediately errors the request with a
 * 429 Too Many Requests status, preventing the authentication attempt from even being processed.
 * </p>
 * <p>
 * This preemptive approach prevents attackers from consuming server resources with repeated
 * authentication attempts after they've already exceeded the threshold.
 * </p>
 * <p>
 * Configuration options:
 * <ul>
 *   <li><code>max-failed-attempts</code> - Maximum allowed failed attempts in the time window (default: 5)</li>
 *   <li><code>trust-x-forwarded-for</code> - Whether to track by X-Forwarded-For header (default: false)</li>
 *   <li><code>x-forwarded-for-value-from-last-element</code> - Which value to use from X-Forwarded-For (default: 0, meaning last value);
 *   the name before 9.9.2, <code>x-forwarded-for-value-from-last</code>, is still read</li>
 *   <li><code>x-forwarded-for-overrides</code> - For a server reached through more than one entry, each with its
 *   own chain of proxies: a list of <code>predicate</code> and <code>value-from-last-element</code>, the first
 *   matching predicate wins, the others fall back to <code>x-forwarded-for-value-from-last-element</code>.
 *   The predicate must tell the entries apart by something a client cannot forge, e.g. a host only one
 *   entry forwards</li>
 *   <li><code>notify-email</code> - Where to mail an alarm when a source gets blocked, with the <code>emails</code>
 *   provider configured; at most one mail per source every <code>notify-cooldown-minutes</code> (default: 60)</li>
 * </ul>
 * </p>
 * <p>
 * A blocked source is logged once, on one line that starts with {@code Brute force guard:}, when it
 * crosses the threshold, and once more when it is released, with the requests blocked meanwhile;
 * the requests in between go to DEBUG.
 * </p>
 * <p>
 * <strong>Note:</strong> This interceptor requires FailedAuthMetricsCollector to be enabled
 * to function properly, as it relies on the metrics collected by that interceptor.
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "bruteForceAttackGuard",
        description = "defends from brute force attacks by blocking requests when failed authentication attempts exceed threshold",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH,
        enabledByDefault = false)
public class BruteForceAttackGuard implements WildcardInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(BruteForceAttackGuard.class);

    private static final MetricRegistry AUTH_METRIC_REGISTRY = SharedMetricRegistries.getOrCreate("AUTH");
    private static int xForwardedForValueFromLast = 0;

    private int maxFailedAttempts = 5;

    /** How long a source stays quiet before its block is over and logged as released. */
    static final Duration RELEASE_AFTER = Duration.ofSeconds(30);

    @Inject(value = "emails", required = false)
    private EmailSender emailSender;

    private String notifyEmail;
    private Duration notifyCooldown = Duration.ofMinutes(60);
    private boolean trackedByXff = false;

    /** The sources blocked now, by the value the failures are counted on. */
    private final Map<String, Block> blocks = new ConcurrentHashMap<>();
    /** When an alarm was last mailed, by source, for the cooldown. */
    private final Map<String, Instant> mailed = new ConcurrentHashMap<>();
    private volatile Instant lastSweep = Instant.EPOCH;

    private static final class Block {
        final Instant since = Instant.now();
        final AtomicLong blocked = new AtomicLong(1);
        volatile Instant last = since;
    }

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() {
        try {
            boolean trustXForwardedFor = arg(config, "trust-x-forwarded-for");

            if (trustXForwardedFor) {
                xForwardedForValueFromLast = xffValueFromLast(config);

                if (xForwardedForValueFromLast < 0) {
                    LOGGER.warn("x-forwarded-for-value-from-last is negative, set to 0");
                    xForwardedForValueFromLast = 0;
                }

                LOGGER.info("Failed auth requests will be counted on X-Forwarded-For header, tracking the {}th value from the last", xForwardedForValueFromLast);
                collectFailedAuthBy(FAILED_AUTH_KEY.X_FORWARDED_FOR);
                trackedByXff = true;
                xffValueRIndex(xForwardedForValueFromLast);
                Metrics.xffOverrides(xffOverrides(config, getClass().getClassLoader()));
            } else {
                LOGGER.info("Failed auth requests will be counted on remote ip");
                collectFailedAuthBy(FAILED_AUTH_KEY.REMOTE_IP);
            }
        } catch (ConfigurationException ce) {
            LOGGER.info("Failed auth requests will be counted on remote ip");
            collectFailedAuthBy(FAILED_AUTH_KEY.REMOTE_IP);
        }

        try {
            this.maxFailedAttempts = arg(config, "max-failed-attempts");
        } catch (ConfigurationException ce) {
            this.maxFailedAttempts = 5;
        }

        LOGGER.info("Requests will be blocked when got more than {} failed attempts in last 10 seconds", maxFailedAttempts);

        var email = config == null ? null : config.get("notify-email");
        if (email instanceof String e && !e.isBlank()) {
            if (emailSender != null && emailSender.isEnabled()) {
                this.notifyEmail = e.strip();
                if (config.get("notify-cooldown-minutes") instanceof Number n && n.longValue() > 0) {
                    this.notifyCooldown = Duration.ofMinutes(n.longValue());
                }
                LOGGER.info("Brute force guard: alarms mailed to {}, at most one per source every {} minutes", notifyEmail, notifyCooldown.toMinutes());
            } else {
                LOGGER.warn("Brute force guard: notify-email is set but the emails provider is not configured; alarms go to the log only");
            }
        }
    }

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        // This interceptor runs BEFORE authentication
        // Check if failed attempts from this source exceed the threshold
        var histogram = authHisto(request);
        var failedAttempts = histogram.getSnapshot().getMax();

        sweep();

        if (failedAttempts >= this.maxFailedAttempts) {
            blocked(request.getExchange(), failedAttempts);

            // Set the request as in error to stop further processing
            response.setInError(
                    org.restheart.utils.HttpStatus.SC_TOO_MANY_REQUESTS,
                    "Too many failed authentication attempts. Please try again later."
            );
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        return !request.isOptions();
    }

    /**
     * A blocked request: the first of a source logs it and may mail the alarm, the others only count.
     */
    private void blocked(HttpServerExchange exchange, double failures) {
        var source = source(exchange);
        var created = new boolean[1];
        var block = blocks.compute(source, (k, b) -> {
            if (b == null) {
                created[0] = true;
                return new Block();
            }
            b.blocked.incrementAndGet();
            b.last = Instant.now();
            return b;
        });

        if (!created[0]) {
            LOGGER.debug("Brute force guard: still blocking source={} blocked={}", source, block.blocked.get());
            return;
        }

        var line = line(exchange, source, failures);
        LOGGER.warn("Brute force guard: blocking {}", line);
        mail(source, line);
    }

    /** The value the failures are counted on: the tracked X-Forwarded-For value, or the remote ip. */
    private String source(HttpServerExchange exchange) {
        if (!trackedByXff) {
            return ExchangeAttributes.remoteIp().readAttribute(exchange);
        }
        var xff = ExchangeAttributes.requestHeader(HttpString.tryFromString(HttpHeaders.X_FORWARDED_FOR)).readAttribute(exchange);
        return xff == null ? "x-forwarded-for-not-set" : xffValue(xff, xffValueRIndex(exchange));
    }

    /** The facts of a block on one line, key=value, for a grep or a log metric filter. */
    private String line(HttpServerExchange exchange, String source, double failures) {
        var xff = ExchangeAttributes.requestHeader(HttpString.tryFromString(HttpHeaders.X_FORWARDED_FOR)).readAttribute(exchange);
        return "source=" + source
                + " failures=" + (long) failures
                + " window=10s"
                + " tracked-by=" + (trackedByXff ? "x-forwarded-for" : "remote-ip")
                + " remote-ip=" + ExchangeAttributes.remoteIp().readAttribute(exchange)
                + " xff=\"" + (xff == null ? "" : xff) + "\""
                + " method=" + ExchangeAttributes.requestMethod().readAttribute(exchange)
                + " url=" + ExchangeAttributes.requestURL().readAttribute(exchange);
    }

    /**
     * The blocks quiet for {@link #RELEASE_AFTER} are over: each logged as released, with the requests
     * it blocked. At most once every ten seconds, and also the cooldowns expired.
     */
    private void sweep() {
        var now = Instant.now();
        if (now.isBefore(lastSweep.plusSeconds(10))) {
            return;
        }
        lastSweep = now;

        blocks.entrySet().removeIf(e -> {
            var b = e.getValue();
            if (now.isBefore(b.last.plus(RELEASE_AFTER))) {
                return false;
            }
            LOGGER.info("Brute force guard: released source={} blocked={} for={}s",
                    e.getKey(), b.blocked.get(), Duration.between(b.since, b.last).toSeconds());
            return true;
        });

        mailed.entrySet().removeIf(e -> now.isAfter(e.getValue().plus(notifyCooldown)));
    }

    /** The alarm by mail, when configured: at most one per source in the cooldown, off the request thread. */
    private void mail(String source, String line) {
        if (notifyEmail == null) {
            return;
        }

        var now = Instant.now();
        var previous = mailed.putIfAbsent(source, now);
        if (previous != null) {
            return;
        }

        var host = hostName();
        var subject = "RESTHeart on " + host + ": blocking a possible brute force attack from " + source;
        var body = "<p>RESTHeart on <b>" + escape(host) + "</b> got " + maxFailedAttempts + " or more failed "
                + "authentications in 10 seconds from <b>" + escape(source) + "</b>, and answers its requests with "
                + "429 Too Many Requests until they stop.</p>"
                + "<pre style=\"font-size:12px;white-space:pre-wrap\">" + escape(line) + "</pre>"
                + "<p>The log has the same line, after <code>Brute force guard: blocking</code>, and a "
                + "<code>Brute force guard: released</code> one when the block is over. No other mail about this "
                + "source for " + notifyCooldown.toMinutes() + " minutes.</p>";

        emailSender.sendEmailAsync(notifyEmail, notifyEmail, subject, body);
        LOGGER.info("Brute force guard: alarm for source={} mailed to {}", source, notifyEmail);
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Throwable t) {
            return "unknown host";
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private Histogram authHisto(ServiceRequest<?> request) {
        return AUTH_METRIC_REGISTRY.histogram(failedAuthHistogramName(request.getExchange()), () -> new Histogram(new SlidingTimeWindowArrayReservoir(10, TimeUnit.SECONDS)));
    }

    /**
     * The element of X-Forwarded-For to count on, from the last. The documented key is
     * {@code x-forwarded-for-value-from-last-element}; until 9.9.1 the guard read
     * {@code x-forwarded-for-value-from-last} only, so a configuration written as documented made
     * it count on the remote ip, the proxy's for every client. Both are read, the documented one
     * first; without either the last element, as the documentation says.
     */
    static int xffValueFromLast(Map<String, Object> config) {
        if (config != null && config.get("x-forwarded-for-value-from-last-element") instanceof Number n) {
            return n.intValue();
        }

        if (config != null && config.get("x-forwarded-for-value-from-last") instanceof Number n) {
            return n.intValue();
        }

        return 0;
    }

    /**
     * The {@code x-forwarded-for-overrides}, in order. An override that cannot be read is left out
     * with an error in the log: its requests fall back to {@code x-forwarded-for-value-from-last-element}.
     */
    static List<XffOverride> xffOverrides(Map<String, Object> config, ClassLoader classLoader) {
        var ret = new ArrayList<XffOverride>();
        if (config == null || !(config.get("x-forwarded-for-overrides") instanceof List<?> overrides)) {
            return ret;
        }

        for (var o : overrides) {
            if (o instanceof Map<?, ?> m
                    && m.get("predicate") instanceof String predicate
                    && m.get("value-from-last-element") instanceof Number valueFromLast
                    && valueFromLast.intValue() >= 0) {
                try {
                    ret.add(new XffOverride(PredicateParser.parse(predicate, classLoader), valueFromLast.intValue()));
                    LOGGER.info("Failed auth requests matching {} will be counted on the {}th X-Forwarded-For value from the last", predicate, valueFromLast);
                } catch (Throwable t) {
                    LOGGER.error("x-forwarded-for-overrides: wrong predicate {}, left out", predicate, t);
                }
            } else {
                LOGGER.error("x-forwarded-for-overrides: an override needs a predicate and a value-from-last-element >= 0, left out: {}", o);
            }
        }

        return ret;
    }
}
