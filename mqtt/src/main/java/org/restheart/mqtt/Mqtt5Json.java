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

import java.util.Base64;

import org.restheart.mqtt.model.Mqtt5Properties;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Renders {@link Mqtt5Properties} as JSON, shared by {@code mqtt-sse} and {@code mqtt-rest} so the
 * two surfaces cannot drift into reporting the same properties differently.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
final class Mqtt5Json {

    private Mqtt5Json() {
        // utility class
    }

    /**
     * Renders the MQTT 5 publish properties as a JSON object, or {@code null} when there are none -
     * on MQTT 3.1.1, or when the publisher set nothing. The caller omits the field in that case
     * rather than emitting it empty, so its presence means something.
     * <p>
     * {@code correlationData} is base64, because it is binary and JSON cannot carry bytes.
     * {@code userProperties} is an array of name/value pairs rather than an object, because MQTT 5
     * permits a repeated name and requires the order to be preserved - an object would keep
     * neither. {@code messageExpiryInterval} is the <strong>remaining</strong> interval, not the one
     * the publisher set; see {@link Mqtt5Properties}.
     * </p>
     *
     * @param properties the properties, possibly {@code null}
     * @return the JSON object, or {@code null} if there is nothing to report
     */
    static JsonObject mqtt5PropertiesAsJson(Mqtt5Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return null;
        }

        var json = new JsonObject();

        if (!properties.userProperties().isEmpty()) {
            var array = new JsonArray();
            for (var property : properties.userProperties()) {
                var entry = new JsonObject();
                entry.addProperty("name", property.name());
                entry.addProperty("value", property.value());
                array.add(entry);
            }
            json.add("userProperties", array);
        }
        if (properties.contentType() != null) {
            json.addProperty("contentType", properties.contentType());
        }
        if (properties.correlationData() != null) {
            json.addProperty("correlationData", Base64.getEncoder().encodeToString(properties.correlationData()));
        }
        if (properties.responseTopic() != null) {
            json.addProperty("responseTopic", properties.responseTopic());
        }
        if (properties.payloadFormatIndicator() != null) {
            json.addProperty("payloadFormatIndicator", properties.payloadFormatIndicator());
        }
        if (properties.messageExpiryInterval() != null) {
            json.addProperty("messageExpiryInterval", properties.messageExpiryInterval());
        }

        return json;
    }
}
