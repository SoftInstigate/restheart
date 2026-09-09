/*-
 * ========================LICENSE_START=================================
 * restheart-mqtt
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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

package org.restheart.mqtt;

import org.restheart.metrics.MetricNameAndLabels;
import org.restheart.metrics.Metrics;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.RegisterPlugin;

/**
 * Exposes {@link MqttMessageRouter.RouterStats} as Prometheus-compatible gauges via
 * {@link Metrics}.
 * <p>
 * Registers five gauges - {@code mqtt_router_topic_filters}, {@code mqtt_router_listeners},
 * {@code mqtt_router_cached_messages}, {@code mqtt_router_messages_received} and
 * {@code mqtt_router_messages_dropped} - each reading {@link MqttMessageRouter#getStats()} fresh
 * at scrape time.
 * </p>
 * <p>
 * Note: since {@code mqtt-router} is a required injection, this plugin is automatically
 * disabled by RESTHeart's plugin registry whenever {@code mqtt-router} itself is disabled.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-metrics-collector",
    description = "Registers mqtt router metrics with restheart-metrics",
    enabledByDefault = true,
    initPoint = InitPoint.AFTER_STARTUP
)
public class MqttMetricsCollector implements Initializer {

    @Inject("mqtt-router")
    private MqttMessageRouter router;

    /**
     * Default constructor used by RESTHeart plugin instantiation.
     */
    public MqttMetricsCollector() {
    }

    /**
     * Package-private constructor for unit tests, allowing a test double for the router to be
     * supplied without going through {@link Inject} field injection.
     *
     * @param router the router whose stats are exposed as gauges
     */
    MqttMetricsCollector(MqttMessageRouter router) {
        this.router = router;
    }

    @Override
    public void init() {
        Metrics.registerGauge(
            MetricNameAndLabels.of("mqtt_router_topic_filters"),
            () -> router.getStats().getTopicFilters()
        );

        Metrics.registerGauge(
            MetricNameAndLabels.of("mqtt_router_listeners"),
            () -> router.getStats().getTotalListeners()
        );

        Metrics.registerGauge(
            MetricNameAndLabels.of("mqtt_router_cached_messages"),
            () -> router.getStats().getCachedMessages()
        );

        Metrics.registerGauge(
            MetricNameAndLabels.of("mqtt_router_messages_received"),
            () -> router.getStats().getMessagesReceived()
        );

        Metrics.registerGauge(
            MetricNameAndLabels.of("mqtt_router_messages_dropped"),
            () -> router.getStats().getMessagesDropped()
        );
    }
}
