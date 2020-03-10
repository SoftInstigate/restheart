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

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.pathTemplate;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathTemplateHandler;
import java.util.Map;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.Color.MAGENTA;
import static org.fusesource.jansi.Ansi.ansi;
import org.restheart.ConfigurationException;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.PipelinedWrappingHandler;
import static org.restheart.mongodb.ConfigurationKeys.MONGO_MOUNT_WHAT_KEY;
import static org.restheart.mongodb.ConfigurationKeys.MONGO_MOUNT_WHERE_KEY;
import org.restheart.mongodb.db.MongoDBClientSingleton;
import org.restheart.mongodb.handlers.CORSHandler;
import org.restheart.mongodb.handlers.OptionsHandler;
import org.restheart.mongodb.handlers.RequestDispatcherHandler;
import org.restheart.mongodb.handlers.injectors.AccountInjectorHandler;
import org.restheart.mongodb.handlers.injectors.BodyInjectorHandler;
import org.restheart.mongodb.handlers.injectors.ClientSessionInjectorHandler;
import org.restheart.mongodb.handlers.injectors.CollectionPropsInjectorHandler;
import org.restheart.mongodb.handlers.injectors.DbPropsInjectorHandler;
import org.restheart.mongodb.handlers.injectors.RequestContextInjectorHandler;
import org.restheart.mongodb.handlers.metrics.MetricsInstrumentationHandler;
import org.restheart.mongodb.handlers.metrics.TracingInstrumentationHandler;
import org.restheart.mongodb.utils.URLUtils;
import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "mongo",
        description = "handles request to mongodb resources",
        enabledByDefault = true,
        defaultURI = "/")
public class MongoService implements Service {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(MongoService.class);

    private final PipelinedHandler handlerPipeline;

    private final String myURI;

    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public MongoService(Map<String, Object> confArgs) {
        Configuration.init(confArgs);
        this.myURI = myURI();
        this.handlerPipeline = getHandlersPipeline();
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return true;
    }

    @Override
    public void handle(HttpServerExchange exchange) throws Exception {
        this.handlerPipeline.handleRequest(exchange);
    }

    /**
     * getHandlersPipe
     *
     * @return a GracefulShutdownHandler
     */
    private PipelinedHandler getHandlersPipeline()
            throws ConfigurationException {
        var rootHandler = path();

        try {
            MongoDBClientSingleton.init(Configuration.get().getMongoUri());

            LOGGER.info("MongoDB connection pool initialized");
            LOGGER.info("MongoDB version {}",
                    ansi().fg(MAGENTA).a(MongoDBClientSingleton
                            .getServerVersion())
                            .reset().toString());

            if (MongoDBClientSingleton.isReplicaSet()) {
                LOGGER.info("MongoDB is a replica set");
            } else {
                LOGGER.warn("MongoDB is a standalone instance, use a replica set in production");
            }

        } catch (Throwable t) {
            throw new ConfigurationException("\"Error connecting to MongoDB.");
        }

        ClientSessionInjectorHandler.build(
                PipelinedHandler.pipe(
                        new DbPropsInjectorHandler(),
                        new CollectionPropsInjectorHandler(),
                        RequestDispatcherHandler.getInstance()));

        PipelinedHandler corePipeline
                = PipelinedHandler.pipe(
                        new AccountInjectorHandler(),
                        ClientSessionInjectorHandler.getInstance());

        PathTemplateHandler pathsTemplates = pathTemplate(false);

        // check that all mounts are either all paths or all path templates
        boolean allPathTemplates = Configuration.get().getMongoMounts()
                .stream()
                .map(m -> (String) m.get(MONGO_MOUNT_WHERE_KEY))
                .allMatch(url -> isPathTemplate(url));

        boolean allPaths = Configuration.get().getMongoMounts()
                .stream()
                .map(m -> (String) m.get(MONGO_MOUNT_WHERE_KEY))
                .allMatch(url -> !isPathTemplate(url));

        final PipelinedHandler basePipeline = PipelinedHandler.pipe(
                new MetricsInstrumentationHandler(),
                new TracingInstrumentationHandler(),
                new CORSHandler(),
                new OptionsHandler(),
                new BodyInjectorHandler(),
                corePipeline);

        if (!allPathTemplates && !allPaths) {
            LOGGER.error("No mongo resource mounted! Check your mongo-mounts."
                    + " where url must be either all absolute paths"
                    + " or all path templates");
        } else {
            Configuration.get().getMongoMounts().stream().forEach(m -> {
                var uri = resolveURI((String) m.get(MONGO_MOUNT_WHERE_KEY));
                var db = (String) m.get(MONGO_MOUNT_WHAT_KEY);

                PipelinedHandler pipeline = new RequestContextInjectorHandler(
                        uri,
                        db,
                        true,
                        Configuration.get().getAggregationCheckOperators(),
                        basePipeline);

                if (allPathTemplates) {
                    pathsTemplates.add(uri, pipeline);
                } else {
                    rootHandler.addPrefixPath(uri, pipeline);
                }

                LOGGER.info(ansi().fg(GREEN).a("URI {} bound to MongoDB resource {}").reset().toString(), uri, db);
            });

            if (allPathTemplates) {
                rootHandler.addPrefixPath(myURI(), pathsTemplates);
            }
        }

        return PipelinedWrappingHandler.wrap(rootHandler);
    }

    private static boolean isPathTemplate(final String url) {
        return (url == null)
                ? false
                : url.contains("{") && url.contains("}");
    }

    /**
     *
     * @param uri
     * @return the URI composed of serviceUri + uri
     */
    private String resolveURI(String uri) {
        if ("".equals(myURI) || "/".equals(myURI)) {
            return uri;
        } else if (uri == null) {
            return myURI;
        } else {
            return myURI.concat(uri);
        }
    }

    private String myURI() {
        Object uri;

        if (Configuration.get().getPluginsArgs() != null) {
            var myconf = Configuration.get().getPluginsArgs().get("mongo");

            if (myconf != null
                    && myconf.containsKey("uri")
                    && !"/".equals(myconf.get("uri"))) {
                uri = myconf.get("uri");
            } else {
                uri = PluginUtils.defaultURI(this);
            }
        } else {
            uri = PluginUtils.defaultURI(this);
        }

        if (uri instanceof String) {
            var _uri = (String) uri;
            // make sure myURI does not end with /
            _uri = URLUtils.removeTrailingSlashes(_uri);

            return _uri;
        } else {
            throw new IllegalArgumentException("Wrong 'uri' configuration of "
                    + "mongo service.");
        }
    }
}
