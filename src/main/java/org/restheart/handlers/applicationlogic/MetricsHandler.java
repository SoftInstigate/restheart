/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.handlers.applicationlogic;

import com.google.common.annotations.VisibleForTesting;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.restheart.Bootstrapper;
import org.restheart.Configuration;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.METHOD;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.SharedMetricRegistryProxy;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.COLLECTION;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.DATABASE;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.ROOT;

/**
 * A handler for dropwizard.io metrics that can return both default metrics JSON and the prometheus format.
 *
 * @author Lena Brüder {@literal <brueder@e-spirit.com>}
 */
public class MetricsHandler extends PipedHttpHandler {

    private enum ResponseType {
        JSON("application/json") {
            @Override
            public String generateResponse(MetricRegistry registry) throws IOException {
                ObjectMapper mapper = new ObjectMapper();
                MetricsModule metricsModule = new MetricsModule(TimeUnit.SECONDS, TimeUnit.MILLISECONDS, false);
                mapper.registerModule(metricsModule);

                ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
                StringWriter stringWriter = new StringWriter();
                writer.writeValue(stringWriter, registry);
                return stringWriter.toString();
            }
        },
        PROMETHEUS("text/plain") { //TODO: which content type to use for this format?
            @Override
            public String generateResponse(MetricRegistry registry) throws IOException {
                ObjectMapper mapper = new ObjectMapper();
                MetricsModule metricsModule = new MetricsModule(TimeUnit.SECONDS, TimeUnit.MILLISECONDS, false);
                mapper.registerModule(metricsModule);

                StringBuilder sb = new StringBuilder();

                JsonNode rootNode = mapper.valueToTree(registry);
                Iterator<Map.Entry<String, JsonNode>> rootIt = rootNode.path("timers").fields();
                while (rootIt.hasNext()) {
                    Map.Entry<String, JsonNode> rootEntry = rootIt.next();
                    String key = rootEntry.getKey();
                    final String[] split = key.split("\\.");
                    final String type = split[0];
                    final String method = split[1];
                    final String responseCode = split.length >= 3 ? split[2] : null;

                    String metricType;
                    Iterator<Map.Entry<String, JsonNode>> metricTypeIt = rootEntry.getValue().fields();
                    while (metricTypeIt.hasNext()) {
                        Map.Entry<String, JsonNode> metricTypeEntry = metricTypeIt.next();
                        metricType = metricTypeEntry.getKey();
                        String value = metricTypeEntry.getValue().asText();
                        sb.append("http_response_timer_" + type + "_" + metricType);
                        sb.append("{");
                        sb.append("method=\"" + method + "\"");
                        if (responseCode != null) {
                            sb.append(",");
                            sb.append("code=\"" + responseCode + "\"");
                        }
                        sb.append("}");
                        sb.append("=" + value);
                        sb.append("\n");
                    }
                }


                return sb.toString();
            }
        };

        String contentType;
        abstract public String generateResponse(MetricRegistry registry) throws IOException;

        ResponseType(String contentType) {
            this.contentType = contentType;
        }

        public static ResponseType forAcceptHeader(String acceptHeader) {
            return (acceptHeader != null && acceptHeader.contains(JSON.contentType)) ? JSON : PROMETHEUS;    //TODO: parse accept header correctly!
        }
    }

    @VisibleForTesting
    Configuration configuration = Bootstrapper.getConfiguration();

    @VisibleForTesting
    SharedMetricRegistryProxy metrics = new SharedMetricRegistryProxy();

    public MetricsHandler(PipedHttpHandler next) {
        super(next);
    }

    private boolean isFilledAndNotMetrics(String dbOrCollectionName) {
        return dbOrCollectionName != null && !dbOrCollectionName.equalsIgnoreCase(RequestContext._METRICS);
    }

    /**
     * Finds the metric registry that is related to the request path currently being asked for.
     * Will only write metrics in case they have been gathered - if none are there, will return null.
     * @return a metric registry, or null if none match
     */
    private MetricRegistry getCorrectMetricRegistry(RequestContext context) {
        MetricRegistry registry = null;
        if (configuration.gatheringAboveOrEqualToLevel(ROOT)) {
            if (isFilledAndNotMetrics(context.getDBName())) {
                if (isFilledAndNotMetrics(context.getCollectionName())) {
                    if (configuration.gatheringAboveOrEqualToLevel(COLLECTION)) {
                        registry = metrics.registry(context.getDBName(), context.getCollectionName());
                    }
                } else {
                    if (configuration.gatheringAboveOrEqualToLevel(DATABASE)) {
                        registry = metrics.registry(context.getDBName());
                    }
                }
            } else {
                registry = metrics.registry();
            }
        }
        return registry;
    }

    /**
     *
     * @param exchange
     * @param context
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {
        MetricRegistry registry = getCorrectMetricRegistry(context);

        if (registry != null) {
            if (context.getMethod() == METHOD.GET) {
                exchange.setStatusCode(HttpStatus.SC_OK);
                exchange.getResponseSender().send(
                    ResponseType.forAcceptHeader(exchange.getRequestHeaders().getFirst(Headers.ACCEPT))
                        .generateResponse(registry)
                );
                exchange.endExchange();
            } else {
                exchange.setStatusCode(HttpStatus.SC_OK);
                if (context.getContent() != null) {
                    exchange.getResponseSender().send(context.getContent().toString());
                }
                exchange.endExchange();
            }
        } else {  //no matching registry found
            ResponseHelper.endExchangeWithMessage(exchange, context, HttpStatus.SC_NOT_FOUND, "not found");
            next(exchange, context);
        }
    }
}
