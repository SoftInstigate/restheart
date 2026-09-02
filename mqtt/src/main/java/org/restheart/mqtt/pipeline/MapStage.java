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

package org.restheart.mqtt.pipeline;

import java.util.Optional;

import org.restheart.mqtt.model.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.JsonPath;

/**
 * Pipeline stage that transforms message payloads.
 *
 * Supports:
 * - Extracting a specific JSON field
 * - Applying a payload template
 * - Renaming JSON keys
 *
 * Examples:
 * <pre>
 * // Extract a field: {"temp": 25, "humidity": 60} -> "25"
 * new MapStage("$.temp")
 *
 * // Apply template: wrap payload in envelope
 * new MapStage("{\"data\": ${payload}, \"timestamp\": \"${timestamp}\"}")
 *
 * // Rename keys: {"temp": 25} -> {"temperature": 25}
 * new MapStage("temp", "temperature")
 * </pre>
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 */
public class MapStage implements MqttEventStage {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapStage.class);
    private static final Gson GSON = new Gson();

    private final String extractField;
    private final String template;
    private final String renameFrom;
    private final String renameTo;

    /**
     * Create a map stage that extracts a JSON field
     *
     * @param extractField JSONPath expression to extract (e.g., "$.temperature")
     */
    public MapStage(String extractField) {
        this.extractField = extractField;
        this.template = null;
        this.renameFrom = null;
        this.renameTo = null;
    }

    /**
     * Create a map stage that renames a JSON key
     *
     * @param renameFrom Original key name
     * @param renameTo New key name
     */
    public MapStage(String renameFrom, String renameTo) {
        this.extractField = null;
        this.template = null;
        this.renameFrom = renameFrom;
        this.renameTo = renameTo;
    }

    /**
     * Create a map stage with a payload template
     *
     * @param template Template string with ${payload} and ${timestamp} placeholders
     * @param isTemplate Must be true to indicate this is a template
     */
    public MapStage(String template, boolean isTemplate) {
        this.extractField = null;
        this.template = isTemplate ? template : null;
        this.renameFrom = null;
        this.renameTo = null;
    }

    @Override
    public Optional<MqttMessage> process(MqttMessage message) {
        try {
            String transformedPayload;

            if (extractField != null) {
                transformedPayload = extractJsonField(message.getPayload());
            } else if (template != null) {
                transformedPayload = applyTemplate(message);
            } else if (renameFrom != null && renameTo != null) {
                transformedPayload = renameKey(message.getPayload());
            } else {
                // No transformation
                return Optional.of(message);
            }

            // Create new message with transformed payload
            MqttMessage transformed = new MqttMessage(
                message.getTopic(),
                transformedPayload,
                message.getQos(),
                message.getReceivedAt()
            );

            return Optional.of(transformed);

        } catch (Exception e) {
            LOGGER.error("Failed to transform message payload: {}", e.getMessage(), e);
            return Optional.empty(); // Drop message on transformation error
        }
    }

    /**
     * Extract a JSON field using JSONPath
     */
    private String extractJsonField(String payload) {
        Object value = JsonPath.read(payload, extractField);

        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else {
            // Complex object - serialize to JSON
            return GSON.toJson(value);
        }
    }

    /**
     * Apply a template to the message
     *
     * Supports placeholders:
     * - ${payload} - original payload
     * - ${timestamp} - message received timestamp
     * - ${topic} - message topic
     * - ${qos} - message QoS
     */
    private String applyTemplate(MqttMessage message) {
        String result = template;

        result = result.replace("${payload}", message.getPayload());
        result = result.replace("${timestamp}", message.getReceivedAt().toString());
        result = result.replace("${topic}", message.getTopic());
        result = result.replace("${qos}", String.valueOf(message.getQos()));

        return result;
    }

    /**
     * Rename a JSON key
     */
    private String renameKey(String payload) {
        JsonElement element = JsonParser.parseString(payload);

        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Payload must be a JSON object to rename keys");
        }

        JsonObject obj = element.getAsJsonObject();

        if (obj.has(renameFrom)) {
            JsonElement value = obj.remove(renameFrom);
            obj.add(renameTo, value);
        }

        return GSON.toJson(obj);
    }
}