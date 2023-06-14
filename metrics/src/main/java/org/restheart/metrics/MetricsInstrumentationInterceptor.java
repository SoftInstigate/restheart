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
import io.undertow.util.PathTemplate;
import io.undertow.util.PathTemplateMatcher;
import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RegisterPlugin(name = "metricsCollector",
        description = "collects request metrics",
        interceptPoint = InterceptPoint.REQUEST_BEFORE_AUTH)
public class MetricsInstrumentationInterceptor implements WildcardInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsInstrumentationInterceptor.class);

    @Inject("config")
    private Map<String, Object> config;

    @Inject("registry")
    private PluginsRegistry pluginsRegistry;

    private PathTemplateMatcher<Boolean> include = new PathTemplateMatcher<>();
    private PathTemplateMatcher<Boolean> exclude = new PathTemplateMatcher<>();;

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
            .forEach(pathTemplate -> this.include.add(pathTemplate, true));

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

    @VisibleForTesting
    SharedMetricsRegistryProxy metrics = new SharedMetricsRegistryProxy();

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) throws Exception {
        var exchange = request.getExchange();

        final var uri = exchange.getRequestPath();
        var startTime = System.currentTimeMillis();

        if (!exchange.isComplete()) {
            try {
                exchange.addExchangeCompleteListener((httpServerExchange, nextListener) -> {
                    addMetrics(uri, startTime, request, response);
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

        var matchInclude = this.include.match(uri);

        if (matchInclude != null && matchInclude.getValue()) {
            LOGGER.debug("Matched include path {}", matchInclude.getMatchedTemplate());

            var matchExclude = this.exclude.match(uri);

             if (matchExclude != null && matchExclude.getValue()) {
                LOGGER.debug("Matched exclude path {}", matchExclude.getMatchedTemplate());
                LOGGER.debug("Return false since matched exclude path {}", matchExclude.getMatchedTemplate());
                return false;
             }

             LOGGER.debug("Return true since matched include path {}", matchInclude.getMatchedTemplate());
             return true;
        }

        LOGGER.debug("Return false since did't match any include path");
        return false;
    }

    private void _addMetrics(MetricRegistry registry, long duration, ServiceRequest<?> request, ServiceResponse<?> response) {
        var handlingService = PluginUtils.name(PluginUtils.handlingService(pluginsRegistry, request.getExchange()));

        registry.timer(handlingService + "." + request.getMethod().toString()).update(duration, TimeUnit.MILLISECONDS);
        registry.timer(handlingService + "." + request.getMethod().toString() + "." + response.getStatusCode()).update(duration, TimeUnit.MILLISECONDS);
        registry.timer(handlingService + "." + request.getMethod().toString() + "." + (response.getStatusCode() / 100) + "xx").update(duration, TimeUnit.MILLISECONDS);
    }

    @VisibleForTesting
    void addMetrics(String uri, long startTime, ServiceRequest<?> request, ServiceResponse<?> response) {
        long duration = System.currentTimeMillis() - startTime;

        _addMetrics(metrics.registry(), duration, request, response);
        _addMetrics(metrics.registry(uri), duration, request, response);
    }
}
