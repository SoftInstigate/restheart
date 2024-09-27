/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2024 SoftInstigate
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

import com.mongodb.client.MongoClient;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.io.IOAccess;
import org.restheart.configuration.Configuration;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;
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

    protected Optional<MongoClient> mclient;
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
    }

    public static Context context(Engine engine, Map<String, String> OPTS) {
        return Context.newBuilder().engine(engine)
            .allowAllAccess(true)
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(className -> true)
            .allowIO(IOAccess.ALL)
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
        Optional<MongoClient> mclient) {
        ctx.getBindings("js").putMember("LOGGER", LOGGER);

        if (mclient.isPresent()) {
            ctx.getBindings("js").putMember("mclient", mclient.get());
        }

        var args = conf != null
            ? conf.getOrDefault(pluginName, new HashMap<String, Object>())
            : new HashMap<String, Object>();

        ctx.getBindings("js").putMember("pluginArgs", args);
    }

    /**
     *
     * @return the Context
     */
    protected Context ctx() {
        var ret = context(engine, contextOptions);
        addBindings(ret, pluginClass, conf, LOGGER, mclient);
        return ret;
    }
}
