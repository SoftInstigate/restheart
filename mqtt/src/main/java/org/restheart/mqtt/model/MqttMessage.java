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
package org.restheart.mqtt.model;

import java.time.Instant;
import java.util.Objects;

/**
 * POJO representing an MQTT message received from the broker.
 *
 * This immutable class encapsulates the essential properties of an MQTT message:
 * - Topic: The MQTT topic the message was published to
 * - Payload: The message content as a UTF-8 string
 * - QoS: Quality of Service level (0, 1, or 2)
 * - ReceivedAt: Timestamp when the message was received by RESTHeart
 * - Retain: whether the broker delivered this as a retained message
 *
 * Instances are created by the MqttMessageRouter when messages arrive from
 * the broker and are distributed to registered listeners.
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttMessage {
    private final String topic;
    private final String payload;
    private final int qos;
    private final Instant receivedAt;
    private final boolean retain;

    /**
     * Create a new MQTT message.
     *
     * @param topic The MQTT topic
     * @param payload The message payload as UTF-8 string
     * @param qos Quality of Service level (0, 1, or 2)
     * @param receivedAt Timestamp when message was received
     * @param retain whether the broker flagged this delivery as retained; see {@link #isRetain()}
     */
    public MqttMessage(String topic, String payload, int qos, Instant receivedAt, boolean retain) {
        this.topic = topic;
        this.payload = payload;
        this.qos = qos;
        this.receivedAt = receivedAt;
        this.retain = retain;
    }

    /**
     * Creates a message that is <strong>not</strong> a retained broker delivery, for derived and
     * synthetic messages: a window aggregate computed from several messages, or a message built by
     * a test. A broker delivery must use
     * {@link #MqttMessage(String, String, int, Instant, boolean)} and pass the publish's own retain
     * flag, or a retained value would be indistinguishable from a live reading.
     *
     * @param topic The MQTT topic
     * @param payload The message payload as UTF-8 string
     * @param qos Quality of Service level (0, 1, or 2)
     * @param receivedAt Timestamp when message was received
     */
    public MqttMessage(String topic, String payload, int qos, Instant receivedAt) {
        this(topic, payload, qos, receivedAt, false);
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

    /**
     * Whether the broker delivered this message with the MQTT retain flag set, meaning it is the
     * topic's last known state replayed to a new subscription rather than an event that has just
     * happened.
     * <p>
     * <strong>This is not the publisher's retain flag.</strong> MQTT 3.1.1 §3.3.1.3 requires a
     * server to set RETAIN on delivery only when it sends a message as the result of a
     * <em>new subscription</em>, and to clear it for an established subscription however the
     * publisher set it. So this answers "was I given this because I had just subscribed?", not
     * "did the publisher ask for this to be retained?" - the latter is not knowable by a
     * subscriber. Measured: the same retained publish delivers with the flag set to a fresh
     * subscription and clear to an established one.
     * </p>
     * <p>
     * <strong>It is the only warning a consumer gets that {@link #getReceivedAt()} is not the
     * moment the measurement was taken.</strong> {@code receivedAt} is always assigned locally on
     * reception, so a retained value published days ago carries today's timestamp. MQTT 3.1.1
     * transports no publisher timestamp, so the message's true age cannot be recovered; knowing it
     * is retained is what tells a consumer not to trust the one it has.
     * </p>
     *
     * @return {@code true} if this was a retained delivery
     */
    public boolean isRetain() {
        return retain;
    }

    @Override
    public String toString() {
        return String.format("MqttMessage{topic='%s', qos=%d, receivedAt=%s, retain=%s, payload='%s'}",
            topic, qos, receivedAt, retain, payload);
    }

    /**
     * Two {@code MqttMessage} instances are equal if and only if their topic, payload, QoS,
     * receivedAt timestamp and retain flag are all equal.
     *
     * @param o the object to compare against
     * @return {@code true} if {@code o} is an {@code MqttMessage} with identical field values
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MqttMessage other)) {
            return false;
        }
        return qos == other.qos
            && retain == other.retain
            && Objects.equals(topic, other.topic)
            && Objects.equals(payload, other.payload)
            && Objects.equals(receivedAt, other.receivedAt);
    }

    /**
     * Consistent with {@link #equals(Object)}: computed over topic, payload, QoS, receivedAt and
     * the retain flag.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(topic, payload, qos, receivedAt, retain);
    }
}
