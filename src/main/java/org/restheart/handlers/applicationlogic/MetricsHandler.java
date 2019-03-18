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

import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.restheart.Bootstrapper;
import org.restheart.Configuration;
import org.restheart.Configuration.METRICS_GATHERING_LEVEL;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.COLLECTION;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.DATABASE;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.OFF;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.ROOT;
import org.restheart.db.DbsDAO;
import org.restheart.handlers.PipedHttpHandler;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.RequestContext.METHOD;
import static org.restheart.handlers.RequestContext.REPRESENTATION_FORMAT_KEY;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.MetricsJsonGenerator;
import org.restheart.utils.ResponseHelper;
import org.restheart.utils.SharedMetricRegistryProxy;

/**
 * A handler for dropwizard.io metrics that can return both default metrics JSON
 * and the prometheus format.
 *
 * @author Lena Brüder {@literal <brueder@e-spirit.com>}
 * @author Christian Groth {@literal <groth@e-spirit.com>}
 */
public class MetricsHandler extends PipedHttpHandler {

    @VisibleForTesting
    Configuration configuration = Bootstrapper.getConfiguration();

    @VisibleForTesting
    SharedMetricRegistryProxy metrics = new SharedMetricRegistryProxy();

    public MetricsHandler(PipedHttpHandler next) {
        super(next);
    }

    public MetricsHandler(PipedHttpHandler next, DbsDAO dbsDao) {
        super(next, dbsDao);
    }

    /**
     * Computes the needed metrics level for given request.
     * @param context current request context
     * @return metrics level for request
     */
    METRICS_GATHERING_LEVEL getMetricsLevelForRequest(RequestContext context) {

        // check if enabled at all
        METRICS_GATHERING_LEVEL level = OFF;
        if (configuration.gatheringAboveOrEqualToLevel(ROOT)) {

            // check if db context is given
            if (isFilledAndNotMetrics(context.getDBName())) {

                // check if collection context is given
                if (isFilledAndNotMetrics(context.getCollectionName())) {

                    // check if collection level configured
                    if (configuration.gatheringAboveOrEqualToLevel(COLLECTION)) {
                        level = COLLECTION;
                    }
                } else {

                    // check if database level configured
                    if (configuration.gatheringAboveOrEqualToLevel(DATABASE)) {
                        level = DATABASE;
                    }
                }
            } else {
                level = ROOT;
            }
        }

        return level;
    }

    private boolean isFilledAndNotMetrics(String dbOrCollectionName) {
        return dbOrCollectionName != null && !dbOrCollectionName.equalsIgnoreCase(RequestContext._METRICS);
    }

    /**
     * Resolves the metrics registry for given gathering level.
     * @param context current request context
     * @param metricsLevel desired metrics gathering level
     * @return metrics registry
     */
    MetricRegistry getMetricsRegistry(RequestContext context, METRICS_GATHERING_LEVEL metricsLevel) {
        switch (metricsLevel) {
            case ROOT: return metrics.registry();
            case DATABASE: return metrics.registry(context.getDBName());
            case COLLECTION: return metrics.registry(context.getDBName(), context.getCollectionName());
            default: return null;
        }
    }

