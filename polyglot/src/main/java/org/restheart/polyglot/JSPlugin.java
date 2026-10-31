/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2026 SoftInstigate
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
package org.restheart.polyglot;

import java.util.Optional;

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.restheart.configuration.Configuration;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.polyglot.spi.GuestErrors;
import org.restheart.polyglot.spi.PluginDefinition;
import org.restheart.polyglot.spi.PluginModule;
import org.restheart.polyglot.spi.RhRequest;
import org.restheart.polyglot.spi.RhResponse;
import org.restheart.polyglot.spi.RhRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 * A JavaScript plugin: its definition, its SDK runtime and the pool of contexts it runs in.
 *
 * <p>{@link #invokeHandle} and {@link #invokeResolve} are what the service and interceptor
 * classes call: they import the module in the context, pick {@code handle} or {@code resolve},
 * hand the plugin the {@link RhRequest} and {@link RhResponse} adapters (or the raw Java
 * objects, for a plugin written before the SDK) and turn an exception into the response
 * {@link GuestErrors} decides.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public abstract class JSPlugin {
    protected static final Logger LOGGER = LoggerFactory.getLogger(JSPlugin.class);

    private static volatile Engine engine;

    /**
     * Returns the shared polyglot Engine, created on first access with the plugins classloader
     * (see {@link PolyglotThreadUtils}). Lazy rather than a static initializer: with the
     * platform-thread escape hatch on, creating it in {@code <clinit>} would deadlock, the
     * caller holding the class-init lock while the dedicated thread waits for it.
     */
    public static Engine engine() {
        if (engine == null) {
            synchronized (JSPlugin.class) {
                if (engine == null) {
                    try {
                        engine = PolyglotThreadUtils.run(Engine::create);
                    } catch (Exception e) {
                        throw new IllegalStateException("Error creating polyglot Engine", e);
                    }
                }
            }
        }
        return engine;
    }

    // read by reflection in PluginUtils.name(): a JS plugin has no @RegisterPlugin annotation,
    // and the registry matches the interceptors of a service by this name
    private final String name;
    private final PluginDefinition definition;
    private final RhRuntime runtime;
    private final Source moduleSource;
    private final Optional<MongoClient> mclient;
    private final Configuration configuration;

    protected ContextQueue contextQueue;

    /**
     * @param definition what the module declares
     * @param runtime the SDK runtime of the plugin
     * @param configuration the configuration, for the legacy {@code pluginArgs}
     * @param mclient the MongoDB client, for the legacy {@code mclient}
     */
    protected JSPlugin(PluginDefinition definition, RhRuntime runtime, Configuration configuration, Optional<MongoClient> mclient) {
        this.name = definition.name();
        this.definition = definition;
        this.runtime = runtime;
        this.moduleSource = PluginModule.source(definition.pluginPath());
        this.mclient = mclient == null ? Optional.empty() : mclient;
        this.configuration = configuration;
        this.contextQueue = new ContextQueue(engine(), runtime, definition.name(), configuration, LOGGER, this.mclient, definition.contextOptions());
    }

    public String name() {
        return name;
    }

    public String getDescription() {
        return definition.description();
    }

    public PluginDefinition definition() {
        return definition;
    }

    public RhRuntime runtime() {
        return runtime;
    }

    /**
     * Provides access to the context queue for executing JavaScript code within a scoped context.
     *
     * @return the context queue instance
     */
    protected ContextQueue contextQueue() {
        return this.contextQueue;
    }

    public Optional<MongoClient> mclient() {
        return mclient;
    }

    public Source moduleSource() {
        return moduleSource;
    }

    public Configuration configuration() {
        return configuration;
    }

    /**
     * Closes the contexts of the plugin, when it is undeployed.
     */
    public void close() {
        contextQueue.close();
    }

    /**
     * Runs the plugin's {@code handle(req, res)}.
     *
     * <p>An exception is answered here, see {@link GuestErrors}: a typed error with its status,
     * anything else with 500 and a log line with the plugin name.</p>
     *
     * @param request the request
     * @param response the response
     * @throws Exception when the context cannot be used at all
     */
    protected void invokeHandle(Request<?> request, Response<?> response) throws Exception {
        try {
            contextQueue.executeWithContext(ctx -> {
                var ns = PluginModule.namespace(ctx, moduleSource);
                var handle = PluginModule.handle(ns);
                handle.executeVoid(adaptRequest(request), adaptResponse(response));
            });
        } catch (Exception e) {
            GuestErrors.handle(e, response, name(), LOGGER);
        }
    }

    /**
     * Runs the plugin's {@code resolve(req, res)}.
     *
     * @param request the request
     * @param response the response
     * @return what resolve returned; false, with the request in error, when it threw or did not return a boolean
     */
    protected boolean invokeResolve(Request<?> request, Response<?> response) {
        try {
            return contextQueue.executeWithContext(ctx -> {
                var ns = PluginModule.namespace(ctx, moduleSource);
                var resolve = PluginModule.resolve(ns);

                if (resolve == null) {
                    LOGGER.error("interceptor {} has no resolve()", name());
                    request.setInError(true);
                    return false;
                }

                Value ret = resolve.execute(adaptRequest(request), adaptResponse(response));

                if (ret != null && ret.isBoolean()) {
                    return ret.asBoolean();
                } else {
                    LOGGER.error("resolve() of interceptor {} did not return a boolean", name());
                    request.setInError(true);
                    return false;
                }
            });
        } catch (Exception e) {
            LOGGER.error("error on interceptor {} resolve()", name(), e);
            request.setInError(true);
            return false;
        }
    }

    private Object adaptRequest(Request<?> request) {
        return definition.sdk() ? new RhRequest(request, definition.contentKind()) : request;
    }

    private Object adaptResponse(Response<?> response) {
        return definition.sdk() ? new RhResponse(response, definition.contentKind()) : response;
    }
}
