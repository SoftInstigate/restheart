/*-
 * ========================LICENSE_START=================================
 * restheart-metrics
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.StringService;
import static org.restheart.utils.BsonUtils.array;
import org.restheart.utils.HttpStatus;

import com.codahale.metrics.SharedMetricRegistries;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

/**
 * Service to returns metrics in prometheus format.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "metrics", description = "returns requests metrics", secure = true, defaultURI="/metrics")
public class MetricsService implements StringService {
    /**
     *
     * prefix for registry names used by retheart-metrics plugins
     */
    public static String METRICS_REGISTRIES_PREFIX = "METRICS-";

    @Inject("config")
    private Map<String, Object> config;
    private String serviceUri = "/metrics";

    @OnInit
    public void onInit() {
        this.serviceUri = argOrDefault(config, "uri", "/metrics");
    }

    /**
     * @param request
     * @param response
     * @throws java.io.IOException
     */
    @Override
    public void handle(StringRequest request, StringResponse response) throws IOException {
        if (request.isOptions()) {
            handleOptions(request);
            return;
        } else if (!request.isGet()) {
            response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var params = request.getPathParams(serviceUri.concat("/{*}"));

        if (!params.containsKey("*")) {
            var content = array();
            SharedMetricRegistries.names().stream().filter(name -> name.startsWith(METRICS_REGISTRIES_PREFIX)).forEachOrdered(reg -> content.add(reg.substring(METRICS_REGISTRIES_PREFIX.length())));
            response.setContent(content.toJson());
            response.setContentTypeAsJson();
        } else {
            var _pathTemplate = "/" + params.get("*");
            var pathTemplate = SharedMetricRegistries.names().stream().filter(METRICS_REGISTRIES_PREFIX.concat(_pathTemplate)::equals).findFirst();

            if (pathTemplate.isPresent()) {
                var collector = new CollectorRegistry();
                collector.register(new RHDropwizardExports(pathTemplate.get()));
                var writer = new StringWriter();

                TextFormat.write004(writer, collector.metricFamilySamples());

                response.setContent(writer.toString());
            } else {
                // Return empty metrics instead of 404 to prevent Prometheus from marking the target as down
                // The registry is created on first request matching the include pattern
                response.setContent("");
            }
        }
    }
}
