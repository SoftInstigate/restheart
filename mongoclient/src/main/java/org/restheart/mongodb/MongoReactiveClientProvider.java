/*-
 * ========================LICENSE_START=================================
 * restheart-mongoclient-provider
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClient;

@RegisterPlugin(name = "mclient-reactive", description = "provides the reactive MongoClient", priority = 12)
public class MongoReactiveClientProvider implements Provider<MongoClient> {
    @Inject("config")
    private Map<String, Object> config;

    static void init(ConnectionString mongoConnetion) {
        MongoReactiveClientSingleton.init(mongoConnetion);

        // force setup
        MongoReactiveClientSingleton.getInstance();
    }

    @Override
    public MongoClient get(PluginRecord<?> caller) {
        return MongoReactiveClientSingleton.get().client();
    }
}
