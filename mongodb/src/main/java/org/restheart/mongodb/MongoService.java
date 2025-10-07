/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.mongodb;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.fusesource.jansi.Ansi.Color.GREEN;
import static org.fusesource.jansi.Ansi.ansi;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.BadRequestException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.exchange.Request;
import org.restheart.handlers.PipelinedHandler;
import org.restheart.handlers.PipelinedWrappingHandler;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MONGO_MOUNT_WHAT_KEY;
import static org.restheart.mongodb.MongoServiceConfigurationKeys.MONGO_MOUNT_WHERE_KEY;
import org.restheart.mongodb.exchange.MongoRequestPropsInjector;
import org.restheart.mongodb.handlers.ErrorHandler;
import org.restheart.mongodb.handlers.OptionsHandler;
import org.restheart.mongodb.handlers.RequestDispatcherHandler;
import org.restheart.mongodb.handlers.injectors.ClientSessionInjector;
import org.restheart.mongodb.handlers.injectors.ETagPolicyInjector;
import org.restheart.mongodb.utils.MongoURLUtils;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.Service;
import org.restheart.utils.HttpStatus;
import org.restheart.utils.PluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

import static io.undertow.Handlers.path;
import static io.undertow.Handlers.pathTemplate;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.PathMatcher;
import io.undertow.util.PathTemplate;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.PathTemplateMatcher;

