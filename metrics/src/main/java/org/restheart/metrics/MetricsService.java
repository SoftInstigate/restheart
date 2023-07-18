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

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.StringService;
import static org.restheart.utils.BsonUtils.array;
import org.restheart.utils.HttpStatus;
import static org.restheart.utils.MetricsUtils.METRICS_REGISTRIES_PREFIX;
import com.codahale.metrics.SharedMetricRegistries;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.dropwizard.samplebuilder.DefaultSampleBuilder;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;

/**
 * Service to returns metrics in prometheus format.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "metrics", description = "returns requests metrics", secure = true)
public class MetricsService implements StringService {

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handle(StringRequest request, StringResponse response) throws Exception {
        if (request.isOptions()) {
            handleOptions(request);
            return;
        } else if (!request.isGet()) {
            response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var params = request.getPathParams("/{servicename}/{pathTemplate}");

        if (!params.containsKey("pathTemplate")) {
            var content = array();
            SharedMetricRegistries.names().stream().filter(name -> name.startsWith(METRICS_REGISTRIES_PREFIX)).forEachOrdered(reg -> content.add(reg.substring(METRICS_REGISTRIES_PREFIX.length())));
            response.setContent(content.toJson());
            response.setContentTypeAsJson();
        } else {
            var _pathTemplate = "/" + params.get("pathTemplate");
            var pathTemplate = SharedMetricRegistries.names().stream().filter(METRICS_REGISTRIES_PREFIX.concat(_pathTemplate)::equals).findFirst();

            if (pathTemplate.isPresent()) {
                var registry = SharedMetricRegistries.getOrCreate(pathTemplate.get());

                var collector = new CollectorRegistry();
                collector.register(new DropwizardExports(registry, new CustomSampler()));
                var writer = new StringWriter();

                TextFormat.write004(writer, collector.metricFamilySamples());

                response.setContent(writer.toString());
            } else {
                response.setInError(HttpStatus.SC_NOT_FOUND, "metric not found");
                return;
            }
        }
    }

    private class CustomSampler implements SampleBuilder {
        private static DefaultSampleBuilder DSB = new DefaultSampleBuilder();

        @Override
        public Sample createSample(String dropwizardName, String nameSuffix, List<String> additionalLabelNames, List<String> additionalLabelValues, double value) {
            if (dropwizardName.startsWith("jvm")) {
                return DSB.createSample(dropwizardName, nameSuffix, additionalLabelNames, additionalLabelValues, value);
            } else {
                var nals = MetricNameAndLabels.fromString(dropwizardName);

                List<String> _additionalLabelNames = new ArrayList<>();
                List<String> _additionalLabelValues = new ArrayList<>();

                nals.lables().forEach(l -> {
                    _additionalLabelNames.add(l.name());
                    _additionalLabelValues.add(l.value());
                });

                _additionalLabelNames.addAll(additionalLabelNames);
                _additionalLabelValues.addAll(additionalLabelValues);

                return DSB.createSample(nals.name(), nameSuffix, _additionalLabelNames, _additionalLabelValues, value);
            }
        }
    }
}
