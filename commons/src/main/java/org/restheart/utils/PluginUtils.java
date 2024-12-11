/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
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

import java.lang.reflect.Type;
import java.util.Map;

import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import static org.restheart.exchange.PipelineInfo.PIPELINE_TYPE.SERVICE;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.plugins.ExchangeTypeResolver;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.Plugin;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.restheart.plugins.Service;
import org.restheart.plugins.security.Authorizer;

import io.undertow.server.HttpServerExchange;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PluginUtils {
    /**
     *
     * @param interceptor
     * @return the interceptPoint as defined by the @RegisterPlugin annotation if
     *         annotated or the value of the field interceptPoint if exists
     */
    @SuppressWarnings("rawtypes")
    public static InterceptPoint interceptPoint(Interceptor interceptor) {
        var a = interceptor.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        InterceptPoint ret;

        if (a == null) {
            // if class is not annotated, look for field interceptPoint
            ret = findInterceptPointField(interceptor.getClass(), interceptor);
        } else {
            ret = a.interceptPoint();
        }

        if (ret == InterceptPoint.ANY) {
            throw new IllegalArgumentException("intercept point ANY can only be used in dontIntercept attribute");
        } else {
            return ret;
        }
    }


    /**
     *
     * @param interceptor
     * @return the requiredinterceptor as defined by the @RegisterPlugin annotation
     */
    public static Boolean requiredinterceptor(Interceptor interceptor) {
        var a = interceptor.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return null;
        } else {
            return a.requiredinterceptor();
        }
    }

    /**
     * this is used to retrieve the interceptPoint of an Interceptor that is not annotated
     * with @RegisterPlugin
     *
     * @param clazz
     * @param o
     * @return
     */
    private static InterceptPoint findInterceptPointField(Class<?> clazz, Object o) {
        try {
            var field = clazz.getDeclaredField("interceptPoint");
            field.setAccessible(true);

            var value = field.get(o);
            if (value instanceof InterceptPoint ip) {
                return ip;
            } else {
                return null;
            }
        } catch (NoSuchFieldException nfe) {
            if (clazz.getSuperclass() != null) {
                return findInterceptPointField(clazz.getSuperclass(), o);
            } else {
                return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    public static InitPoint initPoint(Initializer initializer) {
        var a = initializer.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return null;
        } else {
            return a.initPoint();
        }
    }

    public static boolean requiresContent(Interceptor<? extends Request<?>, ? extends Response<?>> interceptor) {
        var a = interceptor.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return false;
        } else {
            return a.requiresContent();
        }
    }

    /**
     *
     * @param plugin
     * @return the plugin name
     */
    public static String name(Plugin plugin) {
        var a = plugin.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return findNameField(plugin.getClass(), plugin);
        } else {
            return a.name();
        }
    }

    /**
     * this is used to retrieve the name of a plugin that is not annotated
     * with @RegisterPlugin. Assumes the existance of the field 'name'.
     *
     * @param clazz
     * @param o
     * @return
     */
    private static String findNameField(Class<?> clazz, Object o) {
        try {
            var field = clazz.getDeclaredField("name");
            field.setAccessible(true);

            var value = field.get(o);
            if (value instanceof String s) {
                return s;
            } else {
                return null;
            }
        } catch (NoSuchFieldException nfe) {
            if (clazz.getSuperclass() != null) {
                return findNameField(clazz.getSuperclass(), o);
            } else {
                return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     *
     * @param service
     * @return the service default URI. If not explicitly set via defaulUri
     *         attribute, it is /[service-name]
     */
    @SuppressWarnings("rawtypes")
    public static String defaultURI(Service service) {
        var a = service.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        return a == null ? null
                : a.defaultURI() == null || "".equals(a.defaultURI()) ? "/".concat(a.name()) : a.defaultURI();
    }

    /**
     *
     * @param service
     * @return uri match policy.
     */
    @SuppressWarnings("rawtypes")
    public static MATCH_POLICY uriMatchPolicy(Service service) {
        return service.getClass().getDeclaredAnnotation(RegisterPlugin.class).uriMatchPolicy();
    }

    /**
     *
     * @param <P>
     * @param serviceClass
     * @return the service default URI. If not explicitly set via defaulUri
     *         attribute, it is /[service-name]
     */
    @SuppressWarnings("rawtypes")
    public static <P extends Service> String defaultURI(Class<P> serviceClass) {
        var a = serviceClass.getDeclaredAnnotation(RegisterPlugin.class);

        return a == null ? null
                : a.defaultURI() == null || "".equals(a.defaultURI()) ? "/".concat(a.name()) : a.defaultURI();
    }

    /**
     *
     * @param <P>
     * @param conf         the plugin configuration got from @Inject("conf")
     * @param serviceClass the class of the service
     * @return the actual service uri set in cofiguration or the defaultURI
     */
    @SuppressWarnings("rawtypes")
    public static <P extends Service> String actualUri(Map<String, Object> conf, Class<P> serviceClass) {
        if (conf != null && conf.get("uri") != null && conf.get("uri") instanceof String) {
            return (String) conf.get("uri");
        } else {
            return PluginUtils.defaultURI(serviceClass);
        }
    }

    /**
     *
     * @param service
     * @return the intercept points of interceptors that must not be executed on
     *         requests handled by service
     */
    @SuppressWarnings("rawtypes")
    public static InterceptPoint[] dontIntercept(Service service) {
        if (service == null) {
            return null;
        }

        var a = service.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return new InterceptPoint[0];
        } else {
            return a.dontIntercept();
        }
    }

    /**
     *
     * @param authorizer
     * @return the Authorizer type
     */
    public static Authorizer.TYPE authorizerType(Authorizer authorizer) {
        if (authorizer == null) {
            return Authorizer.TYPE.ALLOWER;
        }

        var a = authorizer.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return Authorizer.TYPE.ALLOWER;
        } else {
            return a.authorizerType();
        }
    }

    public static boolean blocking(Service<?,?> service) {
        if (service == null) {
            return true;
        }

        var a = service.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return true;
        } else {
            return a.blocking();
        }
    }

    /**
     *
     * @param registry
     * @param exchange
     * @return the service handling the exchange or null if the request is not
     *         handled by a service
     */
    public static Service<?, ?> handlingService(PluginsRegistry registry, HttpServerExchange exchange) {
        var pr = handlingServicePluginRecord(registry, exchange);

        return pr == null ? null : pr.getInstance();
    }

    /**
     *
     * @param registry
     * @param exchange
     * @return the plugin record of the service handling the exchange or null if the request is not
     *         handled by a service
     */
    public static PluginRecord<Service<?, ?>> handlingServicePluginRecord(PluginsRegistry registry, HttpServerExchange exchange) {
        var pi = Request.getPipelineInfo(exchange);

        if (pi != null && pi.getType() == SERVICE) {
            var srvName = pi.getName();

            if (srvName != null) {
                var _s = registry.getServices().stream().filter(s -> srvName.equals(s.getName())).findAny();

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
     * @return the intercept points of interceptors that must not be executed on the
     *         exchange
     */
    public static InterceptPoint[] dontIntercept(PluginsRegistry registry, HttpServerExchange exchange) {
        var hs = handlingService(registry, exchange);

        return hs == null ? new InterceptPoint[0] : dontIntercept(hs);
    }

    @SuppressWarnings("rawtypes")
    private static final LoadingCache<ExchangeTypeResolver, Type> RC = CacheFactory.createHashMapLoadingCache(plugin -> plugin.requestType());

    @SuppressWarnings("rawtypes")
    private static final LoadingCache<ExchangeTypeResolver, Type> SC = CacheFactory.createHashMapLoadingCache(plugin -> plugin.responseType());

    /**
     * Plugin.requestType() is heavy. This helper methods speeds up invocation using
     * a cache
     *
     * @param plugin
     * @return
     */
    @SuppressWarnings("rawtypes")
    public static Type cachedRequestType(ExchangeTypeResolver plugin) {
        return RC.getLoading(plugin).get();
    }

    /**
     * Plugin.responseType() is heavy. This helper methods speeds up invocation
     * using a cache
     *
     * @param plugin
     * @return
     */
    @SuppressWarnings("rawtypes")
    public static Type cachedResponseType(ExchangeTypeResolver plugin) {
        return SC.getLoading(plugin).get();
    }
}
