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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
        /** dropwizard-metrics compatible JSON format, see
         *  https://github.com/iZettle/dropwizard-metrics/blob/v3.1.2/metrics-json/src/main/java/com/codahale/metrics/json/MetricsModule.java
         *  for how it looks like
         */
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
        /**format description can be found at https://prometheus.io/docs/instrumenting/exposition_formats/ */
        PROMETHEUS("text/plain", "version=0.0.4") { //TODO: which content type to use for this format?
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

        /**The content-type that is being used for both Accept and Content-Type headers*/
        String contentType;
        /**if any, the specialization of the content-type (after ";" in Content-Type header). null if n/a.*/
        String specialization;
        abstract public String generateResponse(MetricRegistry registry) throws IOException;

        ResponseType(String contentType) {
            this.contentType = contentType;
            this.specialization = null;
        }
        ResponseType(String contentType, String specialization) {
            this.contentType = contentType;
            this.specialization = specialization;
        }

        public String getContentType() {
            return contentType;
        }
        public String getOutputContentType() {
            if (specialization == null) {
                return contentType;
            } else {
                return contentType + "; " + specialization;
            }
        }

        public boolean acceptedBy(AcceptHeaderEntry entry) {
            return entry.contentType.equalsIgnoreCase("*/*")
                   || entry.contentType.equalsIgnoreCase(contentType)
                   && (entry.specialization == null || entry.specialization.equalsIgnoreCase(specialization));
        }

        public void writeTo(HttpServerExchange exchange, MetricRegistry registry) throws IOException {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, getOutputContentType());
            exchange.getResponseSender().send(generateResponse(registry));
        }

        static class AcceptHeaderEntry implements Comparable<AcceptHeaderEntry> {
            String contentType;
            String specialization;
            double qValue = 1.0;

            public AcceptHeaderEntry(String contentType, String specialization, double qValue) {
                this.contentType = contentType;
                this.specialization = specialization;
                this.qValue = qValue;
            }

            /**
             * Generate an accept header entry (if possible) for the given entry.
             * @return null if the header could not be generated.
             */
            public static AcceptHeaderEntry of(String acceptHeaderEntry) {
                List<String> entries = Arrays.asList(acceptHeaderEntry.split(";"));

                final String contentType = entries.stream().findFirst().orElse(null);
                double qValue = 1.0;
                String specialization = null;
                for (int i = 1; i < entries.size(); i++) {
                    String element = entries.get(i).trim();
                    if (element.startsWith("q=")) {
                        try {
                            qValue = Double.parseDouble(element.substring(2));
                        } catch (NumberFormatException nfe) {
                            qValue = 1.0;
                        }
                    } else {
                        specialization = element;
                    }
                }
                if (contentType == null) {
                    return null;
                } else {
                    return new AcceptHeaderEntry(contentType, specialization, qValue);
                }
            }

            @Override
            public int compareTo(AcceptHeaderEntry other) {
                return Double.compare(other.qValue, this.qValue);
            }

            @Override
            public String toString() {
                return "AcceptHeaderEntry{" +
                       "contentType='" + contentType + '\'' +
                       ", specialization='" + specialization + '\'' +
                       ", qValue=" + qValue +
                       '}';
            }
        }

        /**
         * Returns the correct response generator for any given accept header.
         *
         * behaviour is:
         * * by default, return prometheus format
         * * if something else is wanted, return that (if available)
         * * if Accept header cannot be satisfied, return 406 (NOT ACCEPTABLE)
         */
        public static ResponseType forAcceptHeader(String acceptHeader) {
            return Arrays.stream(acceptHeader.split(","))
                .map(AcceptHeaderEntry::of).filter(Objects::nonNull) //parse
                .sorted()       //sort by q-value
                .flatMap(x ->   //for each entry: add the response type that is being accepted by the entry (may be multiple)
                             Arrays.stream(ResponseType.values()).filter(rt -> rt.acceptedBy(x))
                ).findFirst().orElse(null);
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
                ResponseType responseType = ResponseType.forAcceptHeader(exchange.getRequestHeaders().getFirst(Headers.ACCEPT));
                if (responseType != null) {
                    exchange.setStatusCode(HttpStatus.SC_OK);
                    responseType.writeTo(exchange, registry);
                    exchange.endExchange();
                } else {
                    String acceptableTypes = Arrays.stream(ResponseType.values())
                        .map(ResponseType::getContentType)
                        .map(x -> "'" + x + "'")
                        .collect(Collectors.joining(","));
                    ResponseHelper.endExchangeWithMessage(exchange,
                        context,
                        HttpStatus.SC_NOT_ACCEPTABLE,
                        "not acceptable, acceptable content types are: " + acceptableTypes
                    );
                    next(exchange, context);
                }
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
