/*-
 * ========================LICENSE_START=================================
 * restheart-mqtt
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
package org.restheart.mqtt;

import java.util.Map;

import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;

import com.hivemq.client.mqtt.MqttClient;

/**
 * RESTHeart provider plugin that supplies the single {@link MqttMessageRouter} instance shared
 * by the MQTT SSE, REST and MongoDB-writer plugins.
 * <p>
 * This provider depends on the {@code mqtt-client} provider ({@link MqttClientProvider}) for the
 * underlying {@link MqttClient}; RESTHeart's {@code ProvidersChecker} builds and validates the
 * provider dependency graph, so a provider depending on another provider is supported.
 * </p>
 *
 * @see Provider
 * @see MqttMessageRouter
 * @see MqttClientProvider
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-router",
    description = "Provides the MQTT message router",
    priority = 11
)
public class MqttRouterProvider implements Provider<MqttMessageRouter> {

    /**
     * The MQTT client injected from the {@code mqtt-client} provider, used to build the router.
     */
    @Inject("mqtt-client")
    private MqttClient mqttClient;

    /**
     * Configuration map containing properties for the MQTT router plugin,
     * injected automatically by RESTHeart.
     */
    @Inject("config")
    private Map<String, Object> config;

    private MqttMessageRouter router;

    /**
     * Builds the {@link MqttMessageRouter} from the {@code mqtt-router} configuration section.
     * <p>
     * Supported configuration keys in the {@code config} map:
     * <ul>
     *   <li>{@code max-inflight-messages-per-second} - the maximum number of messages accepted per
     *       second before further messages are dropped; {@code 0} disables the rate limit
     *       (default: 5000)</li>
     *   <li>{@code last-message-cache} - whether the last message received per topic should be
     *       cached for late-joining subscribers (default: true)</li>
     *   <li>{@code last-message-cache-size} - the maximum number of topics to retain in the
     *       last-message cache when caching is enabled (default: 1000)</li>
     * </ul>
     * </p>
     * <p>
     * As the composition root for the router, this provider also wires the router's
     * {@link MqttMessageRouter#resubscribeAll()} into
     * {@link MqttClientSingleton#addOnNewSessionListener(Runnable)}, so that broker subscriptions
     * are re-issued whenever the client reports a new (non-persisted) session after a (re)connect.
     * </p>
     */
    @OnInit
    public void init() {
        final int maxMessagesPerSecond = argOrDefault(config, "max-inflight-messages-per-second", 5000);
        final boolean cacheEnabled = argOrDefault(config, "last-message-cache", true);
        final int maxCacheSize = argOrDefault(config, "last-message-cache-size", 1000);

        router = new MqttMessageRouter(mqttClient, maxMessagesPerSecond, cacheEnabled, maxCacheSize);

        MqttClientSingleton.getInstance().addOnNewSessionListener(router::resubscribeAll);
    }

    /**
     * Returns the shared {@link MqttMessageRouter} instance built at {@link #init()}.
     *
     * @param caller the plugin record representing the caller requesting the router
     * @return the configured {@link MqttMessageRouter} instance
     */
    @Override
    public MqttMessageRouter get(final PluginRecord<?> caller) {
        return router;
    }
}
