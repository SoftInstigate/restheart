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
package org.restheart.polyglot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.graalvm.polyglot.PolyglotException;
import org.restheart.configuration.Configuration;
import org.restheart.polyglot.spi.Capabilities;
import org.restheart.polyglot.spi.PluginDefinition;
import org.restheart.polyglot.spi.PluginDefinition.Kind;
import org.restheart.polyglot.spi.PluginModule;
import org.restheart.polyglot.spi.RhRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;

/**
 * Reads a plugin module in a throwaway context and returns its definition and its runtime.
 *
 * <p>This is where a plugin that imports a capability the deployment does not have is
 * stopped: the SDK module of this deployment does not export it, the import fails to link,
 * and the plugin is reported with the capability's name and why it is missing, instead of
 * being deployed and throwing a {@code TypeError} inside {@code handle()} at request time.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public final class PluginLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(PluginLoader.class);

    private PluginLoader() {
    }

    /**
     * A loaded plugin: what it declares and the runtime built for it.
     *
     * @param definition the definition
     * @param runtime the runtime, with the plugin's configuration
     */
    public record Loaded(PluginDefinition definition, RhRuntime runtime) {
    }

    /**
     * A plugin imports a capability the deployment does not have.
     */
    public static class MissingCapabilityException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        private final String capability;

        MissingCapabilityException(Path pluginPath, String capability, String reason) {
            super("plugin " + pluginPath.toAbsolutePath() + " imports the capability '" + capability + "' from restheart:sdk, which is not available: " + reason);
            this.capability = capability;
        }

        public String capability() {
            return capability;
        }
    }

    /**
     * Loads a module.
     *
     * @param pluginPath the module file
     * @param kind what {@code package.json} lists it as
     * @param capabilities what the deployment has
     * @param config the configuration
     * @param mclient the MongoDB client, for the legacy {@code mclient} global
     * @return the definition and the runtime
     * @throws IllegalArgumentException when the module is not a valid plugin, with what is wrong
     * @throws MissingCapabilityException when it imports a capability the deployment does not have
     * @throws IOException when the module cannot be read
     */
    public static Loaded load(Path pluginPath, Kind kind, Capabilities capabilities, Configuration config, Optional<MongoClient> mclient) throws IOException, InterruptedException {
        var pluginRoot = PluginModule.pluginRoot(pluginPath);
        var contextOptions = PluginModule.contextOptions(pluginRoot);
        var probeName = pluginPath.getFileName().toString();
        var probeRuntime = new RhRuntime(probeName, pluginRoot, new HashMap<>(), capabilities);
        var source = PluginModule.source(pluginPath);

        LOGGER.debug("Loading {} plugin {} (root {}, options {})", kind, pluginPath, pluginRoot, contextOptions);

        return PolyglotThreadUtils.runIO(() -> {
            var ctx = ContextQueue.newContext(JSPlugin.engine(), probeRuntime, probeName, config, LOGGER, mclient, contextOptions);
            ctx.enter();
            try {
                var ns = ctx.eval(source);
                var definition = PluginModule.define(kind, ns, pluginPath, pluginRoot, contextOptions);
                var args = pluginArgs(config, definition.name());
                return new Loaded(definition, new RhRuntime(definition.name(), pluginRoot, args, capabilities));
            } catch (PolyglotException pe) {
                var missing = PluginModule.missingCapability(pe, capabilities);
                if (missing.isPresent()) {
                    throw new MissingCapabilityException(pluginPath, missing.get().name(), missing.get().reason());
                }
                throw new IllegalArgumentException("wrong js " + kind.name().toLowerCase() + " " + pluginPath.toAbsolutePath() + ": " + pe.getMessage(), pe);
            } finally {
                ctx.leave();
                ctx.close(true);
            }
        });
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> pluginArgs(Configuration config, String pluginName) {
        if (config == null) {
            return new HashMap<>();
        }

        var args = config.getOrDefault(pluginName, (Object) null);

        if (args instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }

        return new HashMap<>();
    }
}
