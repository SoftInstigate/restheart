/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2021 SoftInstigate
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

import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;

public abstract class AbstractJSPlugin {
    protected String name;
    protected String pluginClass;
    protected String description;
    protected String uri;
    protected boolean secured;
    protected MATCH_POLICY matchPolicy;
    protected InterceptPoint interceptPoint;

    // TODO remove this and make fields final
    protected AbstractJSPlugin() {
        this.name = null;
        this.pluginClass = null;
        this.description = null;
        this.uri = null;
        this.secured = false;
        this.matchPolicy = null;
        this.interceptPoint = null;
    }

    protected AbstractJSPlugin(String name,
        String pluginClass,
        String description,
        String uri,
        boolean secured,
        MATCH_POLICY matchPolicy,
        InterceptPoint interceptPoint) {
        this.name = name;
        this.pluginClass = pluginClass;
        this.description = description;
        this.uri = uri;
        this.secured = secured;
        this.matchPolicy = matchPolicy;
        this.interceptPoint = interceptPoint;
    }

    protected Context context(Engine engine, Map<String, String> OPTS) {
        return Context.newBuilder().engine(engine).allowAllAccess(true)
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
}
