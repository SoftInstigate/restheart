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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.restheart.plugins.security.Authenticator;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingTimeWindowArrayReservoir;

/**
 * The pruning of the failed auth histograms keeps the sources that are failing now.
 */
public class FailedAuthMetricsCollectorTest {
    private static Histogram window() {
        return new Histogram(new SlidingTimeWindowArrayReservoir(10, TimeUnit.SECONDS));
    }

    @Test
    public void pruneKeepsTheSourcesFailingNow() {
        var registry = new MetricRegistry();
        var busy = MetricRegistry.name(Authenticator.class, "failed-auth-x-forwarded-for", "203.0.113.7");
        var idle = MetricRegistry.name(Authenticator.class, "failed-auth-x-forwarded-for", "198.51.100.9");

        var counting = registry.histogram(busy, FailedAuthMetricsCollectorTest::window);
        // one entry per failure, as Metrics.failedAuth writes them
        for (var i = 1; i <= 99; i++) {
            counting.update(1);
        }
        registry.histogram(idle, FailedAuthMetricsCollectorTest::window);

        FailedAuthMetricsCollector.pruneIdle(registry);

        assertTrue(registry.getHistograms().containsKey(busy), "a source failing now keeps its count");
        assertEquals(99, registry.getHistograms().get(busy).getSnapshot().size());
        assertFalse(registry.getHistograms().containsKey(idle), "a source with an empty window is removed");
    }
}
