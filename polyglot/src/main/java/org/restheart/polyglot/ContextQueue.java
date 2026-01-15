/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2025 SoftInstigate
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 * Manages a pool of GraalVM Polyglot Context instances for executing JavaScript plugins.
 *
 * <p>Performance Optimizations:</p>
 * <ul>
 *   <li><b>Context Pooling:</b> Maintains a pool of pre-initialized contexts (4 × CPU cores)
 *       for reuse. When pool is empty, creates context on-demand to avoid blocking virtual threads</li>
 *   <li><b>Function Caching:</b> Evaluated JavaScript functions are cached in context bindings
 *       to eliminate repeated source evaluation overhead (see {@link #cacheHandleFunction} and
 *       {@link #cacheResolveFunction})</li>
 *   <li><b>Value Sharing:</b> Contexts are created with {@code allowValueSharing(true)} to enable
 *       efficient value passing between Java and JavaScript without expensive marshalling. This is
 *       safe because plugin code is immutable and contexts don't share mutable state.</li>
 *   <li><b>Virtual Thread Friendly:</b> Non-blocking pool with on-demand context creation ensures
 *       virtual threads never block waiting for contexts</li>
 * </ul>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ContextQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextQueue.class);
    private static final String CACHED_HANDLE_KEY = "__cachedHandle";
    private static final String CACHED_RESOLVE_KEY = "__cachedResolve";

    private final int POOL_SIZE = 1 * Runtime.getRuntime().availableProcessors();
    private final ConcurrentLinkedQueue<Context> pool = new ConcurrentLinkedQueue<>();

    // Context creation parameters - stored for on-demand creation
    private final Engine engine;
    private final String name;
    private final Configuration conf;
    private final Logger logger;
    private final Optional<MongoClient> mclient;
    private final String modulesReplacements;
    private final Map<String, String> OPTS;

    public ContextQueue(Engine engine, String name, Configuration conf, Logger logger, Optional<MongoClient> mclient, String modulesReplacements, Map<String, String> OPTS) {
        this.engine = engine;
        this.name = name;
        this.conf = conf;
        this.logger = logger;
        this.mclient = mclient;
        this.modulesReplacements = modulesReplacements;
        this.OPTS = OPTS;

        // Pre-populate pool
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
     * @param ctx the context to release
     */
    private void release(Context ctx) {
        if (!pool.offer(ctx)) {
            // Pool is full, close the context
            ctx.close();
            LOGGER.debug("Pool full, closed excess context");
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
        Context ctx = acquire();
        ctx.enter();
        try {
            return task.run(ctx);
        } finally {
            ctx.leave();
            release(ctx);
        }
    }

    /**
     * Executes a task with a context (void return variant).
     *
     * @param task the task to execute with the context
     * @throws Exception if the task throws an exception
     */
    public void executeWithContext(VoidContextTask task) throws Exception {
        Context ctx = acquire();
        ctx.enter();
        try {
            task.run(ctx);
        } finally {
            ctx.leave();
            release(ctx);
        }
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
     * Caches an evaluated handle function in the context bindings for reuse.
     * This avoids re-evaluating the source on every request.
     *
     * @param ctx the context to cache the function in
     * @param handleSource the source to evaluate and cache
     * @return the cached Value that can be executed
     */
    public static Value cacheHandleFunction(Context ctx, Source handleSource) {
        var bindings = ctx.getBindings("js");
        if (!bindings.hasMember(CACHED_HANDLE_KEY)) {
            var handleValue = ctx.eval(handleSource);
            bindings.putMember(CACHED_HANDLE_KEY, handleValue);
            LOGGER.trace("Cached handle function in context");
            return handleValue;
        }
        return bindings.getMember(CACHED_HANDLE_KEY);
    }

    /**
     * Caches an evaluated resolve function in the context bindings for reuse.
     * This avoids re-evaluating the source on every request.
     *
     * @param ctx the context to cache the function in
     * @param resolveSource the source to evaluate and cache
     * @return the cached Value that can be executed
     */
    public static Value cacheResolveFunction(Context ctx, Source resolveSource) {
        var bindings = ctx.getBindings("js");
        if (!bindings.hasMember(CACHED_RESOLVE_KEY)) {
            var resolveValue = ctx.eval(resolveSource);
            bindings.putMember(CACHED_RESOLVE_KEY, resolveValue);
            LOGGER.trace("Cached resolve function in context");
            return resolveValue;
        }
        return bindings.getMember(CACHED_RESOLVE_KEY);
    }

    private Context newContext() {
        return newContext(engine, name, conf, logger, mclient, modulesReplacements, OPTS);
    }

    public static Context newContext(Engine engine, String name, Configuration conf, Logger logger, Optional<MongoClient> mclient, String modulesReplacements, Map<String, String> OPTS) {
        if (modulesReplacements != null) {
            LOGGER.trace("modules-replacements: {} ", modulesReplacements);
            OPTS.put("js.commonjs-core-modules-replacements", modulesReplacements);
        } else {
            OPTS.remove("js.commonjs-core-modules-replacements");
        }

        var ctx = Context.newBuilder().engine(engine)
            .allowAllAccess(true)
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(className -> true)
            .allowIO(IOAccess.ALL)
            .allowExperimentalOptions(true)
            .allowValueSharing(true)  // Enable value sharing to reduce marshalling overhead
            .options(OPTS)
            .build();

        addBindings(ctx, name, conf, logger, mclient);

        return ctx;
    }

    private static void addBindings(Context ctx,
        String pluginName,
        Configuration conf,
        Logger logger,
        Optional<MongoClient> mclient) {
        ctx.getBindings("js").putMember("LOGGER", logger);

        if (mclient.isPresent()) {
            ctx.getBindings("js").putMember("mclient", mclient.get());
        }

        var args = conf != null
            ? conf.getOrDefault(pluginName, new HashMap<>())
            : new HashMap<String, Object>();

        ctx.getBindings("js").putMember("pluginArgs", args);
    }
}
