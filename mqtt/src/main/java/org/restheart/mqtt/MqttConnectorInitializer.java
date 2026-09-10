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

import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects the MQTT client, once and last, after every consumer registered at startup exists.
 * <p>
 * <strong>Why this is a plugin of its own rather than a line somewhere else.</strong> A broker
 * redelivers everything a resumed session still owes the moment it sends CONNACK. Whatever is not
 * listening at that instant loses those messages: the router acknowledges a message no durable
 * listener has claimed, and hivemq-mqtt-client acknowledges a publish that no flow consumes at all
 * (its issue #455). So the connection cannot be made until the last consumer has registered, and
 * "the last consumer" is not something any single other component knows it is.
 * </p>
 * <p>
 * The module's registrations happen at three different moments: {@code mqtt-client} builds the
 * client during its own initialization, {@code mqtt-router} registers the global publish consumer
 * when it is first built, and {@code mqtt-mongo-writer} registers its durable listener later still,
 * at {@link InitPoint#AFTER_STARTUP}. Connecting from any of them connects too early for the ones
 * that come after - which is how a restarted instance used to acknowledge and discard everything
 * the broker handed back, on every reconnect with a persistent session rather than only on
 * restart.
 * </p>
 * <p>
 * Hence a step whose only job is to be last. Its priority is deliberately high so it runs after
 * every other {@code AFTER_STARTUP} initializer, {@code mqtt-mongo-writer} (priority 10) included.
 * </p>
 * <p>
 * It is enabled by default and harmless when the module is not in use: with no
 * {@code mqtt-client} configured the singleton is never initialized and this does nothing. That is
 * deliberate too - making it another switch to remember would mean an operator who armed
 * {@code mqtt-client} and {@code mqtt-router} correctly still got a module that never connected.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-connector",
    description = "Connects the MQTT client after every startup-registered consumer exists",
    initPoint = InitPoint.AFTER_STARTUP,
    priority = 1000,
    enabledByDefault = true
)
public class MqttConnectorInitializer implements Initializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttConnectorInitializer.class);

    @Override
    public void init() {
        if (!MqttClientSingleton.isInitialized()) {
            // mqtt-client is not configured or not enabled: nothing to connect, and nothing to say
            // about it either - mqtt-status is what reports on a module that is installed and idle.
            return;
        }

        try {
            MqttClientSingleton.getInstance().connect();
        } catch (Exception e) {
            // Never propagate: a broker that is unreachable at startup must not stop RESTHeart from
            // serving everything that has nothing to do with MQTT. The client's own automatic
            // reconnect keeps trying, and mqtt-client's init already logged the failure in detail.
            LOGGER.error("Could not connect to the MQTT broker at startup; the client will keep retrying", e);
        }
    }
}
