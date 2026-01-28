/*-
 * ========================LICENSE_START=================================
 * restheart-mongoclient-provider
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
package org.restheart.mongodb;

import java.util.Map;

import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;

@RegisterPlugin(
    name = "mclient",
    description = "provides the MongoClient",
    priority = 11)
public class MongoClientProvider implements Provider<MongoClient> {
    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() {
        final String mongoUri = argOrDefault(config, "connection-string", "mongodb://127.0.0.1");

        MongoClientSingleton.init(new ConnectionString(mongoUri));

        // force first connection to MongoDB
        MongoClientSingleton.getInstance().client();
    }

    @Override
    public MongoClient get(final PluginRecord<?> caller) {
        return MongoClientSingleton.get().client();
    }
}
