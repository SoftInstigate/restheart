package org.restheart.utils;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

/**
 * A proxy to the shared metrics registry, mainly to make unit testing easier (extend and inject to object-under-test)
 */
public class SharedMetricRegistryProxy {
    private static final MetricRegistry defaultRegistry = SharedMetricRegistries.getOrCreate("default");
    public MetricRegistry registry() {
        return defaultRegistry;
    }
    public MetricRegistry registry(String dbName) {
        return SharedMetricRegistries.getOrCreate(dbName);
    }
    public MetricRegistry registry(String dbName, String collectionName) {
        return SharedMetricRegistries.getOrCreate(dbName + "/" + collectionName);
    }
}
