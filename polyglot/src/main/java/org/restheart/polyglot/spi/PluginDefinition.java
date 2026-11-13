/*-
 * ========================LICENSE_START=================================
 * restheart-polyglot
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.polyglot.spi;

import java.nio.file.Path;
import java.util.Map;

import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.RegisterPlugin.MATCH_POLICY;

/**
 * What a plugin module declares about itself, read once at deploy time.
 *
 * <p>A plugin declared with {@code defineService} or {@code defineInterceptor} as the default
 * export is an SDK plugin ({@link #sdk()} is true): its {@code handle} and {@code resolve}
 * receive the {@link RhRequest} and {@link RhResponse} adapters. A plugin that exports
 * {@code options}, {@code handle} and {@code resolve} the way plugins did before the SDK is a
 * legacy plugin: it receives the raw Java request and response, as before.</p>
 *
 * <p>Nothing here is bound to a polyglot context: the functions are looked up again in every
 * context the plugin runs in, see {@link PluginModule}.</p>
 *
 * @param kind service or interceptor
 * @param sdk whether the plugin is declared through the SDK
 * @param name the plugin name
 * @param description the plugin description
 * @param uri the service uri, null for interceptors
 * @param secured whether the service requires authentication, false for interceptors
 * @param matchPolicy the service uri match policy, null for interceptors
 * @param interceptPoint the intercept point, null for services
 * @param contentKind the content kind
 * @param pluginClass the legacy {@code pluginClass}, null when not declared
 * @param pluginPath the module file
 * @param pluginRoot the directory with the plugin's {@code package.json}
 * @param contextOptions the context options the plugin needs ({@code js.commonjs-require} and its cwd)
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public record PluginDefinition(Kind kind,
                               boolean sdk,
                               String name,
                               String description,
                               String uri,
                               boolean secured,
                               MATCH_POLICY matchPolicy,
                               InterceptPoint interceptPoint,
                               ContentKind contentKind,
                               String pluginClass,
                               Path pluginPath,
                               Path pluginRoot,
                               Map<String, String> contextOptions) {

    /**
     * The two kinds of polyglot plugins.
     */
    public enum Kind {
        SERVICE, INTERCEPTOR
    }

    /**
     * @return whether this is a service
     */
    public boolean isService() {
        return kind == Kind.SERVICE;
    }

    /**
     * @return whether this is an interceptor
     */
    public boolean isInterceptor() {
        return kind == Kind.INTERCEPTOR;
    }
}
