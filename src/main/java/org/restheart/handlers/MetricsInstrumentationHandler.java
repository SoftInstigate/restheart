package org.restheart.handlers;

import com.google.common.annotations.VisibleForTesting;

import com.codahale.metrics.MetricRegistry;

import org.restheart.Bootstrapper;
import org.restheart.Configuration;
import org.restheart.utils.SharedMetricRegistryProxy;

import java.util.concurrent.TimeUnit;

import io.undertow.server.HttpServerExchange;

import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.COLLECTION;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.DATABASE;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.ROOT;

/**
 * Handler to measure calls to restheart.
 * Will only take actions if metrics should be gathered (config option "metrics-gathering-level" > OFF)
 */
public class MetricsInstrumentationHandler extends PipedHttpHandler {

    public MetricsInstrumentationHandler(PipedHttpHandler next) {
        super(next);
    }

    /**Writable in unit tests to make testing easier*/
    @VisibleForTesting
    Configuration configuration = Bootstrapper.getConfiguration();

    @VisibleForTesting
    SharedMetricRegistryProxy metrics = new SharedMetricRegistryProxy();

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        final long requestStartTime = context.getRequestStartTime();

        exchange.addExchangeCompleteListener((httpServerExchange, nextListener) -> {
            addMetrics(requestStartTime, exchange, context);

            nextListener.proceed();
        });

        if (!exchange.isResponseComplete() && getNext() != null) {
            next(exchange, context);
        }
    }

    private void addDefaultMetrics(MetricRegistry registry, long duration, HttpServerExchange exchange, RequestContext context) {
        registry.timer(context.getType().toString() + "." + context.getMethod().toString())
            .update(duration, TimeUnit.MILLISECONDS);
        registry.timer(context.getType().toString() + "." + context.getMethod().toString() + "." + exchange.getStatusCode())
            .update(duration, TimeUnit.MILLISECONDS);
        registry.timer(context.getType().toString() + "." + context.getMethod().toString() + "." + (exchange.getStatusCode() / 100) + "xx")
            .update(duration, TimeUnit.MILLISECONDS);
    }

    private boolean isFilledAndNotMetrics(String dbOrCollectionName) {
        return dbOrCollectionName != null && !dbOrCollectionName.equalsIgnoreCase(RequestContext._METRICS);
    }

    private void addMetrics(long startTime, HttpServerExchange exchange, RequestContext context) {
        if (configuration.gatheringAboveOrEqualToLevel(ROOT)) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            addDefaultMetrics(metrics.registry(), duration, exchange, context);

            if (isFilledAndNotMetrics(context.getDBName()) && configuration.gatheringAboveOrEqualToLevel(DATABASE)) {
                final MetricRegistry dbRegistry = metrics.registry(context.getDBName());
                addDefaultMetrics(dbRegistry, duration, exchange, context);

                if (isFilledAndNotMetrics(context.getCollectionName()) && configuration.gatheringAboveOrEqualToLevel(COLLECTION)) {
                    final MetricRegistry collectionRegistry = metrics.registry(context.getDBName(), context.getCollectionName());
                    addDefaultMetrics(collectionRegistry, duration, exchange, context);
                }
            }
        }
    }
}
