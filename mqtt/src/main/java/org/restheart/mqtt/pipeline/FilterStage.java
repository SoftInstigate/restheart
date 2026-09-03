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
import java.util.regex.Pattern;

import org.restheart.mqtt.model.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.JsonPath;

/**
 * Pipeline stage that filters messages based on various criteria.
 *
 * Supports filtering by:
 * - JSONPath expression on payload
 * - Topic regex pattern
 * - QoS level
 *
 * Messages that don't match the filter are dropped (returns empty).
 *
 * Examples:
 * <pre>
 * // Filter by JSONPath: keep only messages where temperature > 25
 * new FilterStage("$.temperature", "> 25")
 *
 * // Filter by topic regex: keep only sensor topics
 * new FilterStage("sensors/.*", null)
 *
 * // Filter by QoS: keep only QoS 1 or 2
 * new FilterStage(null, null, 1)
 * </pre>
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class FilterStage implements MqttEventStage {

    private static final Logger LOGGER = LoggerFactory.getLogger(FilterStage.class);

    private final String jsonPath;
    private final String jsonPathCondition;
    private final Pattern topicPattern;
    private final Integer minQos;

    /**
     * Create a filter stage with JSONPath expression
     *
     * @param jsonPath JSONPath expression to evaluate (e.g., "$.temperature")
     * @param condition Condition to check (e.g., "> 25", "== 'active'")
     */
    public FilterStage(String jsonPath, String condition) {
        this(jsonPath, condition, null, null);
    }

    /**
     * Create a filter stage with topic regex
     *
     * @param topicRegex Regex pattern for topic matching
     */
    public FilterStage(String topicRegex) {
        this(null, null, topicRegex, null);
    }

    /**
     * Create a filter stage with minimum QoS
     *
     * @param minQos Minimum QoS level (0, 1, or 2)
     */
    public FilterStage(int minQos) {
        this(null, null, null, minQos);
    }

    /**
     * Create a filter stage with all criteria
     *
     * @param jsonPath JSONPath expression (optional)
     * @param condition Condition for JSONPath result (optional)
     * @param topicRegex Topic regex pattern (optional)
     * @param minQos Minimum QoS level (optional)
     */
    public FilterStage(String jsonPath, String condition, String topicRegex, Integer minQos) {
        this.jsonPath = jsonPath;
        this.jsonPathCondition = condition;
        this.topicPattern = topicRegex != null ? Pattern.compile(topicRegex) : null;
        this.minQos = minQos;
    }

    @Override
    public Optional<MqttMessage> process(MqttMessage message) {
        // Check QoS filter
        if (minQos != null && message.getQos() < minQos) {
            LOGGER.debug("Message dropped: QoS {} < minimum {}", message.getQos(), minQos);
            return Optional.empty();
        }

        // Check topic filter
        if (topicPattern != null && !topicPattern.matcher(message.getTopic()).matches()) {
            LOGGER.debug("Message dropped: topic '{}' doesn't match pattern '{}'",
                message.getTopic(), topicPattern.pattern());
            return Optional.empty();
        }

        // Check JSONPath filter
        if (jsonPath != null && !evaluateJsonPath(message)) {
            LOGGER.debug("Message dropped: JSONPath condition not met");
            return Optional.empty();
        }

        return Optional.of(message);
    }

    /**
     * Evaluate JSONPath expression on message payload
     */
    private boolean evaluateJsonPath(MqttMessage message) {
        try {
            String payload = message.getPayload();

            // Extract value using JSONPath
            Object value = JsonPath.read(payload, jsonPath);

            // If no condition specified, just check if path exists
            if (jsonPathCondition == null) {
                return value != null;
            }

            // Evaluate condition
            return evaluateCondition(value, jsonPathCondition);

        } catch (Exception e) {
            LOGGER.warn("Failed to evaluate JSONPath '{}' on message: {}", jsonPath, e.getMessage());
            return false;
        }
    }

    /**
     * Evaluate a condition on a value
     *
     * Supports: >, <, >=, <=, ==, !=
     */
    private boolean evaluateCondition(Object value, String condition) {
        if (value == null) {
            return false;
        }

        condition = condition.trim();

        // Numeric comparisons
        if (value instanceof Number) {
            double numValue = ((Number) value).doubleValue();

            if (condition.startsWith(">=")) {
                return numValue >= Double.parseDouble(condition.substring(2).trim());
            } else if (condition.startsWith("<=")) {
                return numValue <= Double.parseDouble(condition.substring(2).trim());
            } else if (condition.startsWith(">")) {
                return numValue > Double.parseDouble(condition.substring(1).trim());
            } else if (condition.startsWith("<")) {
                return numValue < Double.parseDouble(condition.substring(1).trim());
            } else if (condition.startsWith("==")) {
                return numValue == Double.parseDouble(condition.substring(2).trim());
            } else if (condition.startsWith("!=")) {
                return numValue != Double.parseDouble(condition.substring(2).trim());
            }
        }

        // String comparisons
        String strValue = value.toString();
        if (condition.startsWith("==")) {
            String expected = condition.substring(2).trim().replaceAll("(^['\"])|(['\"]$)", "");
            return strValue.equals(expected);
        } else if (condition.startsWith("!=")) {
            String expected = condition.substring(2).trim().replaceAll("(^['\"])|(['\"]$)", "");
            return !strValue.equals(expected);
        }

        return false;
    }
}