/**
 * The Service that handles requests to mongodb resources
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@RegisterPlugin(name = "mongo",
        description = "handles requests to mongodb resources",
        secure = true,
        enabledByDefault = true,
        defaultURI = "/",
        priority = Integer.MIN_VALUE)
public class MongoService implements Service<MongoRequest, MongoResponse> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoService.class);

    private String myURI = null;

    private PipelinedHandler pipeline;

    @Inject("mclient")
    private MongoClient mclient;

    /**
     * PathMatcher is used by the root PathHandler to route the call. Here we
     * use the same logic to identify the correct MongoMount in order to
     * correctly init the BsonRequest
     */
    private PathMatcher<MongoMount> mongoMounts = null;
    private PathTemplateMatcher<MongoMount> templateMongoMounts = null;

    @OnInit
    public void init() {
        this.myURI = myURI();
        this.pipeline = getBasePipeline();

        // check that all mounts are either all paths or all path templates
        boolean allPathTemplates = MongoServiceConfiguration.get().getMongoMounts()
            .stream()
            .map(m -> (String) m.get(MONGO_MOUNT_WHERE_KEY))
            .allMatch(MongoService::isPathTemplate);

        if (!allPathTemplates) {
            // init mongoMounts
            this.mongoMounts = new PathMatcher<>();
            getMongoMounts().stream().forEachOrdered(mm -> mongoMounts.addPrefixPath(mm.uri, mm));
        } else {
            // init templateMongoMounts
            this.templateMongoMounts = new PathTemplateMatcher<>();
            getMongoMounts().stream().forEachOrdered(mm -> templateMongoMounts.add(mm.uri, mm));
        }
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        // see method javadoc for more information
        resetRelativePath(request);

        if (mclient != null) {
            this.pipeline.handleRequest(request.getExchange());
        } else {
            final var error = "MongoDB is not available";

            response.setInError(500, error);
            LOGGER.error(error);
        }
    }

    /**
     * getHandlersPipe
     *
     * @return a GracefulShutdownHandler
     */
    private PipelinedHandler getBasePipeline() throws ConfigurationException {
        var rootHandler = path();

        var _pipeline = PipelinedWrappingHandler.wrap(new ErrorHandler(
            PipelinedHandler.pipe(
                new OptionsHandler(),
                ClientSessionInjector.build(),
                new ETagPolicyInjector(),
                RequestDispatcherHandler.getInstance())));

        // check that all mounts are either all paths or all path templates
        var allPathTemplates = MongoServiceConfiguration.get().getMongoMounts()
            .stream()
            .map(m -> (String) m.get(MONGO_MOUNT_WHERE_KEY))
            .allMatch(url -> isPathTemplate(url));

        var allPaths = MongoServiceConfiguration.get().getMongoMounts()
            .stream()
            .map(m -> (String) m.get(MONGO_MOUNT_WHERE_KEY))
            .allMatch(url -> !isPathTemplate(url));

        var pathsTemplates = pathTemplate(false);

        if (!allPathTemplates && !allPaths) {
            LOGGER.error("No mongo resource mounted! Check your mongo-mounts."
                    + " where url must be either all absolute paths"
                    + " or all path templates");
        } else {
            MongoServiceConfiguration.get().getMongoMounts().stream().forEach(m -> {
                var uri = resolveURI((String) m.get(MONGO_MOUNT_WHERE_KEY));
                var resource = (String) m.get(MONGO_MOUNT_WHAT_KEY);

                if (allPathTemplates) {
                    pathsTemplates.add(uri, _pipeline);
                } else {
                    rootHandler.addPrefixPath(uri, _pipeline);
                }

                LOGGER.trace("│   │  ├─ URI {} bound to MongoDB resource {}", uri, resource);
            });

            if (allPathTemplates) {
                rootHandler.addPrefixPath(myURI(), pathsTemplates);
            }
        }

        return PipelinedWrappingHandler.wrap(rootHandler);
    }

    private static boolean isPathTemplate(final String url) {
        return !PathTemplate.create(url).getParameterNames().isEmpty();
    }

    /**
     * Return the MongoRequest initializer
     *
     * @return
     */
    @Override
    public Consumer<HttpServerExchange> requestInitializer() {
        return e -> {
            var path = e.getRequestPath();

            var mm = mongoMountsMatch(e, path);

            if (mm != null) {
                MongoRequest.init(e, mm.uri, mm.resource);
            } else {
                LOGGER.warn("No MongoDB resource bound for {}. "
                        + "Check mongo service configuration: "
                        + "'mongo-mounts' and plugin arg 'uri'", path);
                throw new BadRequestException("No MongoDB resource bound for " + path, HttpStatus.SC_BAD_GATEWAY);
            }
        };
    }

    private MongoMount mongoMountsMatch(HttpServerExchange exchange, String path) {
        if (mongoMounts != null) {
            var mm = mongoMounts.match(path);
            return mm != null ? mm.getValue() : null;
        } else  if (templateMongoMounts != null) {
            var tmm = templateMongoMounts.match(path);
            exchange.putAttachment(PathTemplateMatch.ATTACHMENT_KEY, tmm);
            return tmm != null ? tmm.getValue() : null;
        } else {
            return null;
        }
    }

    @Override
    public Consumer<HttpServerExchange> responseInitializer() {
        return e -> {
            MongoResponse.init(e);

            // MongoRequestPropsInjector requires
            // that both MongoRequest and MongoResponse are initialized
            // so we need to inject propertis here

            MongoRequestPropsInjector.inject(e);
        };
    }

    @Override
    public Function<HttpServerExchange, MongoRequest> request() {
        return e -> MongoRequest.of(e);
    }

    @Override
    public Function<HttpServerExchange, MongoResponse> response() {
        return e -> MongoResponse.of(e);
    }

    private Set<MongoMount> getMongoMounts() {
        final var ret = new LinkedHashSet<MongoMount>();

        MongoServiceConfiguration.get().getMongoMounts()
                .stream()
                .forEachOrdered(e -> ret.add(new MongoMount((String) e.get(MONGO_MOUNT_WHAT_KEY), resolveURI((String) e.get(MONGO_MOUNT_WHERE_KEY)))));

        return ret;
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
            return MongoURLUtils.removeTrailingSlashes(myURI);
        } else {
            return MongoURLUtils.removeTrailingSlashes(myURI.concat(uri));
        }
    }

    private String myURI() {
        var uri = MongoServiceConfiguration.get().getUri();

        if (uri == null || "/".equals(uri)) {
            return PluginUtils.defaultURI(this);
        } else {
            return MongoURLUtils.removeTrailingSlashes(uri);
        }
    }

    /**
     * helper class to store mongo mounts info
     */
    private static record MongoMount(String resource, String uri) {
        public MongoMount(String resource, String uri) {
            if (uri == null) {
                throw new IllegalArgumentException("'where' cannot be null. check your 'mongo-mounts'.");
            }

            if (!uri.startsWith("/")) {
                throw new IllegalArgumentException("'where' must start with \"/\". check your 'mongo-mounts'");
            }

            if (resource == null) {
                throw new IllegalArgumentException("'what' cannot be null. check your 'mongo-mounts'.");
            }

            if (!uri.startsWith("/") && !uri.equals("*")) {
                throw new IllegalArgumentException("'what' must be * (all db resorces) or start with \"/\". (eg. /db/coll) check your 'mongo-mounts'");
            }

            this.resource = resource;
            this.uri = org.restheart.utils.URLUtils.removeTrailingSlashes(uri);
        }

        @Override
        public String toString() {
            return "MongoMount(" + uri + " -> " + resource + ")";
        }
    }

    /**
     * reset the exchange relative path to the request path
     *
     * this is needed because undertow PathHandler first uses the relative path
     * to match the request and then updates it. MongoService uses a second
     * instance of PathHandler that will fail matching the relative path that
     * was updated by restheart-core's root PathHandler
     *
     * @param request
     */
    @SuppressWarnings("rawtypes")
    private void resetRelativePath(Request request) {
        request.getExchange().setRelativePath(request.getPath());
    }
}
