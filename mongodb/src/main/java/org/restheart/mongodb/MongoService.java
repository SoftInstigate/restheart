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
import org.bson.codecs.ByteArrayCodec;
import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.ansi;
import org.restheart.ConfigurationException;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.PipelinedWrappingHandler;
import org.restheart.handlers.exchange.ByteArrayRequest;
import org.restheart.handlers.exchange.ByteArrayResponse;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MONGO_MOUNT_WHAT_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MONGO_MOUNT_WHERE_KEY;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.mongodb.handlers.CORSHandler;
import org.restheart.mongodb.handlers.OptionsHandler;
import org.restheart.mongodb.handlers.RequestDispatcherHandler;
import org.restheart.mongodb.handlers.injectors.AccountInjector;
import org.restheart.mongodb.handlers.injectors.BodyInjector;
import org.restheart.mongodb.handlers.injectors.ClientSessionInjector;
import org.restheart.mongodb.handlers.injectors.CollectionPropsInjector;
import org.restheart.mongodb.handlers.injectors.DbPropsInjector;
import org.restheart.mongodb.handlers.injectors.ETagPolicyInjector;
import org.restheart.mongodb.handlers.injectors.BsonRequestInitializer;
import org.restheart.mongodb.handlers.metrics.MetricsInstrumentationHandler;
import org.restheart.mongodb.utils.URLUtils;
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
        defaultURI = "/",
        priority = Integer.MIN_VALUE)
public class MongoService implements Service {
    private static final Logger LOGGER = LoggerFactory
            .getLogger(MongoService.class);

    private final PipelinedHandler handlerPipeline;

    private final String myURI;

    private final boolean mongoDbInitialized;

    public MongoService() {
        if (!MongoClientSingleton.isInitialized()) {
            LOGGER.error("Service mongo is not initialized. "
                    + "Make sure that mongoInitializer is enabled "
                    + "and executed successfully");
            this.mongoDbInitialized = false;
            this.myURI = null;
            this.handlerPipeline = null;
        } else {
            this.mongoDbInitialized = true;
            this.myURI = myURI();
            this.handlerPipeline = getBasePipeline();
        }
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        return true;
    }

    @Override
    public void handle(HttpServerExchange exchange) throws Exception {
        if (mongoDbInitialized) {
            this.handlerPipeline.handleRequest(exchange);
        } else {
           LOGGER.error("Service mongo is unavailabe");
           ByteArrayResponse.wrap(exchange).setInError(true);
            ByteArrayResponse.wrap(exchange).setStatusCode(500);
        }
    }

    /**
     * getHandlersPipe
     *
     * @return a GracefulShutdownHandler
     */
    private PipelinedHandler getBasePipeline()
            throws ConfigurationException {
        var rootHandler = path();

        ClientSessionInjector.build(
                PipelinedHandler.pipe(
                        new DbPropsInjector(),
                        new CollectionPropsInjector(),
                        new ETagPolicyInjector(),
                        RequestDispatcherHandler.getInstance()));

        PipelinedHandler corePipeline = PipelinedHandler.pipe(
                new AccountInjector(),
                ClientSessionInjector.getInstance());

        PathTemplateHandler pathsTemplates = pathTemplate(false);

        // check that all mounts are either all paths or all path templates
        boolean allPathTemplates = MongoServiceConfiguration.get().getMongoMounts()
                .stream()
                .map(m -> (String) m.get(MONGO_MOUNT_WHERE_KEY))
                .allMatch(url -> isPathTemplate(url));

        boolean allPaths = MongoServiceConfiguration.get().getMongoMounts()
                .stream()
                .map(m -> (String) m.get(MONGO_MOUNT_WHERE_KEY))
                .allMatch(url -> !isPathTemplate(url));

        final PipelinedHandler basePipeline = PipelinedHandler.pipe(
                new MetricsInstrumentationHandler(),
                new CORSHandler(),
                new OptionsHandler(),
                new BodyInjector(),
                corePipeline);

        if (!allPathTemplates && !allPaths) {
            LOGGER.error("No mongo resource mounted! Check your mongo-mounts."
                    + " where url must be either all absolute paths"
                    + " or all path templates");
        } else {
            MongoServiceConfiguration.get().getMongoMounts().stream().forEach(m -> {
                var uri = resolveURI((String) m.get(MONGO_MOUNT_WHERE_KEY));
                var db = (String) m.get(MONGO_MOUNT_WHAT_KEY);

                PipelinedHandler pipeline = new BsonRequestInitializer(
                        uri,
                        db,
                        true,
                        MongoServiceConfiguration.get().getAggregationCheckOperators(),
                        basePipeline);

                if (allPathTemplates) {
                    pathsTemplates.add(uri, pipeline);
                } else {
                    rootHandler.addPrefixPath(uri, pipeline);
                }

                LOGGER.info(ansi().fg(GREEN)
                        .a("URI {} bound to MongoDB resource {}")
                        .reset().toString(), uri, db);
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

        if (MongoServiceConfiguration.get().getPluginsArgs() != null) {
            var myconf = MongoServiceConfiguration.get().getPluginsArgs().get("mongo");

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
