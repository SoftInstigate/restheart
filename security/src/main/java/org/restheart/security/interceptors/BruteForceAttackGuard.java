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
package org.restheart.security.interceptors;

import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.metrics.Metrics.FAILED_AUTH_KEY;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.utils.LogUtils;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import static org.restheart.metrics.Metrics.collectFailedAuthBy;
import static org.restheart.metrics.Metrics.failedAuthHistogramName;
import static org.restheart.metrics.Metrics.xffValue;
import static org.restheart.metrics.Metrics.xffValueRIndex;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;
import com.google.common.net.HttpHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name="bruteForceAttackGuard",
        description = "defends from brute force attacks by returning 429 Too Many Requests when failed auth attempts in last 10 seconds from same ip are more than 50%",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH,
        enabledByDefault = false)
public class BruteForceAttackGuard implements WildcardInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(BruteForceAttackGuard.class);

    private static final MetricRegistry AUTH_METRIC_REGISTRY = SharedMetricRegistries.getOrCreate("AUTH");
    private static int xForwardedForValueFromLast = 0;

    private int maxFailedAttempts = 5;

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() {
        try {
            boolean trustXForwardedFor = arg(config, "trust-x-forwarded-for");

            if (trustXForwardedFor) {
                xForwardedForValueFromLast = arg(config, "x-forwarded-for-value-from-last");

                if (xForwardedForValueFromLast < 0) {
                    LOGGER.warn("x-forwarded-for-value-from-last is negative, set to 0");
                    xForwardedForValueFromLast = 0;
                }

                LOGGER.info("Failed auth requests will be counted on X-Forwarded-For header, tracking the {}th value from the last", xForwardedForValueFromLast);
                collectFailedAuthBy(FAILED_AUTH_KEY.X_FORWARDED_FOR);
                xffValueRIndex(xForwardedForValueFromLast);
            } else {
                LOGGER.info("Failed auth requests will be counted on remote ip");
                collectFailedAuthBy(FAILED_AUTH_KEY.REMOTE_IP);
            }
        } catch(ConfigurationException ce) {
            LOGGER.info("Failed auth requests will be counted on remote ip");
            collectFailedAuthBy(FAILED_AUTH_KEY.REMOTE_IP);
        }

        try {
            this.maxFailedAttempts = arg(config, "max-failed-attempts");
        } catch(ConfigurationException ce) {
            this.maxFailedAttempts = 5;
        }

        LOGGER.info("Requests will be blocked when got more than {} failed attempts in last 10 seconds", maxFailedAttempts);
    }

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        // if failed attempts in last 10 seconds >= maxFailedAttempts, deny access
        var max = authHisto(request).getSnapshot().getMax();
        if (max > this.maxFailedAttempts) {
            logWarning(request.getExchange(), max);
            // this blocks the request authentication
            // with status code 429 TOO_MANY_REQUESTS
            request.blockForTooManyRequests();
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        return !request.isOptions();
    }

    private void logWarning(HttpServerExchange exchange, double mean) {
        var xff = ExchangeAttributes.requestHeader(HttpString.tryFromString(HttpHeaders.X_FORWARDED_FOR)).readAttribute(exchange);
        LogUtils.boxedWarn(LOGGER,
            "A brute force attack might be in progress...",
            "",
            "Got too many of failed auth attempts in last 10 seconds from:",
            "",
            "remote ip: " + ExchangeAttributes.remoteIp().readAttribute(exchange),
            "X-Forwarded-For header: " + xff,
            "X-Forwarded-For tracked value: " + xffValue(xff, xForwardedForValueFromLast),
            "",
            "transport protocol: " + ExchangeAttributes.transportProtocol().readAttribute(exchange),
            "request method: " + ExchangeAttributes.requestMethod().readAttribute(exchange),
            "request url: " + ExchangeAttributes.requestURL().readAttribute(exchange));
    }

    private Histogram authHisto(ServiceRequest<?> request) {
        return AUTH_METRIC_REGISTRY.histogram(failedAuthHistogramName(request.getExchange()), () -> new Histogram(new SlidingTimeWindowArrayReservoir(10, TimeUnit.SECONDS)));
    }
}
