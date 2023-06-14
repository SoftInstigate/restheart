/*-
 * ========================LICENSE_START=================================
 * restheart-metrics
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
package org.restheart.metrics;

import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import io.undertow.util.Headers;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.bson.BsonValue;
import org.bson.json.JsonMode;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.BsonResponse;
import org.restheart.exchange.ExchangeKeys.REPRESENTATION_FORMAT;
import static org.restheart.exchange.ExchangeKeys.REPRESENTATION_FORMAT_KEY;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.BsonService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.LambdaUtils;

/**
 * A handler for dropwizard.io metrics that can return both default metrics JSON
 * and the prometheus format.
 *
 * @author Lena Brüder {@literal <brueder@e-spirit.com>}
 * @author Christian Groth {@literal <groth@e-spirit.com>}
 */
@RegisterPlugin(name = "metrics", description = "return requests metrics", secure = true)
public class MetricsService implements BsonService {
    @VisibleForTesting
    SharedMetricsRegistryProxy metrics = new SharedMetricsRegistryProxy();

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handle(BsonRequest request, BsonResponse response) throws Exception {
        if (request.isOptions()) {
            handleOptions(request);
            return;
        } else if (!request.isGet()) {
            response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var params = request.getPathParams("/{servicename}/{uri}");

        var uri = params.containsKey("uri") ? "/".concat(params.get("uri")) : "/";

        var registry = uri.equals("/*") ? metrics.registry() : metrics.registry(uri);

        if (registry == null) {  //no matching registry found
            response.setInError(HttpStatus.SC_NOT_FOUND, "not found");
            return;
        }

        // detect metrics response type
        var representationFormatParameters = request.getQueryParameters().get(REPRESENTATION_FORMAT_KEY);

        var responseType = Optional.ofNullable(ResponseType.forQueryParameter(representationFormatParameters == null ? null : representationFormatParameters.getFirst())
        ).orElseGet(() -> ResponseType.forAcceptHeader(request.getHeader(Headers.ACCEPT.toString())));

        // render metrics or error on unknown response type
        if (responseType != null) {
            response.setStatusCode(HttpStatus.SC_OK);
            responseType.writeTo(response, uri, registry);
        } else {
            var acceptableTypes = Arrays.stream(ResponseType.values())
                .map(ResponseType::getContentType)
                .map(x -> "'" + x + "'")
                .collect(Collectors.joining(","));

            response.setInError(HttpStatus.SC_NOT_ACCEPTABLE, "not acceptable, acceptable content types are: " + acceptableTypes);
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
            public String generateResponse(String uri, MetricRegistry registry) throws IOException {
                var document = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
                return BsonUtils.toJson(document, JsonMode.RELAXED);
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
            public String generateResponse(String uri, MetricRegistry registry) throws IOException {
                return generateResponse(registry, uri, System.currentTimeMillis());
            }

            public String generateResponse(MetricRegistry registry, String uri, long timestamp) {
                // fetch metrics registry and build json data
                var root = MetricsJsonGenerator.generateMetricsBson(registry, TimeUnit.SECONDS, TimeUnit.MILLISECONDS);
                root.remove("version");

                // convert json data to prometheus format
                var sb = new StringBuilder();
                root.forEach((groupKey, groupContent) -> groupContent.asDocument().forEach((metricKey, metricContent) -> {
                        final var split = metricKey.split("\\.");
                        final var type = split[0];
                        final var method = split[1];
                        final var responseCode = split.length >= 3 ? split[2] : null;

                        metricContent.asDocument().forEach((metricType, value) -> {
                            if (value.isNumber()) {
                                sb.append("http_response_").append(groupKey).append("_").append(metricType);
                                sb.append("{");
                                if (uri != null) {
                                    sb.append("uri=\"").append(escapePrometheusLabelValue(uri)).append("\",");
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

            // see description for 'label_value' at https://prometheus.io/docs/instrumenting/exposition_formats/#comments-help-text-and-type-information
            // quote and backslash get escaped and line feed gets converted to text '\n'
            private String escapePrometheusLabelValue(String input) {
                return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
            }
        };

        SharedMetricsRegistryProxy metricsProxy = new SharedMetricsRegistryProxy();

        // if we just use /* data is aggregated this would lead to problems
        // defining filters in grafana correctly, so it's better to use an artificial value for these cases. we use a
        // value starting and ending with an underscore here to reduce the chance of hitting a real uri
        final String ALL_VALUES_LABEL_VALUE = "_all_";

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

        abstract public String generateResponse(String c, MetricRegistry registry) throws IOException;

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
            return specialization == null
                ? contentType
                : contentType + "; " + specialization;
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

        public void writeTo(ServiceResponse<?> response, String uri, MetricRegistry registry) throws IOException {
            var body = generateResponse(uri, registry);

            if (body != null) {
                response.getHeaders().put(Headers.CONTENT_TYPE, getOutputContentType());
                response.setCustomSender(() -> {
                    try {
                        response.getExchange().getResponseSender().send(body);
                    } catch(Throwable t) {
                        LambdaUtils.throwsSneakyException(t);
                    }
                });
            }
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
                var entries = Arrays.asList(acceptHeaderEntry.split(";"));

                final var contentType = entries.stream().findFirst().orElse(null);
                var qValue = 1.0d;
                String specialization = null;
                for (int i = 1; i < entries.size(); i++) {
                    var element = entries.get(i).strip();
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

            /**
             *
             */
            private static final long serialVersionUID = 1546289051858469995L;

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
            if (REPRESENTATION_FORMAT.S.name().equalsIgnoreCase(rep)
                    || REPRESENTATION_FORMAT.STANDARD.name().equalsIgnoreCase(rep)
                    || REPRESENTATION_FORMAT.HAL.name().equalsIgnoreCase(rep)
                    || REPRESENTATION_FORMAT.SHAL.name().equalsIgnoreCase(rep)
                    || REPRESENTATION_FORMAT.PLAIN_JSON.name().equalsIgnoreCase(rep)
                    || REPRESENTATION_FORMAT.PJ.name().equalsIgnoreCase(rep)) {
                return ResponseType.JSON;
            } else {
                return null;
            }
        }
    }
}
