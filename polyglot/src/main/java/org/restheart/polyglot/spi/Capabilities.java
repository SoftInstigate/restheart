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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.restheart.configuration.Configuration;
import org.restheart.emails.EmailSender;
import org.restheart.plugins.PluginsRegistry;

import com.mongodb.client.MongoClient;

/**
 * What the deployment can give to a plugin: the providers behind the capabilities of the SDK,
 * each present or not depending on the configuration.
 *
 * <p>The capabilities and what they need:</p>
 * <table>
 *   <tr><th>name</th><th>provider</th><th>available when</th></tr>
 *   <tr><td>{@code http}</td><td>{@code http-client}</td><td>always</td></tr>
 *   <tr><td>{@code mongo}</td><td>{@code mclient}</td><td>MongoDB is enabled</td></tr>
 *   <tr><td>{@code config}</td><td>{@code config}</td><td>always</td></tr>
 *   <tr><td>{@code mail}</td><td>{@code emails}</td><td>email is configured</td></tr>
 *   <tr><td>{@code providers}</td><td>{@code registry}</td><td>always</td></tr>
 * </table>
 *
 * @param mclient the MongoDB client, when MongoDB is enabled
 * @param httpClient the shared HTTP client of the {@code http-client} provider
 * @param httpTimeout the request timeout of an HTTP call that sets none
 * @param emails the email sender, when the {@code emails} provider is enabled
 * @param registry the plugins registry, for {@code providers}
 * @param config the RESTHeart configuration
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public record Capabilities(Optional<MongoClient> mclient,
                           HttpClient httpClient,
                           Duration httpTimeout,
                           EmailSender emails,
                           PluginsRegistry registry,
                           Configuration config) {

    /** The names a plugin can import from {@code restheart:sdk} as capabilities. */
    public static final String HTTP = "http";
    public static final String MONGO = "mongo";
    public static final String CONFIG = "config";
    public static final String MAIL = "mail";
    public static final String PROVIDERS = "providers";

    /**
     * @return capabilities with nothing behind them, for tests and for contexts that run no plugin
     */
    public static Capabilities none() {
        return new Capabilities(Optional.empty(), null, Duration.ofSeconds(30), null, null, null);
    }

    /**
     * @return whether {@code http} can be imported
     */
    public boolean hasHttp() {
        return httpClient != null;
    }

    /**
     * @return whether {@code mongo} can be imported
     */
    public boolean hasMongo() {
        return mclient != null && mclient.isPresent();
    }

    /**
     * @return whether {@code mail} can be imported
     */
    public boolean hasMail() {
        return emails != null && emails.isEnabled();
    }

    /**
     * @return whether {@code providers} can be imported
     */
    public boolean hasProviders() {
        return registry != null;
    }

    /**
     * Why a capability cannot be imported, for the log line of a plugin that is not deployed.
     *
     * @param name the capability
     * @return the reason, empty when the capability is available or unknown
     */
    public Optional<String> unavailableReason(String name) {
        return switch (name) {
            case HTTP -> hasHttp() ? Optional.empty() : Optional.of("the http-client provider is not available");
            case MONGO -> hasMongo() ? Optional.empty() : Optional.of("MongoDB is not enabled (no mclient provider)");
            case MAIL -> hasMail() ? Optional.empty() : Optional.of("the emails provider is not enabled");
            case PROVIDERS -> hasProviders() ? Optional.empty() : Optional.of("the plugins registry is not available");
            default -> Optional.empty();
        };
    }

    /**
     * @return the capabilities a plugin can import, by name, in a stable order
     */
    public Map<String, Boolean> available() {
        var m = new LinkedHashMap<String, Boolean>();
        m.put(HTTP, hasHttp());
        m.put(MONGO, hasMongo());
        m.put(CONFIG, true);
        m.put(MAIL, hasMail());
        m.put(PROVIDERS, hasProviders());
        return m;
    }

    /**
     * @param name a name
     * @return whether it is the name of a capability, available or not
     */
    public static boolean isCapability(String name) {
        return switch (name) {
            case HTTP, MONGO, CONFIG, MAIL, PROVIDERS -> true;
            default -> false;
        };
    }
}
