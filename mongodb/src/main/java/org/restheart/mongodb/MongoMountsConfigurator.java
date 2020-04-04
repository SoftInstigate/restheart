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
package org.restheart.mongodb;

import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.PathMatcher;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.exchange.PipelineBranchInfo;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MONGO_MOUNT_WHAT_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MONGO_MOUNT_WHERE_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.PLUGINS_ARGS_KEY;
import org.restheart.mongodb.utils.URLUtils;
import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.MongoMount;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.PluginUtils;

/**
 * Configures the MongoMounts in BsonRequestInitializer
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "mongoMountsConfigurator",
        description = "processes the mongo-mounts configuration",
        initPoint = InitPoint.AFTER_STARTUP,
        priority = -10)
public class MongoMountsConfigurator implements Initializer {
    private static final String BSON_REQUEST_INITIALIZER_CLASS
            = "org.restheart.handlers.BsonRequestInitializer";

    private static final String PIPELINE_BRANCH_INFO_INJECTOR_CLASS
            = "org.restheart.handlers.injectors.PipelineBranchInfoInjector";

    private PluginsRegistry pluginsRegistry;
    private boolean mongoSrvEnabled = false;
    private String mongoSrvUri;

    @InjectConfiguration(scope = ConfigurationScope.ALL)
    @InjectPluginsRegistry
    public void init(Map<String, Object> confArgs,
            PluginsRegistry pluginsRegistry) {
        MongoServiceConfiguration.init(confArgs);

        this.pluginsRegistry = pluginsRegistry;

        this.mongoSrvEnabled = isMongoEnabled(confArgs);

        this.mongoSrvUri = getUri(confArgs);
    }

    @Override
    public void init() {
        if (!this.mongoSrvEnabled) {
            return;
        }

        var _mp = findMongoPipeline(pluginsRegistry.getRootPathHandler());

        if (!_mp.isPresent()) {
            return;
        }

        addMongoMounts(
                findBsonRequestInitializer(_mp.get()),
                getMongoMounts(this.mongoSrvUri));
    }

    @SuppressWarnings("unchecked")
    private Set<MongoMount> getMongoMounts(String srvUri) {
        final var ret = new LinkedHashSet<MongoMount>();

        MongoServiceConfiguration.get().getMongoMounts()
                .stream()
                .forEachOrdered(e -> {
                    ret.add(new MongoMount(
                            (String) e.get(MONGO_MOUNT_WHAT_KEY),
                            resolveURI(srvUri,
                                    (String) e.get(MONGO_MOUNT_WHERE_KEY))));
                });

        return ret;
    }

    private Optional<PipelinedHandler> findMongoPipeline(PathHandler handler) {
        var pipelinePM = getPathMatcher(handler);

        var allPaths = pipelinePM.getPaths()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue));

        if (pipelinePM.getPrefixPath("/") != null) {
            allPaths.put("/", pipelinePM.getPrefixPath("/"));
        }

        return allPaths
                .entrySet()
                .stream()
                .filter(e -> e.getValue() instanceof PipelinedHandler)
                .map(e -> (PipelinedHandler) e.getValue())
                .filter(h -> getBranchInfo(h) != null)
                .filter(h -> "mongo".equals(getBranchInfo(h).getName()))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private PathMatcher<HttpHandler> getPathMatcher(PathHandler ph) {
        try {
            var clazz = Class.forName(PathHandler.class.getName());
            var idmF = clazz.getDeclaredField("pathMatcher");
            idmF.setAccessible(true);

            return (PathMatcher<HttpHandler>) idmF.get(ph);
        } catch (ClassNotFoundException
                | SecurityException
                | NoSuchFieldException
                | IllegalAccessException ex) {
            return null;
        }
    }

    private PipelinedHandler getNext(PipelinedHandler handler) {
        try {
            var clazz = Class.forName(PipelinedHandler.class.getName());
            var m = clazz.getDeclaredMethod("getNext");
            m.setAccessible(true);

            return (PipelinedHandler) m.invoke(handler);
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | SecurityException
                | InvocationTargetException
                | IllegalAccessException ex) {
            return null;
        }
    }

    private PipelineBranchInfo getBranchInfo(PipelinedHandler handler) {
        try {
            var clazz = Class.forName(PIPELINE_BRANCH_INFO_INJECTOR_CLASS);
            var f = clazz.getDeclaredField("pbi");
            f.setAccessible(true);

            return (PipelineBranchInfo) f.get(handler);
        } catch (ClassNotFoundException
                | NoSuchFieldException
                | SecurityException
                | IllegalAccessException ex) {
            return null;
        }
    }

    private PipelinedHandler findBsonRequestInitializer(PipelinedHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("argument cannot be null");
        } else if (BSON_REQUEST_INITIALIZER_CLASS
                .equals(handler.getClass().getName())) {
            return handler;
        } else {
            var next = getNext(handler);

            if (next != null) {
                return findBsonRequestInitializer(next);
            } else {
                throw new IllegalStateException("Cannot find "
                        + "BsonRequestInitializer in the pipeline");
            }
        }
    }

    private void addMongoMounts(PipelinedHandler bri, Set<MongoMount> mms) {
        try {
            var clazz = Class.forName("org.restheart.handlers.BsonRequestInitializer");
            var amm = clazz.getDeclaredMethod("addMongoMount", MongoMount.class);
            amm.setAccessible(true);

            for (var mm : mms) {
                amm.invoke(bri, mm);
            }
        } catch (ClassNotFoundException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException
                | SecurityException ex) {
            throw new RuntimeException("Error configuring mongo-mounts", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private String getUri(Map<String, Object> confArgs) {
        Map<String, Object> mongoConf = null;

        if (confArgs.get(PLUGINS_ARGS_KEY) != null
                && confArgs.get(PLUGINS_ARGS_KEY) instanceof Map) {
            var pa = (Map) confArgs.get(PLUGINS_ARGS_KEY);

            if (pa.get("mongo") instanceof Map) {
                mongoConf = (Map) pa.get("mongo");
            }
        }

        return PluginUtils.actualUri(mongoConf, MongoService.class);
    }

    /**
     *
     * @param uri
     * @return the URI composed of serviceUri + uri
     */
    private String resolveURI(String serviceURI, String uri) {
        if ("".equals(serviceURI) || "/".equals(serviceURI)) {
            return uri;
        } else if (uri == null) {
            return URLUtils.removeTrailingSlashes(serviceURI);
        } else {
            return URLUtils.removeTrailingSlashes(serviceURI.concat(uri));
        }
    }

    private boolean isMongoEnabled(Map<String, Object> confArgs) {
        if (confArgs.get(PLUGINS_ARGS_KEY) != null
                && confArgs.get(PLUGINS_ARGS_KEY) instanceof Map) {
            var pa = (Map) confArgs.get(PLUGINS_ARGS_KEY);

            if (pa.get("mongo") != null
                    && pa.get("mongo") instanceof Map) {
                var mc = (Map) pa.get("mongo");

                if (mc.get("enabled") != null
                        && mc.get("enabled") instanceof Boolean) {
                    return (Boolean) mc.get("enabled");
                }
            }
        }

        return true;
    }
}
