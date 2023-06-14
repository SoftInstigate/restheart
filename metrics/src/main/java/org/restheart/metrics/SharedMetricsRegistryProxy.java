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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import java.util.stream.Stream;

public class SharedMetricsRegistryProxy {
    public static final String REGISTRY_PREFIX = "RESTHEART_METRICS_REGISTRY_";
    private static final String DEFAULT_NAME = "DEFAULT_RESTHEART_METRICS_REGISTRY";

    /**
     *
     * @param uri
     * @return
     */
    public boolean isDefault(String uri) {
        return DEFAULT_NAME.equals(uri);
    }

    /**
     *
     * @return
     */
    public MetricRegistry registry() {
        return SharedMetricRegistries.getOrCreate(DEFAULT_NAME);
    }

    /**
     *
     * @param uri
     * @return
     */
    public MetricRegistry registry(String uri) {
        return SharedMetricRegistries.getOrCreate(REGISTRY_PREFIX.concat(uri));
    }

    /**
     *
     * @return
     */
    public Stream<String> registries() {
        return SharedMetricRegistries.names().stream().filter(n -> n.startsWith(REGISTRY_PREFIX));
    }
}
