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
 * The requested value is itself an MQTT topic <em>filter</em> (it may contain
 * {@code +}/{@code #} wildcards), not a concrete topic. An ACL entry therefore
 * does not match it the way a filter matches a topic; instead the ACL pattern
 * must <em>cover</em> the requested filter, i.e. every concrete topic the
 * requested filter could ever select must already be selected by the ACL
 * pattern. See {@link #patternCovers(String, String)} for the precise rules.
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

    /**
     * Matches on a path segment boundary rather than a plain
     * {@code String.startsWith}, and deliberately does <strong>not</strong>
     * collapse to an exact-match {@code path.equals("/mqtt")}. Both
     * {@code MqttRestService} ({@code /mqtt}, via
     * {@code RegisterPlugin.uriMatchPolicy()}'s default of
     * {@code MATCH_POLICY.PREFIX}) and the SSE pipeline
     * ({@code /mqtt-sse}, registered with {@code MATCH_POLICY.PREFIX}
     * unconditionally by {@code plugSseService}) are mounted as URI
     * <em>prefixes</em>, so requests such as {@code /mqtt/anything} or
     * {@code /mqtt-sse/anything} genuinely reach these services and
     * {@code MqttRestService.handleGet} reads the {@code topic} query
     * parameter regardless of the sub-path. An exact match here would stop
     * the ACL from ever applying to those sub-paths — a topic-authorization
     * bypass. Every sub-path of {@code /mqtt} and {@code /mqtt-sse} must
     * still be authorized, so segment-boundary matching (equal to the base,
     * or starting with base + "/") is what's required, not equality.
     */
    @Override
    public boolean resolve(ServiceRequest<?> request, ServiceResponse<?> response) {
        String path = request.getPath();
        return matchesBase(path, "/mqtt-sse") || matchesBase(path, "/mqtt");
    }

    /**
     * Returns {@code true} if {@code path} is exactly {@code base} or a
     * sub-path of it (i.e. {@code base} followed by a {@code /} segment
     * separator), never merely a string with {@code base} as a character
     * prefix.
     */
    private static boolean matchesBase(String path, String base) {
        return path.equals(base) || path.startsWith(base + "/");
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
            if (patternCovers(pattern, topicFilter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether an ACL pattern <em>covers</em> a requested topic
     * filter, i.e. whether every concrete topic the requested filter could
     * ever select is already selected by the ACL pattern. This is filter
     * <em>containment</em>, not topic matching.
     * <p>
     * The distinction matters because the value being authorized here is not
     * a concrete topic — it is itself a filter that the client is about to
     * subscribe with, and that filter may carry its own {@code +}/{@code #}
     * wildcards. Matching it against the ACL pattern as if it were a plain
     * topic (as {@link MqttTopicMatcher#matches(String, String)} does for the
     * router) treats those wildcard characters as ordinary literal level
     * names, which lets a client escalate its privileges simply by asking
     * for a broader filter than the one it was granted: an ACL of
     * {@code sensors/+} would wrongly appear to "match" a request for
     * {@code sensors/#}, even though {@code sensors/#} selects topics (e.g.
     * {@code sensors/a/b}) that {@code sensors/+} never grants.
     * </p>
     * <p>
     * Containment is decided level by level, comparing the ACL pattern
     * against the requested filter:
     * </p>
     * <ul>
     *   <li>an ACL level of {@code #} covers the requested level and every
     *       level below it — containment holds for the remainder,
     *       regardless of what the requested filter still contains;</li>
     *   <li>an ACL level of {@code +} covers a requested level that is a
     *       literal, or {@code +}, but <strong>not</strong> {@code #}, since
     *       {@code #} spans an unbounded number of levels that a single-level
     *       wildcard cannot vouch for;</li>
     *   <li>a literal ACL level covers only the identical literal requested
     *       level; it does not cover {@code +} or {@code #};</li>
     *   <li>if the ACL pattern runs out of levels while the requested filter
     *       still has some, containment fails;</li>
     *   <li>if the requested filter runs out while the ACL pattern still has
     *       levels, containment fails unless the sole remaining ACL level is
     *       {@code #};</li>
     *   <li>an ACL pattern of {@code #} alone covers everything.</li>
     * </ul>
     *
     * @param aclPattern     the ACL pattern granted to the account (may contain
     *                       {@code +} and {@code #} wildcards)
     * @param requestedFilter the topic filter the client is requesting to
     *                       subscribe to (may also contain wildcards)
     * @return {@code true} if {@code aclPattern} covers every topic that
     *         {@code requestedFilter} could ever select
     */
    static boolean patternCovers(String aclPattern, String requestedFilter) {
        if ("#".equals(aclPattern)) {
            return true;
        }

        if (aclPattern.equals(requestedFilter)) {
            return true;
        }

        String[] patternLevels = aclPattern.split("/");
        String[] requestedLevels = requestedFilter.split("/");

        int i = 0;
        for (; i < patternLevels.length; i++) {
            String patternLevel = patternLevels[i];

            if ("#".equals(patternLevel)) {
                // covers the requested level and everything below it, no
                // matter how many requested levels remain (including none)
                return true;
            }

            if (i >= requestedLevels.length) {
                // the requested filter ran out of levels; only a trailing
                // '#' (handled above) could still cover it
                return false;
            }

            String requestedLevel = requestedLevels[i];

            if ("+".equals(patternLevel)) {
                if ("#".equals(requestedLevel)) {
                    // a single-level wildcard cannot cover an unbounded
                    // multi-level wildcard
                    return false;
                }
                continue; // + covers a literal level or a + at this level
            }

            // a literal ACL level covers only the identical literal level
            if (!patternLevel.equals(requestedLevel)) {
                return false;
            }
        }

        // the ACL pattern is exhausted: containment holds only if the
        // requested filter is exhausted too
        return i == requestedLevels.length;
    }
}
