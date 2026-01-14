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
import java.util.concurrent.ArrayBlockingQueue;

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
 *       to avoid expensive context creation on every request</li>
 *   <li><b>Function Caching:</b> Evaluated JavaScript functions are cached in context bindings
 *       to eliminate repeated source evaluation overhead (see {@link #cacheHandleFunction} and 
 *       {@link #cacheResolveFunction})</li>
 *   <li><b>Value Sharing:</b> Contexts are created with {@code allowValueSharing(true)} to enable
 *       efficient value passing between Java and JavaScript without expensive marshalling. This is
 *       safe because plugin code is immutable and contexts don't share mutable state.</li>
 * </ul>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ContextQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextQueue.class);
    private static final String CACHED_HANDLE_KEY = "__cachedHandle";
    private static final String CACHED_RESOLVE_KEY = "__cachedResolve";
    
    private final int QUEUE_SIZE = 4 * Runtime.getRuntime().availableProcessors();
    private final ArrayBlockingQueue<Context> QUEUE = new ArrayBlockingQueue<>(QUEUE_SIZE);

    public ContextQueue(Engine engine, String name, Configuration conf, Logger logger, Optional<MongoClient> mclient, String modulesReplacements, Map<String, String> OPTS) {
        for (var c = 0; c < QUEUE_SIZE; c++) {
            QUEUE.add(newContext(engine, name, conf, logger, mclient, modulesReplacements, OPTS));
        }
    }

    public Context take() throws InterruptedException {
        return QUEUE.take();
    }

    public void release(Context ctx) {
        try {
            QUEUE.add(ctx);
        } catch(IllegalStateException ise) {
            try (ctx) {
                // queue is full
                LOGGER.warn("Error releasing Context in {}", Thread.currentThread().getName());
            }
        }
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

    public static Context newContext(Engine engine, String name, Configuration conf, Logger LOGGER, Optional<MongoClient> mclient, String modulesReplacements, Map<String, String> OPTS) {
        if (modulesReplacements!= null) {
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

        addBindings(ctx, name, conf, LOGGER, mclient);

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