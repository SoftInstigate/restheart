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

import java.time.Instant;

/**
 * POJO representing an MQTT message received from the broker.
 *
 * This immutable class encapsulates the essential properties of an MQTT message:
 * - Topic: The MQTT topic the message was published to
 * - Payload: The message content as a UTF-8 string
 * - QoS: Quality of Service level (0, 1, or 2)
 * - ReceivedAt: Timestamp when the message was received by RESTHeart
 *
 * Instances are created by the MqttMessageRouter when messages arrive from
 * the broker and are distributed to registered listeners.
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 */
public class MqttMessage {
    private final String topic;
    private final String payload;
    private final int qos;
    private final Instant receivedAt;

    /**
     * Create a new MQTT message
     *
     * @param topic The MQTT topic
     * @param payload The message payload as UTF-8 string
     * @param qos Quality of Service level (0, 1, or 2)
     * @param receivedAt Timestamp when message was received
     */
    public MqttMessage(String topic, String payload, int qos, Instant receivedAt) {
        this.topic = topic;
        this.payload = payload;
        this.qos = qos;
        this.receivedAt = receivedAt;
    }

    /**
     * @return The MQTT topic
     */
    public String getTopic() {
        return topic;
    }

    /**
     * @return The message payload as UTF-8 string
     */
    public String getPayload() {
        return payload;
    }

    /**
     * @return Quality of Service level (0, 1, or 2)
     */
    public int getQos() {
        return qos;
    }

    /**
     * @return Timestamp when message was received
     */
    public Instant getReceivedAt() {
        return receivedAt;
    }

    @Override
    public String toString() {
        return String.format("MqttMessage{topic='%s', qos=%d, receivedAt=%s, payload='%s'}",
            topic, qos, receivedAt, payload);
    }
}