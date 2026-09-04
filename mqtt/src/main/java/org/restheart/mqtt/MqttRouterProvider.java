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

import java.util.List;
import java.util.Map;

import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.Provider;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttRouterProvider.class);

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
     *   <li>{@code subscriptions} - a list of {@code {topic, qos}} entries subscribed at startup
     *       with no associated listener, purely to populate the last-message cache so that
     *       {@code mqtt-rest} polling works even with no SSE client connected; unlike
     *       listener-driven subscriptions, these survive listener churn (default: none)</li>
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

        subscribeConfiguredTopics();
    }

    /**
     * Establishes, with no associated listener, the broker subscriptions listed under the
     * {@code subscriptions} configuration key, each shaped as {@code {topic: "...", qos: N}}.
     * Entries missing a topic, or whose topic is not a (non-blank) string, are skipped with a
     * warning; the remaining entries are still subscribed. A missing {@code qos} defaults to
     * {@code 0}, but a {@code qos} that is present and does not resolve to 0, 1 or 2 - whether
     * an out-of-range integer (e.g. {@code 5}) or a value of the wrong type (e.g. a non-numeric
     * string) - fails fast here, at {@link #init()}, naming the offending key and entry, rather
     * than silently defaulting to 0 for a value the user actually wrote.
     *
     * @throws IllegalArgumentException if an entry's {@code qos} is present but does not resolve
     *                                   to 0, 1 or 2
     */
    @SuppressWarnings("unchecked")
    private void subscribeConfiguredTopics() {
        final List<Map<String, Object>> subscriptions = argOrDefault(config, "subscriptions", List.of());

        for (Map<String, Object> subscription : subscriptions) {
            final Object topic = subscription.get("topic");
            if (!(topic instanceof String topicFilter) || topicFilter.isBlank()) {
                LOGGER.warn("Skipping invalid startup subscription entry, missing topic: {}", subscription);
                continue;
            }

            final int qosCode = resolveConfiguredQos(subscription);

            router.subscribeFromConfig(topicFilter, MqttQos.fromCode(qosCode));
        }
    }

    /**
     * Resolves the {@code qos} of a single {@code subscriptions} entry, accepting either a
     * {@link Number} or a numeric {@link String} (e.g. a value quoted in YAML, a common slip).
     * A missing {@code qos} defaults to {@code 0}; a {@code qos} that is present but does not
     * resolve to 0, 1 or 2 fails fast, naming the offending entry, instead of silently yielding
     * 0 for a value the user actually wrote.
     *
     * @param subscription a single {@code subscriptions} entry
     * @return the resolved QoS code (0, 1 or 2)
     * @throws IllegalArgumentException if {@code qos} is present but does not resolve to 0, 1 or 2
     */
    private static int resolveConfiguredQos(Map<String, Object> subscription) {
        final Object qosValue = subscription.get("qos");
        if (qosValue == null) {
            return 0;
        }

        Integer qosCode = null;
        if (qosValue instanceof Number number) {
            qosCode = number.intValue();
        } else if (qosValue instanceof String s) {
            try {
                qosCode = Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                qosCode = null;
            }
        }

        if (qosCode == null || MqttQos.fromCode(qosCode) == null) {
            throw new IllegalArgumentException(
                "Invalid value for subscriptions[].qos: " + qosValue + " in entry " + subscription
                    + ". Accepted values are: 0, 1, 2");
        }

        return qosCode;
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
