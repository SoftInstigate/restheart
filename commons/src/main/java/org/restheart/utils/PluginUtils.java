/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2020 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.utils;

import io.undertow.server.HttpServerExchange;
import java.util.Map;
import org.restheart.exchange.BufferedByteArrayRequest;
import org.restheart.exchange.PipelineInfo;
import static org.restheart.exchange.PipelineInfo.PIPELINE_TYPE.SERVICE;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PluginUtils {
    public static InterceptPoint interceptPoint(Interceptor interceptor) {
        var a = interceptor.getClass()
                .getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return null;
        } else {
            return a.interceptPoint();
        }
    }

    public static InitPoint initPoint(Initializer initializer) {
        var a = initializer.getClass()
                .getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return null;
        } else {
            return a.initPoint();
        }
    }

    public static boolean requiresContent(Interceptor interceptor) {
        var a = interceptor.getClass()
                .getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return false;
        } else {
            return a.requiresContent();
        }
    }

    /**
     *
     * @param service
     * @return the service default URI. If not explicitly set via defaulUri
     * attribute, it is /[service-name]
     */
    public static String defaultURI(Service service) {
        var a = service.getClass()
                .getDeclaredAnnotation(RegisterPlugin.class);

        return a == null
                ? null
                : a.defaultURI() == null || "".equals(a.defaultURI())
                ? "/".concat(a.name())
                : a.defaultURI();
    }

    /**
     *
     * @param <P>
     * @param serviceClass
     * @return the service default URI. If not explicitly set via defaulUri
     * attribute, it is /[service-name]
     */
    public static <P extends Service> String defaultURI(Class<P> serviceClass) {
        var a = serviceClass
                .getDeclaredAnnotation(RegisterPlugin.class);

        return a == null
                ? null
                : a.defaultURI() == null || "".equals(a.defaultURI())
                ? "/".concat(a.name())
                : a.defaultURI();
    }

    /**
     *
     * @param <P>
     * @param conf the plugin configuration got from @InjectConfiguration
     * @param serviceClass the class of the service
     * @return the actual service uri set in cofiguration or the defaultURI
     */
    public static <P extends Service> String actualUri(Map<String, Object> conf,
            Class<P> serviceClass) {

        if (conf != null
                && conf.get("uri") != null
                && conf.get("uri") instanceof String) {
            return (String) conf.get("uri");
        } else {
            return PluginUtils.defaultURI(serviceClass);
        }
    }

    /**
     *
     * @param service
     * @return the intercept points of interceptors that must not be executed on
     * requests handled by service
     */
    public static InterceptPoint[] dontIntercept(Service service) {
        var a = service.getClass()
                .getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return new InterceptPoint[0];
        } else {
            return a.dontIntercept();
        }
    }

    /**
     *
     * @param registry
     * @param exchange
     * @return the service handling the exchange or null if the request is not
     * handled by a service
     */
    public static Service handlingService(PluginsRegistry registry,
            HttpServerExchange exchange) {
        var pi = pipelineInfo(exchange);

        if (pi != null && pi.getType() == SERVICE) {
            var srvName = pi.getName();

            if (srvName != null) {
                var _s = registry.getServices()
                        .stream()
                        .filter(s -> srvName.equals(s.getName()))
                        .map(s -> s.getInstance())
                        .findAny();

                if (_s.isPresent()) {
                    return _s.get();
                }
            }
        }

        return null;
    }

    /**
     *
     * @param registry
     * @param exchange
     * @return the intercept points of interceptors that must not be executed on
     * the exchange
     */
    public static InterceptPoint[] dontIntercept(PluginsRegistry registry,
            HttpServerExchange exchange) {
        var hs = handlingService(registry, exchange);

        return hs == null
                ? new InterceptPoint[0]
                : dontIntercept(hs);
    }

    public static PipelineInfo pipelineInfo(HttpServerExchange exchange) {
        return BufferedByteArrayRequest.of(exchange).getPipelineInfo();
    }
}
