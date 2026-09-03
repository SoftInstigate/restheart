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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.restheart.exchange.ServiceRequest;
import org.restheart.exchange.ServiceResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.WildcardInterceptor;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interceptor that checks MQTT topic access against ACL entries.
 * <p>
 * Runs after authentication ({@link InterceptPoint#REQUEST_AFTER_AUTH}) and
 * verifies that the authenticated principal has permission to subscribe to
 * the requested MQTT topic filter.
 * </p>
 * <p>
 * Configuration in {@code restheart-config.yml}:
 * <pre>
 * plugins-args:
 *   mqtt-topic-authorizer:
 *     acl:
 *       iot-reader:
 *         - "sensors/#"
 *         - "devices/+/status"
 *       admin:
 *         - "#"
 * </pre>
 * </p>
 * <p>
 * ACL entries are matched against the topic filter using MQTT wildcard rules:
 * <ul>
 *   <li>{@code #} — matches all topics</li>
 *   <li>{@code sensors/#} — matches any topic starting with {@code sensors/}</li>
 *   <li>{@code sensors/+/temp} — matches {@code sensors/room1/temp} etc.</li>
 *   <li>{@code sensors/temp} — exact match</li>
 * </ul>
 * </p>
 * <p>
 * This interceptor fails closed: a request with no authenticated account is
 * denied with {@link HttpStatus#SC_UNAUTHORIZED}, and a request whose account
 * has no ACL entry matching the requested topic is denied with
 * {@link HttpStatus#SC_FORBIDDEN}. There is no permissive default — a role
 * with no {@code acl} entry, or an unconfigured {@code acl}, grants no access.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-topic-authorizer",
    interceptPoint = InterceptPoint.REQUEST_AFTER_AUTH,
    description = "Checks MQTT topic access against ACL"
)
public class MqttTopicAuthorizer implements WildcardInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttTopicAuthorizer.class);

    @Inject("config")
    private Map<String, Object> config;

    private Map<String, List<String>> acl;

    @OnInit
    public void init() {
        if (config != null) {
            Object aclConfig = config.get("acl");
            if (aclConfig instanceof Map<?, ?> map) {
                acl = parseAcl(map);
            }
        }
        if (acl == null) {
            acl = Map.of();
        }
        LOGGER.info("MqttTopicAuthorizer initialized with {} ACL entries", acl.size());
    }

    /**
     * Validates and converts the raw {@code acl} configuration map into a
     * {@code Map<String, List<String>>}, failing fast with a descriptive
     * {@link IllegalArgumentException} if any role key or topic pattern is not
     * a {@code String}, rather than deferring the failure to a
     * {@code ClassCastException} at request time.
     *
     * @param rawAcl the raw {@code acl} map read from the plugin configuration
     * @return an immutable, validated {@code Map<String, List<String>>}
     */
    private static Map<String, List<String>> parseAcl(Map<?, ?> rawAcl) {
        var result = new LinkedHashMap<String, List<String>>();

        for (var entry : rawAcl.entrySet()) {
            if (!(entry.getKey() instanceof String role)) {
                throw new IllegalArgumentException(
                    "Invalid mqtt-topic-authorizer 'acl' configuration: role key '" + entry.getKey()
                        + "' must be a string");
            }

            if (!(entry.getValue() instanceof List<?> rawTopics)) {
                throw new IllegalArgumentException(
                    "Invalid mqtt-topic-authorizer 'acl' configuration: value of role '" + role
                        + "' must be a list of topic patterns");
            }

            var topics = new ArrayList<String>(rawTopics.size());
            for (var rawTopic : rawTopics) {
                if (!(rawTopic instanceof String topic)) {
                    throw new IllegalArgumentException(
                        "Invalid mqtt-topic-authorizer 'acl' configuration: topic pattern '" + rawTopic
                            + "' for role '" + role + "' must be a string");
                }
                topics.add(topic);
            }

            result.put(role, List.copyOf(topics));
        }

        return Map.copyOf(result);
    }

    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        String path = request.getPath();
        return path.startsWith("/mqtt-sse") || path.startsWith("/mqtt");
    }

    @Override
    public void handle(ServiceRequest<?> request, ServiceResponse<?> response) {
        String topicFilter = extractTopic(request);

        // No topic in query string — let the service handle it
        if (topicFilter == null || topicFilter.isEmpty()) {
            return;
        }

        // With @RegisterPlugin(secure = true) an unauthenticated request should never
        // reach REQUEST_AFTER_AUTH, so a null account here is anomalous and must fail
        // closed rather than let the request through unchecked.
        var account = request.getAuthenticatedAccount();
        if (account == null) {
            LOGGER.debug("Denying unauthenticated request for topic '{}': no authenticated account", topicFilter);
            response.setInError(HttpStatus.SC_UNAUTHORIZED, "Authentication required for topic: " + topicFilter);
            return;
        }

        boolean allowed = false;
        for (String role : account.getRoles()) {
            List<String> roleTopics = acl.get(role);
            if (roleTopics != null && isTopicAllowed(topicFilter, roleTopics)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            LOGGER.debug("Topic '{}' denied for account '{}'", topicFilter, account.getPrincipal().getName());
            response.setInError(HttpStatus.SC_FORBIDDEN, "Not authorized for topic: " + topicFilter);
        }
    }

    /**
     * Extracts the topic filter from the request query string.
     */
    private String extractTopic(ServiceRequest<?> request) {
        var topicParam = request.getQueryParameters().get("topic");
        if (topicParam != null && !topicParam.isEmpty()) {
            return URLDecoder.decode(topicParam.getFirst(), StandardCharsets.UTF_8);
        }
        return null;
    }

    /**
     * Checks if a topic filter is allowed by any of the given topic patterns.
     *
     * @param topicFilter   the requested topic filter
     * @param allowedTopics the list of allowed topic patterns from ACL
     * @return {@code true} if the topic is allowed
     */
    static boolean isTopicAllowed(String topicFilter, List<String> allowedTopics) {
        if (allowedTopics == null || allowedTopics.isEmpty()) {
            return false;
        }

        for (String pattern : allowedTopics) {
            if (topicMatchesPattern(topicFilter, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a topic filter matches an ACL pattern using MQTT wildcard rules.
     *
     * @param topic   the topic filter to check
     * @param pattern the ACL pattern (may contain + and # wildcards)
     * @return {@code true} if the topic matches the pattern
     */
    static boolean topicMatchesPattern(String topic, String pattern) {
        if (pattern.equals("#")) {
            return true;
        }

        if (topic.equals(pattern)) {
            return true;
        }

        String[] topicLevels = topic.split("/");
        String[] patternLevels = pattern.split("/");

        // Multi-level wildcard: pattern ends with /#
        boolean hasMultiLevel = pattern.endsWith("/#");
        int effectivePatternLength = hasMultiLevel ? patternLevels.length - 1 : patternLevels.length;

        // Topic must have at least as many levels as the pattern (excluding #)
        if (topicLevels.length < effectivePatternLength) {
            return false;
        }

        // If no multi-level wildcard, level count must match exactly
        if (!hasMultiLevel && topicLevels.length != patternLevels.length) {
            return false;
        }

        // Check each level up to the multi-level wildcard
        for (int i = 0; i < effectivePatternLength; i++) {
            String patternLevel = patternLevels[i];
            String topicLevel = topicLevels[i];

            if (patternLevel.equals("+")) {
                continue; // single-level wildcard matches any level
            }

            if (!patternLevel.equals(topicLevel)) {
                return false;
            }
        }

        return true;
    }
}
