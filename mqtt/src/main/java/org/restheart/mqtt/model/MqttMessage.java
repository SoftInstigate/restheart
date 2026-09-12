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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * POJO representing an MQTT message received from the broker.
 *
 * This immutable class encapsulates the essential properties of an MQTT message:
 * - Topic: The MQTT topic the message was published to
 * - Payload: The message content, as the bytes the broker delivered
 * - QoS: Quality of Service level (0, 1, or 2)
 * - ReceivedAt: Timestamp when the message was received by RESTHeart
 * - Retain: whether the broker delivered this as a retained message
 * - Mqtt5Properties: the MQTT 5.0 publish properties, absent on MQTT 3.1.1
 *
 * Instances are created by the MqttMessageRouter when messages arrive from
 * the broker and are distributed to registered listeners.
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttMessage {
    private final String topic;

    /**
     * The payload exactly as the broker delivered it. MQTT payloads are arbitrary bytes, so this -
     * not a decoded string - is the message's content.
     */
    private final byte[] payload;

    /**
     * The payload decoded as UTF-8, or {@code null} when the bytes are not valid UTF-8. Computed
     * once at construction, since the class is immutable, so no consumer pays to decode twice and
     * the validity question has exactly one answer.
     */
    private final String payloadText;

    private final int qos;
    private final Instant receivedAt;
    private final boolean retain;

    /**
     * The MQTT 5.0 publish properties, or {@code null} on MQTT 3.1.1, which has none - absent
     * rather than empty, so a consumer can tell "the protocol has no such thing" from "the
     * publisher set nothing".
     */
    private final Mqtt5Properties mqtt5Properties;

    /**
     * Create a new MQTT message from the bytes the broker delivered.
     *
     * @param topic The MQTT topic
     * @param payload The message payload as delivered; copied, and may be {@code null}
     * @param qos Quality of Service level (0, 1, or 2)
     * @param receivedAt Timestamp when message was received
     * @param retain whether the broker flagged this delivery as retained; see {@link #isRetain()}
     */
    public MqttMessage(String topic, byte[] payload, int qos, Instant receivedAt, boolean retain,
            Mqtt5Properties mqtt5Properties) {
        this.topic = topic;
        // Copied, or this would not be immutable: the caller still holds the array.
        this.payload = payload == null ? null : payload.clone();
        this.payloadText = decodeUtf8Strictly(this.payload);
        this.qos = qos;
        this.receivedAt = receivedAt;
        this.retain = retain;
        this.mqtt5Properties = mqtt5Properties;
    }

    /**
     * Create a new MQTT message with no MQTT 5 properties, as an MQTT 3.1.1 delivery has none.
     *
     * @param topic The MQTT topic
     * @param payload The message payload as delivered; copied, and may be {@code null}
     * @param qos Quality of Service level (0, 1, or 2)
     * @param receivedAt Timestamp when message was received
     * @param retain whether the broker flagged this delivery as retained; see {@link #isRetain()}
     */
    public MqttMessage(String topic, byte[] payload, int qos, Instant receivedAt, boolean retain) {
        this(topic, payload, qos, receivedAt, retain, null);
    }

    /**
     * Create a new MQTT message from text, which is encoded to UTF-8 bytes.
     *
     * @param topic The MQTT topic
     * @param payload The message payload as text; may be {@code null}
     * @param qos Quality of Service level (0, 1, or 2)
     * @param receivedAt Timestamp when message was received
     * @param retain whether the broker flagged this delivery as retained; see {@link #isRetain()}
     */
    public MqttMessage(String topic, String payload, int qos, Instant receivedAt, boolean retain) {
        this(topic, payload, qos, receivedAt, retain, null);
    }

    /**
     * Create a new MQTT message from text, which is encoded to UTF-8 bytes.
     *
     * @param topic The MQTT topic
     * @param payload The message payload as text; may be {@code null}
     * @param qos Quality of Service level (0, 1, or 2)
     * @param receivedAt Timestamp when message was received
     * @param retain whether the broker flagged this delivery as retained; see {@link #isRetain()}
     * @param mqtt5Properties the MQTT 5 publish properties, or {@code null}
     */
    public MqttMessage(String topic, String payload, int qos, Instant receivedAt, boolean retain,
            Mqtt5Properties mqtt5Properties) {
        this(topic, payload == null ? null : payload.getBytes(StandardCharsets.UTF_8), qos, receivedAt,
            retain, mqtt5Properties);
    }

    /**
     * Decodes {@code bytes} as UTF-8, rejecting rather than replacing malformed input.
     * <p>
     * {@code new String(bytes, UTF_8)} substitutes U+FFFD for every byte sequence it cannot decode,
     * silently and irreversibly. That is why this module used to destroy any payload that was not
     * text - protobuf, CBOR, an image, anything compressed - before it reached a consumer or the
     * database. Reporting the error instead is what lets a non-text payload be recognised as such
     * and passed on intact.
     * </p>
     *
     * @param bytes the payload bytes, possibly {@code null}
     * @return the decoded text, or {@code null} if {@code bytes} is {@code null} or not valid UTF-8
     */
    private static String decodeUtf8Strictly(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        var decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
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
     * The payload decoded as UTF-8 text.
     * <p>
     * <strong>Returns {@code null} when the payload is not valid UTF-8</strong>, which an MQTT
     * payload is under no obligation to be. Callers that must have text - a JSONPath filter, a
     * window aggregation, an SSE {@code data:} line - have to handle that, and
     * {@link #isTextPayload()} is the question to ask first. Use {@link #getPayloadBytes()} to get
     * the content whatever it is, and {@link #getPayloadAsBase64()} to put it somewhere that only
     * accepts text.
     * </p>
     *
     * @return the payload as UTF-8 text, or {@code null} if it is not valid UTF-8
     */
    public String getPayload() {
        return payloadText;
    }

    /**
     * @return the payload exactly as the broker delivered it (a copy), or {@code null} if there
     *         was none
     */
    public byte[] getPayloadBytes() {
        return payload == null ? null : payload.clone();
    }

    /**
     * @return {@code true} if the payload is valid UTF-8 and {@link #getPayload()} will return it
     */
    public boolean isTextPayload() {
        return payloadText != null;
    }

    /**
     * The payload as standard base64, for the surfaces that can only carry text - JSON and the SSE
     * wire format, neither of which can represent arbitrary bytes.
     *
     * @return the base64 of the payload, or {@code null} if there was none
     */
    public String getPayloadAsBase64() {
        return payload == null ? null : Base64.getEncoder().encodeToString(payload);
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

    /**
     * The MQTT 5.0 publish properties - user properties, content type, correlation data, response
     * topic, payload format indicator and the remaining message expiry interval.
     *
     * @return the properties, or {@code null} on MQTT 3.1.1, which has none
     */
    public Mqtt5Properties getMqtt5Properties() {
        return mqtt5Properties;
    }

    @Override
    public String toString() {
        return String.format("MqttMessage{topic='%s', qos=%d, receivedAt=%s, retain=%s, payload=%s}",
            topic, qos, receivedAt, retain,
            payloadText != null ? "'" + payloadText + "'" : (payload == null ? "null" : payload.length + " bytes"));
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
            && Arrays.equals(payload, other.payload)
            && Objects.equals(receivedAt, other.receivedAt)
            && Objects.equals(mqtt5Properties, other.mqtt5Properties);
    }

    /**
     * Consistent with {@link #equals(Object)}: computed over topic, payload, QoS, receivedAt and
     * the retain flag.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(topic, Arrays.hashCode(payload), qos, receivedAt, retain, mqtt5Properties);
    }
}
