package org.restheart.handlers.metrics;

import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import io.undertow.server.HttpServerExchange;
import java.util.concurrent.TimeUnit;
import org.restheart.Bootstrapper;
import org.restheart.Configuration;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.COLLECTION;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.DATABASE;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.ROOT;
import org.restheart.db.DatabaseImpl;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;

/**
 * Handler to measure calls to restheart. Will only take actions if metrics
 * should be gathered (config option "metrics-gathering-level" > OFF)
 */
public class MetricsInstrumentationHandler extends PipedHttpHandler {

    @VisibleForTesting
    static boolean isFilledAndNotMetrics(String dbOrCollectionName) {
        return dbOrCollectionName != null
                && !dbOrCollectionName.trim().isEmpty()
                && !dbOrCollectionName.equalsIgnoreCase(RequestContext._METRICS);
    }

    /**
     * Writable in unit tests to make testing easier
     */
    @VisibleForTesting
    Configuration configuration = Bootstrapper.getConfiguration();

    @VisibleForTesting
    SharedMetricRegistryProxy metrics = new SharedMetricRegistryProxy();

    public MetricsInstrumentationHandler(PipedHttpHandler next) {
        super(next);
    }

    @VisibleForTesting
    MetricsInstrumentationHandler(PipedHttpHandler next, DatabaseImpl dbsDAO) {
        super(next, dbsDAO);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        final long requestStartTime = context.getRequestStartTime();

        if (!exchange.isComplete()) {
            exchange.addExchangeCompleteListener((httpServerExchange, nextListener) -> {
                addMetrics(requestStartTime, httpServerExchange, context);

                nextListener.proceed();
            });
        }

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

    @VisibleForTesting
    void addMetrics(long startTime, HttpServerExchange exchange, RequestContext context) {
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