    @Override
    public void handleRequest(HttpServerExchange exchange, RequestContext context) throws Exception {

        METRICS_GATHERING_LEVEL metricsLevelForRequest = getMetricsLevelForRequest(context);
        MetricRegistry registry = getMetricsRegistry(context, metricsLevelForRequest);

        if (registry != null) {
            if (context.getMethod() == METHOD.GET) {

                // detect metrics response type
                Deque<String> representationFormatParameters = exchange.getQueryParameters().get(REPRESENTATION_FORMAT_KEY);
                ResponseType responseType = Optional.ofNullable(
                        ResponseType.forQueryParameter(
                                representationFormatParameters == null ? null : representationFormatParameters.getFirst())
                ).orElseGet(()
                        -> ResponseType.forAcceptHeader(exchange.getRequestHeaders().getFirst(Headers.ACCEPT))
                );

                // render metrics or error on unknown response type
                if (responseType != null) {
                    exchange.setStatusCode(HttpStatus.SC_OK);
                    responseType.writeTo(exchange, metricsLevelForRequest, registry);
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

    @VisibleForTesting
    enum ResponseType {

        /**
         * dropwizard-metrics compatible JSON format, see
         * https://github.com/iZettle/dropwizard-metrics/blob/v3.1.2/metrics-json/src/main/java/com/codahale/metrics/json/MetricsModule.java
         * for how it looks like
         */
        JSON("application/json") {
            @Override
            public String generateResponse(METRICS_GATHERING_LEVEL metricsLevel, MetricRegistry registry) throws IOException {
                BsonDocument document = MetricsJsonGenerator
                        .generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
                return document.toJson(
                        JsonWriterSettings.builder()
                                .outputMode(JsonMode.RELAXED)
                                .indent(true)
                                .build()
                );
            }
        },

        /**
         * format description can be found at
         * https://prometheus.io/docs/instrumenting/exposition_formats/
         */
        PROMETHEUS("text/plain", "version=0.0.4") {
            private String valueAsString(BsonValue value) {
                if (value.isDouble()) {
                    return Double.toString(value.asDouble().getValue());
                } else if (value.isString()) {
                    return value.asString().getValue();
                } else if (value.isInt64()) {
                    return Long.toString(value.asInt64().getValue());
                } else if (value.isInt32()) {
                    return Long.toString(value.asInt32().getValue());
                } else {
                    return value.toString();
                }
            }

            @Override
            public String generateResponse(METRICS_GATHERING_LEVEL metricsLevel, MetricRegistry registry) throws IOException {

                StringBuilder sb = new StringBuilder();
                long timestamp = System.currentTimeMillis();
                if(metricsLevel == ROOT) {
                    metricsProxy.registries().forEach(registryName -> {

                        // reconstruct database and collection name
                        String[] registryNameParts = registryName.split("/");
                        String databaseName = registryNameParts.length > 0 ? registryNameParts[0] : null;
                        String collectionName = registryNameParts.length > 1 ? registryNameParts[1] : null;
                        boolean isRootMetricsRegistry = metricsProxy.isDefault(databaseName);

                        // set values for database and collection labels
                        if(isRootMetricsRegistry) {
                            databaseName = DATABASE_AND_COLLECTION_ALL_VALUES_LABEL_VALUE;
                            collectionName = DATABASE_AND_COLLECTION_ALL_VALUES_LABEL_VALUE;
                        } else {
                            if(collectionName == null) {
                                collectionName = DATABASE_AND_COLLECTION_ALL_VALUES_LABEL_VALUE;
                            }
                        }

                        // generate metrics
                        sb.append(generateResponse(metricsProxy.registry(registryName), databaseName, collectionName, timestamp));
                    });
                } else {

                    // we provide null here for database and collection names to not change the previous behavior, generating
                    // these prometheus labels is only available when requesting metrics on root level
                    sb.append(generateResponse(registry, null, null, timestamp));
                }

                return sb.toString().trim();
            }

            public String generateResponse(MetricRegistry registry, String databaseName, String collectionName, long timestamp) {

                // fetch metrics registry and build json data
                BsonDocument root = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
                root.remove("version");

                // convert json data to prometheus format
                StringBuilder sb = new StringBuilder();
                root.forEach((groupKey, groupContent)
                        -> groupContent.asDocument().forEach((metricKey, metricContent) -> {
                            final String[] split = metricKey.split("\\.");
                            final String type = split[0];
                            final String method = split[1];
                            final String responseCode = split.length >= 3 ? split[2] : null;

                            metricContent.asDocument().forEach((metricType, value) -> {
                                if (value.isNumber()) {
                                    sb.append("http_response_").append(groupKey).append("_").append(metricType);
                                    sb.append("{");
                                    if(databaseName != null) {
                                        sb.append("database=\"").append(databaseName).append("\",");
                                    }
                                    if(collectionName != null) {
                                        sb.append("collection=\"").append(collectionName).append("\",");
                                    }
                                    sb.append("type=\"").append(type).append("\",");
                                    sb.append("method=\"").append(method).append("\"");
                                    if (responseCode != null) {
                                        sb.append(",code=\"").append(responseCode).append("\"");
                                    }
                                    sb.append("} ");
                                    sb.append(valueAsString(value));
                                    sb.append(" ");
                                    sb.append(timestamp);
                                    sb.append("\n");
                                }
                            });

                            sb.append("\n");
                        }
                ));

                // return result
                return sb.toString();
            }
        };

        SharedMetricRegistryProxy metricsProxy = new SharedMetricRegistryProxy();

        // if we just use null for database and collection labels when data is aggregated this would lead to problems
        // defining filters in grafana correctly, so it's better to use an artificial value for these cases. we use a
        // value starting and ending with an underscore here to reduce the chance of hitting a real database or collection name
        final String DATABASE_AND_COLLECTION_ALL_VALUES_LABEL_VALUE = "_all_";

        /**
         * The content-type that is being used for both Accept and Content-Type
         * headers
         */
        String contentType;

        /**
         * the media range for the given content type
         */
        String mediaRange;

        /**
         * if any, the specialization of the content-type (after ";" in
         * Content-Type header). null if n/a.
         */
        String specialization;

        abstract public String generateResponse(METRICS_GATHERING_LEVEL context, MetricRegistry registry) throws IOException;

        ResponseType(String contentType) {
            this(contentType, null);
        }

        ResponseType(String contentType, String specialization) {
            this.contentType = contentType;
            this.mediaRange = calculateMediaRange(contentType);
            this.specialization = specialization;
        }

        @VisibleForTesting
        static String calculateMediaRange(String contentType) {
            return contentType.substring(0, contentType.indexOf('/')) + "/*";
        }

        public String getContentType() {
            return contentType;
        }

        public String getMediaRange() {
            return mediaRange;
        }

        public String getOutputContentType() {
            if (specialization == null) {
                return contentType;
            } else {
                return contentType + "; " + specialization;
            }
        }

        /**
         * whether this content type is acceptable for the given accept header
         * entry
         */
        public boolean isAcceptableFor(AcceptHeaderEntry entry) {
            return entry.contentType.equalsIgnoreCase("*/*")
                    || (entry.contentType.equalsIgnoreCase(contentType)
                    && (entry.specialization == null || entry.specialization.equalsIgnoreCase(specialization)))
                    || entry.contentType.equalsIgnoreCase(mediaRange);
        }

        public void writeTo(HttpServerExchange exchange, METRICS_GATHERING_LEVEL metricsLevel, MetricRegistry registry) throws IOException {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, getOutputContentType());
            exchange.getResponseSender().send(generateResponse(metricsLevel, registry));
        }

        /**
         * Encapsulate code around accept-header handling
         */
        static class AcceptHeaderEntry {

            /**
             * Generate an accept header entry (if possible) for the given
             * entry. Will be called for each entry of the accept header.
             *
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

            String contentType;
            String specialization;
            double qValue = 1.0;

            AcceptHeaderEntry(String contentType) {
                this(contentType, null, Double.MAX_VALUE);
            }

            AcceptHeaderEntry(String contentType, String specialization, double qValue) {
                this.contentType = contentType;
                this.specialization = specialization;
                this.qValue = qValue;
            }

            @Override
            public String toString() {
                return "AcceptHeaderEntry{"
                        + "contentType='" + contentType + '\''
                        + ", specialization='" + specialization + '\''
                        + ", qValue=" + qValue
                        + '}';
            }
        }

        /**
         * sorts large q-values first, smaller ones later
         */
        static class AcceptHeaderEntryComparator implements Comparator<AcceptHeaderEntry>, Serializable {

            @Override
            public int compare(AcceptHeaderEntry one, AcceptHeaderEntry two) {
                return Double.compare(two.qValue, one.qValue);
            }
        }

        /**
         * Returns the correct response generator for any given accept header.
         *
         * behaviour is: * by default, return prometheus format * if something
         * else is wanted, return that (if available) * if Accept header cannot
         * be satisfied, return 406 (NOT ACCEPTABLE)
         */
        public static ResponseType forAcceptHeader(String acceptHeader) {
            if (acceptHeader == null || acceptHeader.equalsIgnoreCase("*/*")) {
                return ResponseType.PROMETHEUS;
            }
            return Arrays.stream(acceptHeader.split(","))
                    .map(String::trim)
                    .map(AcceptHeaderEntry::of).filter(Objects::nonNull) //parse
                    .sorted(new AcceptHeaderEntryComparator()) //sort by q-value
                    .flatMap(x -> Arrays.stream(ResponseType.values()).filter(rt -> rt.isAcceptableFor(x)))
                    .findFirst()
                    .orElse(null);
        }

        public static ResponseType forQueryParameter(String rep) {
            if (RequestContext.REPRESENTATION_FORMAT.PLAIN_JSON.name().equalsIgnoreCase(rep)
                    || RequestContext.REPRESENTATION_FORMAT.PJ.name().equalsIgnoreCase(rep)) {
                return ResponseType.JSON;
            } else {
                return null;
            }
        }
    }
}
