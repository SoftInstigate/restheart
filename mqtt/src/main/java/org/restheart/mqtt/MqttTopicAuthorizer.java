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

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> acl;

    @OnInit
    public void init() {
        if (config != null) {
            Object aclConfig = config.get("acl");
            if (aclConfig instanceof Map<?, ?> map) {
                acl = (Map<String, List<String>>) map;
            }
        }
        if (acl == null) {
            acl = Map.of();
        }
        LOGGER.info("MqttTopicAuthorizer initialized with {} ACL entries", acl.size());
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

        // Check if any of the account's roles grant access to this topic
        var account = request.getAuthenticatedAccount();
        if (account == null) {
            return; // no auth — let the auth pipeline handle it
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
