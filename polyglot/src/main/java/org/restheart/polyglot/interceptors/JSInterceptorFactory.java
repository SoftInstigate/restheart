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
package org.restheart.polyglot.interceptors;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Optional;

import org.restheart.configuration.Configuration;
import org.restheart.exchange.Request;
import org.restheart.exchange.Response;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.PluginRecord;
import org.restheart.polyglot.PluginLoader;
import org.restheart.polyglot.spi.Capabilities;
import org.restheart.polyglot.spi.PluginDefinition;
import org.restheart.polyglot.spi.PluginDefinition.Kind;
import org.restheart.polyglot.spi.RhRuntime;

import com.mongodb.client.MongoClient;

/**
 * Builds the {@link JSInterceptor} of a module: the variant is picked from the legacy
 * {@code pluginClass} when the module declares one, from {@code contentKind} otherwise.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JSInterceptorFactory {
    private final Optional<MongoClient> mclient;
    private final Configuration config;
    private final Capabilities capabilities;

    public JSInterceptorFactory(Optional<MongoClient> mclient, Configuration config, Capabilities capabilities) {
        this.mclient = mclient;
        this.config = config;
        this.capabilities = capabilities;
    }

    /**
     * @param pluginPath the module file
     * @return the record to register
     * @throws IllegalArgumentException when the module is not a valid interceptor
     * @throws PluginLoader.MissingCapabilityException when it imports a capability the deployment does not have
     */
    public PluginRecord<Interceptor<?, ?>> create(Path pluginPath) throws IOException, InterruptedException {
        var loaded = PluginLoader.load(pluginPath, Kind.INTERCEPTOR, capabilities, config, mclient);
        var interceptor = create(loaded.definition(), loaded.runtime());

        return new PluginRecord<>(interceptor.name(),
                interceptor.getDescription(),
                false,
                true,
                interceptor.getClass().getName(),
                interceptor,
                new HashMap<>());
    }

    JSInterceptor<? extends Request<?>, ? extends Response<?>> create(PluginDefinition def, RhRuntime runtime) {
        if (def.pluginClass() != null) {
            var simple = def.pluginClass().contains(".") ? def.pluginClass().substring(def.pluginClass().lastIndexOf('.') + 1) : def.pluginClass();

            return switch (simple) {
                case "StringInterceptor" -> new StringJSInterceptor(def, runtime, config, mclient);
                case "BsonInterceptor" -> new BsonJSInterceptor(def, runtime, config, mclient);
                case "ByteArrayInterceptor" -> new ByteArrayJSInterceptor(def, runtime, config, mclient);
                case "ByteArrayProxyInterceptor" -> new ByteArrayProxyJSInterceptor(def, runtime, config, mclient);
                case "JsonProxyInterceptor" -> new JsonProxyJSInterceptor(def, runtime, config, mclient);
                case "CsvInterceptor" -> new CsvJSInterceptor(def, runtime, config, mclient);
                case "JsonInterceptor" -> new JsonJSInterceptor(def, runtime, config, mclient);
                case "MongoInterceptor" -> new MongoJSInterceptor(def, runtime, config, mclient);
                case "WildCardJSInterceptor", "WildcardInterceptor" -> new WildCardJSInterceptor(def, runtime, config, mclient);
                default -> throw new IllegalArgumentException("wrong js interceptor " + def.pluginPath().toAbsolutePath()
                        + ": unknown pluginClass '" + def.pluginClass() + "'; use contentKind: string, json, bson, bytes, mongo or csv");
            };
        }

        return switch (def.contentKind()) {
            case STRING -> new StringJSInterceptor(def, runtime, config, mclient);
            case JSON -> new JsonJSInterceptor(def, runtime, config, mclient);
            case BSON -> new BsonJSInterceptor(def, runtime, config, mclient);
            case BYTES -> new ByteArrayJSInterceptor(def, runtime, config, mclient);
            case MONGO -> new MongoJSInterceptor(def, runtime, config, mclient);
            case CSV -> new CsvJSInterceptor(def, runtime, config, mclient);
        };
    }
}
