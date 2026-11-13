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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.restheart.metrics.MetricNameAndLabels;
import org.restheart.metrics.Metrics;

class MqttMetricsCollectorTest {

    @Test
    void registersGaugesReadingLiveFromRouterStats() {
        var router = mock(MqttMessageRouter.class);
        when(router.getStats()).thenReturn(new MqttMessageRouter.RouterStats(3, 5, 2, 100L, 7L));

        var collector = new MqttMetricsCollector(router);
        collector.init();

        assertEquals(3, Metrics.getGaugeValue(MetricNameAndLabels.of("mqtt_router_topic_filters")));
        assertEquals(5, Metrics.getGaugeValue(MetricNameAndLabels.of("mqtt_router_listeners")));
        assertEquals(2, Metrics.getGaugeValue(MetricNameAndLabels.of("mqtt_router_cached_messages")));
        assertEquals(100L, Metrics.getGaugeValue(MetricNameAndLabels.of("mqtt_router_messages_received")));
        assertEquals(7L, Metrics.getGaugeValue(MetricNameAndLabels.of("mqtt_router_messages_dropped")));
    }
}
