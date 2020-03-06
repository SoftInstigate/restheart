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
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.BsonRequest;
import static org.restheart.handlers.exchange.ExchangeKeys._METRICS;

/**
 * Handler to measure calls to restheart. Will only take actions if metrics
 * should be gathered (config option "metrics-gathering-level" > OFF)
 */
public class MetricsInstrumentationHandler extends PipelinedHandler {

    @VisibleForTesting
    static boolean isFilledAndNotMetrics(String dbOrCollectionName) {
        return dbOrCollectionName != null
                && !dbOrCollectionName.trim().isEmpty()
                && !dbOrCollectionName.equalsIgnoreCase(_METRICS);
    }

    /**
     * Writable in unit tests to make testing easier
     */
    @VisibleForTesting
    Configuration configuration = Bootstrapper.getConfiguration();

    @VisibleForTesting
    SharedMetricRegistryProxy metrics = new SharedMetricRegistryProxy();

    /**
     *
     */
    public MetricsInstrumentationHandler() {
        this(null);
    }
    
    /**
     *
     * @param next
     */
    public MetricsInstrumentationHandler(PipelinedHandler next) {
        super(next);
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var request = BsonRequest.wrap(exchange);
        
        final long requestStartTime = request.getRequestStartTime();

        if (!exchange.isComplete()) {
            exchange.addExchangeCompleteListener((httpServerExchange, nextListener) -> {
                addMetrics(requestStartTime, httpServerExchange);

                nextListener.proceed();
            });
        }

        if (!exchange.isResponseComplete() && getNext() != null) {
            next(exchange);
        }
    }

    private void addDefaultMetrics(MetricRegistry registry, long duration, HttpServerExchange exchange) {
        var request = BsonRequest.wrap(exchange);
        
        registry.timer(request.getType().toString() + "." + request.getMethod().toString())
                .update(duration, TimeUnit.MILLISECONDS);
        registry.timer(request.getType().toString() + "." + request.getMethod().toString() + "." + exchange.getStatusCode())
                .update(duration, TimeUnit.MILLISECONDS);
        registry.timer(request.getType().toString() + "." + request.getMethod().toString() + "." + (exchange.getStatusCode() / 100) + "xx")
                .update(duration, TimeUnit.MILLISECONDS);
    }

    @VisibleForTesting
    void addMetrics(long startTime, HttpServerExchange exchange) {
        if (configuration.gatheringAboveOrEqualToLevel(ROOT)) {
            var request = BsonRequest.wrap(exchange);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            addDefaultMetrics(metrics.registry(), duration, exchange);

            if (isFilledAndNotMetrics(request.getDBName()) && configuration.gatheringAboveOrEqualToLevel(DATABASE)) {
                final MetricRegistry dbRegistry = metrics.registry(request.getDBName());
                addDefaultMetrics(dbRegistry, duration, exchange);

                if (isFilledAndNotMetrics(request.getCollectionName()) && configuration.gatheringAboveOrEqualToLevel(COLLECTION)) {
                    final MetricRegistry collectionRegistry = metrics.registry(request.getDBName(), request.getCollectionName());
                    addDefaultMetrics(collectionRegistry, duration, exchange);
                }
            }
        }
    }
}
