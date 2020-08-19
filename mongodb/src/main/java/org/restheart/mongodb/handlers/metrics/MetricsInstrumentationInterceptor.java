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
import com.google.common.annotations.VisibleForTesting;
import io.undertow.server.HttpServerExchange;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.RegisterPlugin;

import java.util.concurrent.TimeUnit;

import static org.restheart.exchange.ExchangeKeys._METRICS;
import static org.restheart.mongodb.MongoServiceConfiguration.METRICS_GATHERING_LEVEL.*;

@RegisterPlugin(name = "metricsInstrumentationInterceptor",
        description = "collects metrics for requests handled by mongo service",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH)
public class MetricsInstrumentationInterceptor implements MongoInterceptor {

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
    MongoServiceConfiguration configuration = null;

    @VisibleForTesting
    SharedMetricRegistryProxy metrics = new SharedMetricRegistryProxy();

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var exchange = request.getExchange();

        final long requestStartTime = request.getRequestStartTime();

        if (!exchange.isComplete()) {
            exchange.addExchangeCompleteListener((httpServerExchange, nextListener) -> {
                addMetrics(requestStartTime, httpServerExchange);

                nextListener.proceed();
            });
        }
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        if (configuration == null) {
            configuration = MongoServiceConfiguration.get();
        }

        return configuration != null && request.isHandledBy("mongo");
    }

    private void addDefaultMetrics(MetricRegistry registry, long duration, HttpServerExchange exchange) {
        var request = MongoRequest.of(exchange);
        
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
            var request = MongoRequest.of(exchange);
            
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
