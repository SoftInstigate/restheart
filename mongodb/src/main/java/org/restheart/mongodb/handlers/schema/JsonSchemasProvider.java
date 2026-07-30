/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.mongodb.handlers.schema;

import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.schema.JsonSchemas;

/**
 * Provider that exposes the JSON Schema store to any plugin via
 * {@code @Inject("json-schemas")}.
 * <p>
 * The implementation lives in {@code restheart-mongodb} but the interface
 * ({@link JsonSchemas}) lives in {@code restheart-commons}, so consumers
 * depend only on commons.
 */
@RegisterPlugin(
        name = "json-schemas",
        description = "provides access to the JSON Schema store")
public class JsonSchemasProvider implements Provider<JsonSchemas> {

    // the plugin registry instantiates this provider once and owns its lifecycle
    private final JsonSchemasImpl jsonSchemas = new JsonSchemasImpl();

    @Override
    public JsonSchemas get(PluginRecord<?> caller) {
        return jsonSchemas;
    }
}
