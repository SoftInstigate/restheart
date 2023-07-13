/*-
 * ========================LICENSE_START=================================
 * restheart-metrics
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

package org.restheart.metrics;

import org.restheart.plugins.Initializer;
import org.restheart.plugins.RegisterPlugin;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import static org.restheart.utils.MetricsUtils.METRICS_REGISTRIES_PREFIX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RegisterPlugin(name="jvmMetricsCollector", description = "registers the JVM metrics", enabledByDefault = false)
public class JvmMetricsCollector implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JvmMetricsCollector.class);

    public void init() {
        var registry = SharedMetricRegistries.getOrCreate(METRICS_REGISTRIES_PREFIX + "/jvm");
        registry.registerAll("jvm mem", new MemoryUsageGaugeSet());
        LOGGER.info("registered VM memory usage metrics");
        registry.registerAll("jvm garbage-collector", new GarbageCollectorMetricSet());
        LOGGER.info("registered Garbage Collections metrics");
    }
}
