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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The MQTT 5.0 properties carried by a received PUBLISH, in this module's own types.
 * <p>
 * Expressed without any HiveMQ type, for the same reason {@link MqttMessage} is: a plugin that
 * consumes messages through {@code mqtt-router} does not compile against an MQTT library.
 * </p>
 * <p>
 * <strong>Why these are captured at all.</strong> Topic, payload, QoS and the retain flag are not
 * the whole of an MQTT 5 message. A correlation id, a content type, a response topic and a list of
 * user properties are part of what the publisher sent, and a collection of messages that drops them
 * is a record of the readings but not of the events — it cannot be used to replay the stream it came
 * from. They are absent, not empty, for MQTT 3.1.1, which has no such properties.
 * </p>
 * <p>
 * <strong>One of them is not what it appears to be.</strong>
 * {@link #messageExpiryInterval()} is the <em>remaining</em> interval, not the one the publisher
 * set: a server decrements it by the time the message waited before delivery. So it describes this
 * delivery, not the original publish, and is the second value in this module with that property —
 * see {@link MqttMessage#isRetain()} for the first. A stored message therefore cannot reproduce the
 * publisher's chosen expiry, and a replay built from one must decide what to set instead.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public final class Mqtt5Properties {

    /**
     * One MQTT 5 user property.
     * <p>
     * A list rather than a map, because the protocol allows the same name more than once and
     * requires the order to be preserved — so a {@code Map<String, String>} would quietly discard
     * part of what the publisher sent.
     * </p>
     *
     * @param name  the property name
     * @param value the property value
     */
    public record UserProperty(String name, String value) {
    }

    private final List<UserProperty> userProperties;
    private final String contentType;
    private final byte[] correlationData;
    private final String responseTopic;
    private final Integer payloadFormatIndicator;
    private final Long messageExpiryInterval;

    /**
     * @param userProperties         the user properties in the order received; never {@code null},
     *                               possibly empty
     * @param contentType            the content type, or {@code null} if the publisher set none
     * @param correlationData        the correlation data, or {@code null} if none; copied
     * @param responseTopic          the response topic, or {@code null} if none
     * @param payloadFormatIndicator {@code 0} (unspecified bytes) or {@code 1} (UTF-8), or
     *                               {@code null} if the publisher set none
     * @param messageExpiryInterval  the <strong>remaining</strong> expiry interval in seconds, or
     *                               {@code null} if none; see the class javadoc
     */
    public Mqtt5Properties(List<UserProperty> userProperties, String contentType, byte[] correlationData,
            String responseTopic, Integer payloadFormatIndicator, Long messageExpiryInterval) {
        this.userProperties = userProperties == null ? List.of() : List.copyOf(userProperties);
        this.contentType = contentType;
        // Copied, like MqttMessage's payload: correlation data is bytes, and the caller still holds
        // the array.
        this.correlationData = correlationData == null ? null : correlationData.clone();
        this.responseTopic = responseTopic;
        this.payloadFormatIndicator = payloadFormatIndicator;
        this.messageExpiryInterval = messageExpiryInterval;
    }

    /**
     * @return {@code true} if the publisher set none of these properties, so that a consumer can
     *         skip reporting an object whose every field is absent
     */
    public boolean isEmpty() {
        return userProperties.isEmpty()
            && contentType == null
            && correlationData == null
            && responseTopic == null
            && payloadFormatIndicator == null
            && messageExpiryInterval == null;
    }

    /**
     * @return the user properties in the order received, possibly empty, never {@code null}
     */
    public List<UserProperty> userProperties() {
        return userProperties;
    }

    /**
     * @return the content type, or {@code null}
     */
    public String contentType() {
        return contentType;
    }

    /**
     * @return a copy of the correlation data, or {@code null}
     */
    public byte[] correlationData() {
        return correlationData == null ? null : correlationData.clone();
    }

    /**
     * @return the response topic, or {@code null}
     */
    public String responseTopic() {
        return responseTopic;
    }

    /**
     * @return {@code 0} for unspecified bytes, {@code 1} for UTF-8, or {@code null}
     */
    public Integer payloadFormatIndicator() {
        return payloadFormatIndicator;
    }

    /**
     * The <strong>remaining</strong> message expiry interval in seconds, not the one the publisher
     * set: see the class javadoc.
     *
     * @return the remaining expiry interval, or {@code null}
     */
    public Long messageExpiryInterval() {
        return messageExpiryInterval;
    }

    @Override
    public String toString() {
        return "Mqtt5Properties{userProperties=" + userProperties.size()
            + ", contentType=" + contentType
            + ", correlationData=" + (correlationData == null ? "null" : correlationData.length + " bytes")
            + ", responseTopic=" + responseTopic
            + ", payloadFormatIndicator=" + payloadFormatIndicator
            + ", messageExpiryInterval=" + messageExpiryInterval + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Mqtt5Properties other)) {
            return false;
        }
        return Objects.equals(userProperties, other.userProperties)
            && Objects.equals(contentType, other.contentType)
            && Arrays.equals(correlationData, other.correlationData)
            && Objects.equals(responseTopic, other.responseTopic)
            && Objects.equals(payloadFormatIndicator, other.payloadFormatIndicator)
            && Objects.equals(messageExpiryInterval, other.messageExpiryInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userProperties, contentType, Arrays.hashCode(correlationData),
            responseTopic, payloadFormatIndicator, messageExpiryInterval);
    }
}
