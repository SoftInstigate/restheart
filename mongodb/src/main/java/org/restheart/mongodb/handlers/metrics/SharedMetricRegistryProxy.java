package org.restheart.mongodb.handlers.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import java.util.stream.Stream;

/**
 * A proxy to the shared metrics registry, mainly to make unit testing easier (extend and inject to object-under-test)
 */
public class SharedMetricRegistryProxy {

    private static final String DEFAULT_NAME = "default";

    /**
     *
     */
    public SharedMetricRegistryProxy() {

        // initialize default metrics registry name, if not already set
        if(SharedMetricRegistries.tryGetDefault() == null) {
            SharedMetricRegistries.setDefault(DEFAULT_NAME);
        }
    }

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
        return SharedMetricRegistries.tryGetDefault();
    }

    /**
     *
     * @param dbName
     * @return
     */
    public MetricRegistry registry(String dbName) {
        return SharedMetricRegistries.getOrCreate(dbName);
    }

    /**
     *
     * @param dbName
     * @param collectionName
     * @return
     */
    public MetricRegistry registry(String dbName, String collectionName) {
        return SharedMetricRegistries.getOrCreate(dbName + "/" + collectionName);
    }

    /**
     *
     * @return
     */
    public Stream<String> registries() {
        return SharedMetricRegistries.names().stream();
    }
}
