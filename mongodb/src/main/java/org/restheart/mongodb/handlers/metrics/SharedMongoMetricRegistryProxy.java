/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.handlers.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import java.util.stream.Stream;
public class SharedMongoMetricRegistryProxy {
    public static final String REGISTRY_PREFIX = "MONGO_METRICS_REGISTRY_";
    private static final String DEFAULT_NAME = "MONGO_METRICS_REGISTRY_0_DEFAULT";

    /**
     *
     * @param databaseName
     * @return
     */
    public boolean isDefault(String databaseName) {
        return DEFAULT_NAME.equals(databaseName);
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
     * @param dbName
     * @return
     */
    public MetricRegistry registry(String dbName) {
        return SharedMetricRegistries.getOrCreate(REGISTRY_PREFIX.concat(dbName));
    }

    /**
     *
     * @param dbName
     * @param collectionName
     * @return
     */
    public MetricRegistry registry(String dbName, String collectionName) {
        return SharedMetricRegistries.getOrCreate(REGISTRY_PREFIX.concat(dbName).concat("/").concat(collectionName));
    }

    /**
     *
     * @return
     */
    public Stream<String> registries() {
        return SharedMetricRegistries.names().stream().filter(n -> n.startsWith(REGISTRY_PREFIX));
    }
}
