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

import io.undertow.util.PathTemplate;
import io.undertow.util.PathTemplateMatcher;
import io.undertow.util.PathTemplateMatcher.PathMatchResult;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import static org.restheart.metrics.MetricsService.METRICS_REGISTRIES_PREFIX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.codahale.metrics.SharedMetricRegistries;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RegisterPlugin(name = "requestsMetricsCollector",
        description = "collects http requests metrics",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH,
        enabledByDefault = false)
public class RequestsMetricsCollector implements WildcardInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestsMetricsCollector.class);

    @Inject("config")
    private Map<String, Object> config;

    @Inject("registry")
    private PluginsRegistry pluginsRegistry;

    // include is a set because we want to check all path templates that match the request
    private Set<PathTemplateMatcher<Boolean>> include = new HashSet<>();
    private PathTemplateMatcher<Boolean> exclude = new PathTemplateMatcher<>();

    @OnInit
    public void onInit() {
        List<String> _include = argOrDefault(config, "include", new ArrayList<>());
        List<String> _exclude = argOrDefault(config, "exclude", new ArrayList<>());

        _include.stream().map(path -> {
                try {
                    var ret =  PathTemplate.create(path);
                    LOGGER.debug("Add include path {}", ret.getTemplateString());
                    return ret;
                } catch(Throwable t) {
                    LOGGER.warn("Wrong include path {}", path , t);
                    return null;
                }
            })
            .filter(pathTemplate -> pathTemplate != null)
            .forEach(pathTemplate ->{
                final var ptm = new PathTemplateMatcher<Boolean>();
                ptm.add(pathTemplate, true);
                this.include.add(ptm);
            });

        _exclude.stream().map(path -> {
                try {
                    var ret =  PathTemplate.create(path);
                    LOGGER.debug("Add exclude path {}", ret.getTemplateString());
                    return ret;
                } catch(Throwable t) {
                    LOGGER.warn("Wrong exclude path {}", path , t);
                    return null;
                }
            })
            .filter(pathTemplate -> pathTemplate != null)
            .forEach(pathTemplate -> this.exclude.add(pathTemplate, true));
    }

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        var exchange = request.getExchange();

        if (!exchange.isComplete()) {
            final var startTime = System.currentTimeMillis();
            final var uri = request.getPath();

            // template string /{db}/{coll}
            // uri /foo/bar

            final var matchedTemplates = this.include.stream()
                .map(ptm -> ptm.match(uri))
                .filter(pmr -> pmr != null)
                .collect(Collectors.toList());

            LOGGER.debug("Matched path templates {}", matchedTemplates.stream().map(t -> t.getMatchedTemplate()).collect(Collectors.toList()));

            try {
                exchange.addExchangeCompleteListener((httpServerExchange, nextListener) -> {
                    matchedTemplates.forEach(mt -> addMetrics(mt, startTime, request, response));
                    nextListener.proceed();
                });
            } catch(Throwable t) {
                LOGGER.warn("Error adding metric collector to request {} {}", request.getMethod(), request.getPath(), t);
            }
        }
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        var uri = request.getPath();
        var matchInclude = this.include.stream().anyMatch(ptm -> ptm.match(uri) != null);

        if (matchInclude) {
            var matchExclude = this.exclude.match(uri);

             if (matchExclude != null && matchExclude.getValue()) {
                LOGGER.debug("Matched exclude path {}", matchExclude.getMatchedTemplate());
                LOGGER.debug("Return false since matched exclude path {}", matchExclude.getMatchedTemplate());
                return false;
             }

             LOGGER.debug("Return true since matched include paths");
             return true;
        }

        LOGGER.debug("Return false since did't match any include path");
        return false;
    }

    private void addMetrics(PathMatchResult<Boolean> pathTemplate, long startTime, ServiceRequest<?> request, ServiceResponse<?> response) {
        var registyName = METRICS_REGISTRIES_PREFIX.concat(pathTemplate.getMatchedTemplate());
        var registry = SharedMetricRegistries.getOrCreate(registyName);
        var duration = System.currentTimeMillis() - startTime;
        var _status = response.getStatusCode() > 0 ? response.getStatusCode() : 200;

        var status = new MetricLabel("response_status_code", _status + "");
        var method = new MetricLabel("request_method", request.getMethod().toString());
        var matchedTemplate = new MetricLabel("path_template", pathTemplate.getMatchedTemplate());

        var matchParams = pathTemplate.getParameters().entrySet().stream()
            .filter(p -> !p.getKey().equals("*"))
            .sorted()
            .map(param -> new MetricLabel("path_template_param_".concat(param.getKey()), param.getValue()))
            .collect(Collectors.toList());

        var t1wp = new ArrayList<MetricLabel>();
        t1wp.addAll(MetricLabel.collect(method, matchedTemplate, status));
        t1wp.addAll(matchParams);

        // custom labels
        var customLabels = Metrics.getMetricLabels(request);
        if (customLabels != null) {
            t1wp.addAll(customLabels);
        }

        registry.timer(new MetricNameAndLabels("http_requests", t1wp).toString()).update(duration, TimeUnit.MILLISECONDS);
    }
}
