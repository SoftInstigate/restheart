/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2022 SoftInstigate
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

import com.google.common.collect.Maps;
import com.mongodb.client.MongoClient;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.restheart.configuration.Configuration;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
import org.restheart.utils.CleanerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractJSPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJSPlugin.class);

    protected Map<String, String> contextOptions = new HashMap<>();

    protected Engine engine = Engine.create();

    protected String modulesReplacements;
    protected Source handleSource;

    protected String name;
    protected String pluginClass;
    protected String description;
    protected String uri;
    protected boolean secured;
    protected MATCH_POLICY matchPolicy;
    protected InterceptPoint interceptPoint;

    protected boolean isService;
    protected boolean isInterceptor;

    protected MongoClient mclient;
    protected Configuration conf;

    protected AbstractJSPlugin() {
        this.name = null;
        this.pluginClass = null;
        this.description = null;
        this.uri = null;
        this.secured = false;
        this.matchPolicy = null;
        this.interceptPoint = null;
        this.conf = null;
        this.isService = true;
        this.isInterceptor = false;

        // register cleaner
        CleanerUtils.get().cleaner().register(this, new State(this.ctxs));
    }

    protected AbstractJSPlugin(String name,
        String pluginClass,
        String description,
        String uri,
        boolean secured,
        MATCH_POLICY matchPolicy,
        InterceptPoint interceptPoint,
        Configuration conf,
        boolean isService,
        boolean isInterceptor) {
        this.name = name;
        this.pluginClass = pluginClass;
        this.description = description;
        this.uri = uri;
        this.secured = secured;
        this.matchPolicy = matchPolicy;
        this.interceptPoint = interceptPoint;
        this.conf = conf;
        this.isService = isService;
        this.isInterceptor = isInterceptor;

        // register cleaner
        CleanerUtils.get().cleaner().register(this, new State(this.ctxs));
    }

    public static Context context(Engine engine, Map<String, String> OPTS) {
        return Context.newBuilder().engine(engine)
            .allowAllAccess(true)
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(className -> true)
            .allowIO(true)
            .allowExperimentalOptions(true)
            .options(OPTS)
            .build();
    }

    public String getName() {
        return name;
    }

    public String getPluginClass() {
        return pluginClass;
    }

    public String getUri() {
        return uri;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSecured() {
        return secured;
    }

    public MATCH_POLICY getMatchPolicy() {
        return matchPolicy;
    }

    public InterceptPoint getInterceptPoint() {
        return interceptPoint;
    }

    public static void addBindings(Context ctx,
        String pluginName,
        Configuration conf,
        Logger LOGGER,
        MongoClient mclient) {
        ctx.getBindings("js").putMember("LOGGER", LOGGER);

        if (mclient != null) {
            ctx.getBindings("js").putMember("mclient", mclient);
        }

        var args = conf != null
            ? conf.getOrDefault(pluginName, new HashMap<String, Object>())
            : new HashMap<String, Object>();

        ctx.getBindings("js").putMember("pluginArgs", args);
    }

    // cache Context and handles for performace
    // each working thread is associates with one context and one handle
    // because js Context does not allow multithreaded access
    protected Map<String, Context> ctxs = Maps.newHashMap();
    protected Map<String, Value> handles = Maps.newHashMap();

    /**
     *
     * @return the Context associated with this thread. If not existing, it instanitates it.
     */
    protected Context ctx() {
        var workingThreadName = Thread.currentThread().getName();

        if (this.ctxs.get(workingThreadName) == null) {
            var ctx = context(engine, contextOptions);
            this.ctxs.put(workingThreadName, ctx);

            addBindings(ctx, this.name, this.conf, LOGGER, this.mclient);
        }

        return this.ctxs.get(workingThreadName);
    }

    /**
     *
     * @return the handle Value associated with this thread. If not existing, it instanitates it.
     */
    protected Value _handle() {
        var workingThreadName = Thread.currentThread().getName();

        if (this.handles.containsKey(workingThreadName)) {
            return this.handles.get(workingThreadName);
        } else {
            var handle = ctx().eval(this.handleSource);
            this.handles.put(workingThreadName, handle);
            return handle;
        }
    }

    // for cleaning
    protected static class State implements Runnable {
        private Map<String, Context> ctxs;

        State(Map<String, Context> ctxs) {
            // initialize State needed for cleaning action
            this.ctxs = ctxs;
        }

        public void run() {
            if (this.ctxs != null) {
                this.ctxs.entrySet().stream().map(e -> e.getValue())
                .filter(ctx -> ctx != null)
                .forEach(ctx -> {
                    try {
                        ctx.close();
                    } catch(Throwable t) {
                        // nothing to do
                    }
                });
            }
        }
    }
}

