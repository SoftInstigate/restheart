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

import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;

public abstract class AbstractJavaScriptPlugin {
    protected String name;
    protected String pluginClassName;
    protected String description;
    protected String uri;
    protected boolean secured;
    protected MATCH_POLICY matchPolicy;
    protected InterceptPoint interceptPoint;

    public String getName() {
        return name;
    }

    public String getPluginClassName() {
        return pluginClassName;
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
