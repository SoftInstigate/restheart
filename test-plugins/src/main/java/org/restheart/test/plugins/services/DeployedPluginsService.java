/*-
 * ========================LICENSE_START=================================
 * restheart-test-plugins
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
package org.restheart.test.plugins.services;

import java.util.Set;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Lists the names of the registered services and interceptors, including the ones the
 * polyglot deployer adds at runtime.
 *
 * <p>The integration tests use it to wait until the JavaScript reference plugins are
 * deployed, instead of sleeping a fixed time (#802).</p>
 */
@RegisterPlugin(
        name = "deployedPlugins",
        description = "lists the names of the registered services and interceptors",
        enabledByDefault = true,
        defaultURI = "/test-deployed-plugins")
public class DeployedPluginsService implements JsonService {
    @Inject("registry")
    private PluginsRegistry registry;

    @Override
    public void handle(JsonRequest request, JsonResponse response) throws Exception {
        if (request.isOptions()) {
            handleOptions(request);
            return;
        }

        if (!request.isGet()) {
            response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        var content = new JsonObject();
        content.add("services", names(registry.getServices()));
        content.add("interceptors", names(registry.getInterceptors()));

        response.setContent(content);
    }

    private static JsonArray names(Set<? extends PluginRecord<?>> plugins) {
        var ret = new JsonArray();
        plugins.forEach(p -> ret.add(p.getName()));
        return ret;
    }
}
