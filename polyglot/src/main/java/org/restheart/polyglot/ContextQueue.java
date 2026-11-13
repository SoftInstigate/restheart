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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.restheart.configuration.Configuration;
import org.restheart.polyglot.spi.PluginModule;
import org.restheart.polyglot.spi.RhRuntime;
import org.restheart.polyglot.spi.SdkFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 * Manages a pool of GraalVM Polyglot Context instances for executing JavaScript plugins.
 *
 * <p>Performance Optimizations:</p>
 * <ul>
 *   <li><b>Context Pooling:</b> Maintains a pool of pre-initialized contexts (one per CPU core)
 *       for reuse. When pool is empty, creates context on-demand to avoid blocking virtual threads</li>
 *   <li><b>Module Caching:</b> The plugin module is imported once per context and its namespace
 *       kept in the context bindings, see {@link PluginModule#namespace}</li>
 *   <li><b>Value Sharing:</b> Contexts are created with {@code allowValueSharing(true)} to enable
 *       efficient value passing between Java and JavaScript without expensive marshalling. This is
 *       safe because plugin code is immutable and contexts don't share mutable state.</li>
 *   <li><b>Virtual Thread Friendly:</b> Non-blocking pool with on-demand context creation ensures
 *       virtual threads never block waiting for contexts</li>
 * </ul>
 *
 * <p>Every context gets the SDK: the {@link RhRuntime} of the plugin as the global
 * {@code __restheart}, and the {@link SdkFileSystem} that serves {@code restheart:sdk}. The
 * globals of the plugins written before the SDK, {@code LOGGER}, {@code mclient} and
 * {@code pluginArgs}, stay as deprecated aliases.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ContextQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextQueue.class);

    private final int POOL_SIZE = 1 * Runtime.getRuntime().availableProcessors();
    private final ConcurrentLinkedQueue<Context> pool = new ConcurrentLinkedQueue<>();

    // Context creation parameters - stored for on-demand creation
    private final Engine engine;
    private final RhRuntime runtime;
    private final String name;
    private final Configuration conf;
    private final Logger logger;
    private final Optional<MongoClient> mclient;
    private final Map<String, String> OPTS;

    /**
     * A queue for a plugin that has no SDK runtime: the legacy globals only.
     *
     * @param modulesReplacements ignored, the option was removed in GraalVM 25.1
     */
    public ContextQueue(Engine engine, String name, Configuration conf, Logger logger, Optional<MongoClient> mclient, String modulesReplacements, Map<String, String> OPTS) {
        this(engine, null, name, conf, logger, mclient, OPTS);
    }

    /**
     * @param engine the shared engine
     * @param runtime the SDK runtime of the plugin, null for the legacy globals only
     * @param name the plugin name
     * @param conf the configuration, for {@code pluginArgs}
     * @param logger the {@code LOGGER} global
     * @param mclient the {@code mclient} global
     * @param OPTS the context options
     */
    public ContextQueue(Engine engine, RhRuntime runtime, String name, Configuration conf, Logger logger, Optional<MongoClient> mclient, Map<String, String> OPTS) {
        this.engine = engine;
        this.runtime = runtime;
        this.name = name;
        this.conf = conf;
        this.logger = logger;
        this.mclient = mclient;
        this.OPTS = OPTS;

        // Pre-populate the pool: each Context is created, entered, bound, left and offered.
        // Truffle operations need the plugins classloader, see PolyglotThreadUtils.
        try {
            PolyglotThreadUtils.run(() -> {
                populatePool();
                return null;
            });
        } catch (Exception e) {
            throw new IllegalStateException("Error pre-populating polyglot context pool", e);
        }
    }

    private void populatePool() {
        for (var c = 0; c < POOL_SIZE; c++) {
            pool.offer(newContext());
        }
    }

    /**
     * Gets a context from the pool, or creates a new one if pool is empty.
     * Never blocks - virtual thread friendly.
     *
     * @return a context ready for use
     */
    private Context acquire() {
        Context ctx = pool.poll();
        if (ctx == null) {
            LOGGER.debug("Pool empty, creating context on-demand");
            ctx = newContext();
        }
        return ctx;
    }

    /**
     * Returns a context to the pool if there's space, otherwise closes it.
     *
     * <p>The queue is unbounded, so the bound is checked here: without it a burst of requests
     * beyond the pool size would leave its on-demand contexts in the pool for good.</p>
     *
     * @param ctx the context to release
     */
    private void release(Context ctx) {
        if (pool.size() >= POOL_SIZE || !pool.offer(ctx)) {
            try {
                PolyglotThreadUtils.run(() -> {
                    ctx.close();
                    return null;
                });
                LOGGER.debug("Pool full, closed excess context");
            } catch (Exception e) {
                LOGGER.warn("Error closing excess context", e);
            }
        }
    }

    /**
     * Closes every pooled context, when the plugin is undeployed.
     */
    public void close() {
        Context ctx;
        while ((ctx = pool.poll()) != null) {
            var c = ctx;
            try {
                PolyglotThreadUtils.run(() -> {
                    c.close(true);
                    return null;
                });
            } catch (Exception e) {
                LOGGER.warn("Error closing context of {}", name, e);
            }
        }
    }

    /**
     * Executes a task with a context following GraalVM's enter/leave pattern.
     * Gets context from pool (or creates on-demand), enters it, executes task, leaves and returns to pool.
     * This pattern ensures proper context lifecycle management as recommended by GraalVM documentation.
     *
     * @param task the task to execute with the context
     * @param <T> the return type
     * @return the result of the task
     * @throws Exception if the task throws an exception
     */
    public <T> T executeWithContext(ContextTask<T> task) throws Exception {
        // On the calling thread, the request's virtual thread included: only the classloader is
        // set around the Truffle operations, see PolyglotThreadUtils.
        return PolyglotThreadUtils.run(() -> {
            Context ctx = acquire();
            try {
                ctx.enter();
                try {
                    return task.run(ctx);
                } finally {
                    ctx.leave();
                }
            } finally {
                release(ctx);
            }
        });
    }

    /**
     * Executes a task with a context (void return variant).
     *
     * @param task the task to execute with the context
     * @throws Exception if the task throws an exception
     */
    public void executeWithContext(VoidContextTask task) throws Exception {
        PolyglotThreadUtils.run(() -> {
            Context ctx = acquire();
            try {
                ctx.enter();
                try {
                    task.run(ctx);
                } finally {
                    ctx.leave();
                }
            } finally {
                release(ctx);
            }
            return null;
        });
    }

    /**
     * Functional interface for tasks that take a context and return a value.
     */
    @FunctionalInterface
    public interface ContextTask<T> {
        T run(Context ctx) throws Exception;
    }

    /**
     * Functional interface for tasks that take a context and return void.
     */
    @FunctionalInterface
    public interface VoidContextTask {
        void run(Context ctx) throws Exception;
    }

    /**
     * The namespace of the plugin module in a context, imported once per context.
     *
     * @param ctx an entered context
     * @param moduleSource the source of {@link PluginModule#source}
     * @return the namespace
     */
    public static Value cacheModule(Context ctx, Source moduleSource) {
        return PluginModule.namespace(ctx, moduleSource);
    }

    private Context newContext() {
        return newContext(engine, runtime, name, conf, logger, mclient, OPTS);
    }

    /**
     * A context with the legacy globals only, no SDK.
     *
     * @param modulesReplacements ignored, the option was removed in GraalVM 25.1
     */
    public static Context newContext(Engine engine, String name, Configuration conf, Logger logger, Optional<MongoClient> mclient, String modulesReplacements, Map<String, String> OPTS) {
        return newContext(engine, null, name, conf, logger, mclient, OPTS);
    }

    /**
     * A new context with the default bindings ({@code LOGGER}, {@code mclient},
     * {@code pluginArgs}, {@code __restheart}) in place, left, ready to be entered by its user.
     *
     * <p>The bindings are added here, whoever asks for the context: the pool at start-up and
     * {@link #acquire()} when the pool is empty under load. A context created on demand without
     * them answered {@code ReferenceError: LOGGER is not defined} to the requests beyond the
     * pool size, and, once released, kept doing so from the pool.</p>
     *
     * @param engine the shared engine
     * @param runtime the SDK runtime of the plugin, null for the legacy globals only
     * @param name the plugin name
     * @param conf the configuration, for {@code pluginArgs}
     * @param logger the {@code LOGGER} global
     * @param mclient the {@code mclient} global
     * @param OPTS the context options
     * @return the context
     */
    public static Context newContext(Engine engine, RhRuntime runtime, String name, Configuration conf, Logger logger, Optional<MongoClient> mclient, Map<String, String> OPTS) {
        var io = runtime == null
                ? IOAccess.ALL
                : IOAccess.newBuilder()
                        .fileSystem(new SdkFileSystem(runtime.pluginRoot(), runtime.sdkModuleSource()))
                        .allowHostSocketAccess(true)
                        .build();

        var ctx = Context.newBuilder().engine(engine)
                .allowAllAccess(true)
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(className -> true)
                .allowIO(io)
                .allowExperimentalOptions(true)
                .allowValueSharing(true)  // Enable value sharing to reduce marshalling overhead
                .options(OPTS == null ? Map.of() : OPTS)
                .build();

        // the bindings need an entered context
        ctx.enter();
        try {
            addBindings(ctx, name, conf, logger, mclient);
            if (runtime != null) {
                ctx.getBindings("js").putMember(RhRuntime.GLOBAL, runtime);
            }
        } finally {
            ctx.leave();
        }

        return ctx;
    }

    /**
     * Adds the legacy bindings (LOGGER, mclient, pluginArgs) to an already-entered
     * context. Must only be called between ctx.enter() and ctx.leave().
     *
     * @deprecated the SDK replaces them: {@code logger}, {@code mongo} and {@code config}
     *             imported from {@code restheart:sdk}
     */
    @Deprecated
    public static void addBindings(Context ctx,
                                   String pluginName,
                                   Configuration conf,
                                   Logger logger,
                                   Optional<MongoClient> mclient) {
        ctx.getBindings("js").putMember("LOGGER", logger);

        if (mclient != null && mclient.isPresent()) {
            ctx.getBindings("js").putMember("mclient", mclient.get());
        }

        var args = conf != null
                ? conf.getOrDefault(pluginName, new HashMap<>())
                : new HashMap<String, Object>();

        ctx.getBindings("js").putMember("pluginArgs", args);
    }
}
