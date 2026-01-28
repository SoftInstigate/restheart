/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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
 * Utility class providing helper methods for plugin management and introspection.
 * This class contains methods to extract metadata from plugins, handle plugin
 * annotations, retrieve plugin configurations, and manage plugin lifecycle operations.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PluginUtils {
    /**
     * Retrieves the intercept point for the given interceptor.
     * First checks the @RegisterPlugin annotation, then falls back to looking
     * for an interceptPoint field in the class.
     *
     * @param interceptor the interceptor to analyze
     * @return the intercept point as defined by the @RegisterPlugin annotation
     *         or the value of the interceptPoint field
     * @throws IllegalArgumentException if the intercept point is ANY (which is only
     *         allowed in the dontIntercept attribute)
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
     * Determines if the interceptor is required based on the @RegisterPlugin annotation.
     *
     * @param interceptor the interceptor to check
     * @return true if the interceptor is required, false otherwise, or null if
     *         the annotation is not present
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
     * Retrieves the interceptPoint field value from an Interceptor that is not
     * annotated with @RegisterPlugin. Uses reflection to find the field.
     *
     * @param clazz the class to search for the interceptPoint field
     * @param o the object instance to get the field value from
     * @return the InterceptPoint value from the field, or null if not found
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

    /**
     * Retrieves the initialization point for the given initializer from the
     * @RegisterPlugin annotation.
     *
     * @param initializer the initializer to analyze
     * @return the initialization point, or null if the annotation is not present
     */
    public static InitPoint initPoint(Initializer initializer) {
        var a = initializer.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return null;
        } else {
            return a.initPoint();
        }
    }

    /**
     * Determines if the interceptor requires content based on the @RegisterPlugin annotation.
     *
     * @param interceptor the interceptor to check
     * @return true if the interceptor requires content, false otherwise
     */
    public static boolean requiresContent(Interceptor<? extends Request<?>, ? extends Response<?>> interceptor) {
        var a = interceptor.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        if (a == null) {
            return false;
        } else {
            return a.requiresContent();
        }
    }

    /**
     * Retrieves the name of the plugin from the @RegisterPlugin annotation
     * or from a 'name' field if the annotation is not present.
     *
     * @param plugin the plugin to get the name from
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
     * Retrieves the name field value from a plugin that is not annotated
     * with @RegisterPlugin. Uses reflection to find the 'name' field.
     *
     * @param clazz the class to search for the name field
     * @param o the object instance to get the field value from
     * @return the name value from the field, or null if not found
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
     * Retrieves the default URI for the service from the @RegisterPlugin annotation.
     * If not explicitly set via defaultUri attribute, it defaults to /[service-name].
     *
     * @param service the service to get the default URI from
     * @return the service default URI, or null if the annotation is not present
     */
    @SuppressWarnings("rawtypes")
    public static String defaultURI(Service service) {
        var a = service.getClass().getDeclaredAnnotation(RegisterPlugin.class);

        return a == null ? null
                : a.defaultURI() == null || "".equals(a.defaultURI()) ? "/".concat(a.name()) : a.defaultURI();
    }

    /**
     * Retrieves the URI match policy for the service from the @RegisterPlugin annotation.
     *
     * @param service the service to get the match policy from
     * @return the URI match policy
     */
    @SuppressWarnings("rawtypes")
    public static MATCH_POLICY uriMatchPolicy(Service service) {
        return service.getClass().getDeclaredAnnotation(RegisterPlugin.class).uriMatchPolicy();
    }

    /**
     * Retrieves the default URI for the service class from the @RegisterPlugin annotation.
     * If not explicitly set via defaultUri attribute, it defaults to /[service-name].
     *
     * @param <P> the service type
     * @param serviceClass the service class to get the default URI from
     * @return the service default URI, or null if the annotation is not present
     */
    @SuppressWarnings("rawtypes")
    public static <P extends Service> String defaultURI(Class<P> serviceClass) {
        var a = serviceClass.getDeclaredAnnotation(RegisterPlugin.class);

        return a == null ? null
                : a.defaultURI() == null || "".equals(a.defaultURI()) ? "/".concat(a.name()) : a.defaultURI();
    }

    /**
     * Retrieves the actual URI for the service, either from configuration or
     * falling back to the default URI from the annotation.
     *
     * @param <P> the service type
     * @param conf the plugin configuration obtained from @Inject("conf")
     * @param serviceClass the class of the service
     * @return the actual service URI set in configuration or the defaultURI
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
     * Retrieves the intercept points that should be excluded when processing
     * requests handled by the given service.
     *
     * @param service the service to check
     * @return array of intercept points that must not be executed on requests
     *         handled by this service
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
     * Retrieves the authorizer type from the @RegisterPlugin annotation.
     * Defaults to ALLOWER if the annotation is not present or authorizer is null.
     *
     * @param authorizer the authorizer to check
     * @return the authorizer type (ALLOWER by default)
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

    /**
     * Determines if the service is blocking based on the @RegisterPlugin annotation.
     * Defaults to true (blocking) if the annotation is not present or service is null.
     *
     * @param service the service to check
     * @return true if the service is blocking, false if non-blocking
     */
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
     * Finds the service that is handling the given HTTP server exchange.
     *
     * @param registry the plugins registry containing all registered services
     * @param exchange the HTTP server exchange to analyze
     * @return the service handling the exchange, or null if the request is not
     *         handled by a service
     */
    public static Service<?, ?> handlingService(PluginsRegistry registry, HttpServerExchange exchange) {
        var pr = handlingServicePluginRecord(registry, exchange);

        return pr == null ? null : pr.getInstance();
    }

    /**
     * Finds the plugin record of the service that is handling the given HTTP server exchange.
     *
     * @param registry the plugins registry containing all registered services
     * @param exchange the HTTP server exchange to analyze
     * @return the plugin record of the service handling the exchange, or null if
     *         the request is not handled by a service
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
     * Retrieves the intercept points that should be excluded when processing
     * the given HTTP server exchange.
     *
     * @param registry the plugins registry containing all registered services
     * @param exchange the HTTP server exchange to analyze
     * @return array of intercept points that must not be executed on this exchange
     */
    public static InterceptPoint[] dontIntercept(PluginsRegistry registry, HttpServerExchange exchange) {
        var hs = handlingService(registry, exchange);

        return hs == null ? new InterceptPoint[0] : dontIntercept(hs);
    }

    /** Cache for plugin request types to improve performance of type resolution. */
    @SuppressWarnings("rawtypes")
    private static final LoadingCache<ExchangeTypeResolver, Type> RC = CacheFactory.createHashMapLoadingCache(plugin -> plugin.requestType());

    /** Cache for plugin response types to improve performance of type resolution. */
    @SuppressWarnings("rawtypes")
    private static final LoadingCache<ExchangeTypeResolver, Type> SC = CacheFactory.createHashMapLoadingCache(plugin -> plugin.responseType());

    /**
     * Retrieves the priority of a plugin from its @RegisterPlugin annotation.
     *
     * @param plugin the plugin to get the priority from
     * @return the plugin priority, or 10 (default) if annotation is not present
     */
    public static int priority(Plugin plugin) {
        if (plugin == null) return 10; // default priority
        var annotation = plugin.getClass().getDeclaredAnnotation(RegisterPlugin.class);
        return annotation != null ? annotation.priority() : 10; // default priority
    }

    /**
     * Retrieves the request type for the plugin using a cache for improved performance.
     * Plugin.requestType() is computationally expensive, so this helper method
     * speeds up invocation by caching results.
     *
     * @param plugin the plugin to get the request type from
     * @return the cached request type
     */
    @SuppressWarnings("rawtypes")
    public static Type cachedRequestType(ExchangeTypeResolver plugin) {
        return RC.getLoading(plugin).get();
    }

    /**
     * Retrieves the response type for the plugin using a cache for improved performance.
     * Plugin.responseType() is computationally expensive, so this helper method
     * speeds up invocation by caching results.
     *
     * @param plugin the plugin to get the response type from
     * @return the cached response type
     */
    @SuppressWarnings("rawtypes")
    public static Type cachedResponseType(ExchangeTypeResolver plugin) {
        return SC.getLoading(plugin).get();
    }
}
