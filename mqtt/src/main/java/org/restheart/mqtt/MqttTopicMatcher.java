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

/**
 * Matches a concrete MQTT topic against a topic filter, following MQTT 3.1.1/5.0 topic matching
 * semantics.
 * <p>
 * Rules implemented:
 * <ul>
 *   <li>{@code #} alone matches every topic (subject to the {@code $}-prefix exclusion below);</li>
 *   <li>a filter ending in {@code /#} matches the parent level itself as well as any of its
 *       descendants, e.g. {@code sport/#} matches {@code sport}, {@code sport/x} and
 *       {@code sport/x/y};</li>
 *   <li>{@code +} matches exactly one topic level, including an empty level;</li>
 *   <li>outside of a trailing {@code #}, the topic and the filter must have the same number of
 *       levels;</li>
 *   <li>a filter level is never treated as a plain prefix of a longer sibling level, so
 *       {@code sensors/#} does not match {@code sensorsPrivate/x};</li>
 *   <li>per the specification, a filter whose first level is a wildcard ({@code #} or {@code +})
 *       never matches a topic whose first level begins with {@code $} (e.g. {@code $SYS/...}).</li>
 * </ul>
 * <p>
 * This class is used both by {@link MqttMessageRouter} (matching an incoming publish against
 * registered topic filters) and by {@link MqttMongoWriter} (matching a buffered message against a
 * configured sink filter). {@link MqttTopicAuthorizer} deliberately does not use this class, since
 * it matches a requested filter against an ACL pattern, which is a different relation.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public final class MqttTopicMatcher {

    private MqttTopicMatcher() {
        // utility class
    }

    /**
     * Determines whether the given concrete topic matches the given topic filter.
     *
     * @param topic  the concrete topic a message was published on
     * @param filter the topic filter (possibly containing {@code +}/{@code #} wildcards) to match
     *               against
     * @return {@code true} if {@code topic} matches {@code filter}, {@code false} otherwise
     */
    public static boolean matches(String topic, String filter) {
        if (topic == null || filter == null) {
            return false;
        }

        String[] topicLevels = topic.split("/", -1);
        String[] filterLevels = filter.split("/", -1);

        boolean topicIsSystem = topicLevels.length > 0 && topicLevels[0].startsWith("$");
        boolean filterStartsWithWildcard = filterLevels.length > 0
            && ("#".equals(filterLevels[0]) || "+".equals(filterLevels[0]));
        if (topicIsSystem && filterStartsWithWildcard) {
            return false;
        }

        int fi = 0;
        int ti = 0;
        while (fi < filterLevels.length) {
            String filterLevel = filterLevels[fi];

            if ("#".equals(filterLevel)) {
                // multi-level wildcard: matches the current level (if any) and everything below it
                return true;
            }

            if (ti >= topicLevels.length) {
                return false;
            }

            if (!"+".equals(filterLevel) && !filterLevel.equals(topicLevels[ti])) {
                return false;
            }

            fi++;
            ti++;
        }

        return ti == topicLevels.length;
    }
}
