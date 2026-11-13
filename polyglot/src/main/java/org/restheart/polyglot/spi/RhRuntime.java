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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.proxy.ProxyArray;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.polyglot.spi.proxy.MapProxy;
import org.restheart.polyglot.spi.proxy.ReadOnlyObject;

/**
 * What the {@code restheart:sdk} module of one plugin is built from: the utilities, always, and
 * the capabilities the deployment has. Bound in the guest context as the global
 * {@code __restheart}, which the SDK module reads and re-exports.
 *
 * <p>The module source is generated per plugin by {@link #sdkModuleSource()}: a capability that
 * is not available is simply not exported, so a plugin importing it fails to link, at load
 * time, before it is deployed. No separate declaration to keep in sync: the import is the
 * declaration.</p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RhRuntime {
    /** The virtual path the SDK module is served from, see {@link SdkFileSystem}. */
    public static final String SDK_SPECIFIER = "restheart:sdk";

    /** The global the SDK module reads the runtime from. */
    public static final String GLOBAL = "__restheart";

    /** The version of the SDK, what {@code rh:spi} in package.json is checked against. */
    public static final int SPI_VERSION = 1;

    private static final String SDK_TEMPLATE = readTemplate();

    private final String name;
    private final Path pluginRoot;
    private final Capabilities capabilities;
    private final Map<String, Object> config;
    private final RhLogger logger;
    private final RhCrypto crypto = new RhCrypto();
    private final RhEncode encode = new RhEncode();
    private final RhBson bson = new RhBson();
    private final RhResource resource;
    private final RhHttp http;
    private final RhMongo mongo;
    private final RhMail mail;
    private final Providers providers;

    /**
     * @param name the plugin name
     * @param pluginRoot the plugin directory, for {@code resource}
     * @param config the plugin's configuration, from {@code restheart.yml}
     * @param capabilities what the deployment has
     */
    public RhRuntime(String name, Path pluginRoot, Map<String, Object> config, Capabilities capabilities) {
        this.name = name;
        this.pluginRoot = pluginRoot;
        this.capabilities = capabilities;
        this.config = config == null ? new HashMap<>() : config;
        this.logger = new RhLogger(name);
        this.resource = new RhResource(pluginRoot);
        this.http = capabilities.hasHttp() ? new RhHttp(capabilities.httpClient(), capabilities.httpTimeout()) : null;
        this.mongo = capabilities.hasMongo() ? new RhMongo(capabilities.mclient().get()) : null;
        this.mail = capabilities.hasMail() ? new RhMail(capabilities.emails()) : null;
        this.providers = capabilities.hasProviders() ? new Providers(capabilities, name) : null;
    }

    public String name() {
        return name;
    }

    public int spiVersion() {
        return SPI_VERSION;
    }

    /**
     * @return the plugin directory
     */
    public Path pluginRoot() {
        return pluginRoot;
    }

    public RhLogger logger() {
        return logger;
    }

    public RhCrypto crypto() {
        return crypto;
    }

    public RhEncode encode() {
        return encode;
    }

    public RhBson bson() {
        return bson;
    }

    public RhResource resource() {
        return resource;
    }

    /**
     * @return the plugin configuration as native data
     */
    public Object config() {
        return new MapProxy(config);
    }

    public RhHttp http() {
        return http;
    }

    public RhMongo mongo() {
        return mongo;
    }

    public RhMail mail() {
        return mail;
    }

    public Providers providers() {
        return providers;
    }

    public Capabilities capabilities() {
        return capabilities;
    }

    /**
     * The source of the {@code restheart:sdk} module for this plugin: the template, always, and
     * one {@code export} per available capability.
     *
     * @return the module source
     */
    public String sdkModuleSource() {
        var sb = new StringBuilder(SDK_TEMPLATE);
        sb.append("\n// capabilities of this deployment\n");
        capabilities.available().forEach((cap, available) -> {
            if (available) {
                sb.append("export const ").append(cap).append(" = __rt.").append(cap).append("();\n");
            }
        });
        return sb.toString();
    }

    private static String readTemplate() {
        try (InputStream in = RhRuntime.class.getResourceAsStream("/org/restheart/polyglot/sdk.mjs")) {
            if (in == null) {
                throw new IllegalStateException("sdk.mjs not found in restheart-polyglot.jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@code providers["name"]}: any registered provider, by name, untyped: the configured,
     * initialised instance, not a class to wire up.
     */
    public static class Providers extends ReadOnlyObject {
        private final Capabilities capabilities;
        private final PluginRecord<?> caller;

        Providers(Capabilities capabilities, String pluginName) {
            this.capabilities = capabilities;
            this.caller = new PluginRecord<>(pluginName, "polyglot plugin " + pluginName, false, true, "org.restheart.polyglot", null, new HashMap<>());
        }

        @Override
        public Object getMember(String key) {
            for (var record : capabilities.registry().getProviders()) {
                if (key.equals(record.getName())) {
                    Provider<?> provider = record.getInstance();
                    return provider.get(caller);
                }
            }
            return null;
        }

        @Override
        public Object getMemberKeys() {
            var names = new ArrayList<Object>();
            capabilities.registry().getProviders().forEach(r -> names.add(r.getName()));
            return ProxyArray.fromList(names);
        }

        @Override
        public boolean hasMember(String key) {
            return capabilities.registry().getProviders().stream().anyMatch(r -> key.equals(r.getName()));
        }
    }
}
