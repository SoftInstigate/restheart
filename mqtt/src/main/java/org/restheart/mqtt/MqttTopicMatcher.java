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

    /**
     * Determines whether every topic matching {@code narrower} also matches {@code broader}, i.e.
     * whether a broker subscription to {@code broader} already delivers everything a subscription
     * to {@code narrower} would.
     * <p>
     * This is a relation between two <em>filters</em>, not between a topic and a filter, and it
     * exists so the router can subscribe to a minimal covering set on the broker and fan out
     * locally. Overlapping broker subscriptions are not harmless: MQTT 3.1.1 lets a broker deliver
     * one copy of a message per matching subscription, and Mosquitto does exactly that, so a
     * client subscribed to both {@code sensors/#} and {@code sensors/temp} receives every
     * {@code sensors/temp} message twice.
     * </p>
     * <p>
     * Note the asymmetry with a wildcard's treatment of {@code $}-prefixed topics: {@code #} does
     * <strong>not</strong> subsume {@code $SYS/#}, because {@code $SYS/x} matches the latter and
     * not the former. Two filters that both begin with a wildcard are unaffected, since both
     * exclude the same {@code $} topics.
     * </p>
     *
     * @param broader  the candidate covering filter
     * @param narrower the filter that would be covered
     * @return {@code true} if {@code broader} matches every topic {@code narrower} matches
     */
    public static boolean subsumes(String broader, String narrower) {
        if (broader == null || narrower == null) {
            return false;
        }
        if (broader.equals(narrower)) {
            return true;
        }

        String[] b = broader.split("/", -1);
        String[] n = narrower.split("/", -1);

        // A wildcard first level never reaches $-topics, so a filter that names one explicitly
        // reaches topics the wildcard cannot and is therefore not covered by it.
        boolean broaderStartsWithWildcard = b.length > 0 && ("#".equals(b[0]) || "+".equals(b[0]));
        boolean narrowerNamesSystem = n.length > 0 && n[0].startsWith("$");
        if (broaderStartsWithWildcard && narrowerNamesSystem) {
            return false;
        }

        int i = 0;
        while (i < b.length) {
            String bl = b[i];

            if ("#".equals(bl)) {
                // Absorbs every remaining level of the narrower filter, and matches the parent
                // level itself, so nothing further need be checked.
                return true;
            }

            if (i >= n.length) {
                // The broader filter still demands levels the narrower one does not have.
                return false;
            }

            String nl = n[i];

            if ("+".equals(bl)) {
                // A single-level wildcard covers any single level, but never a "#" - that spans
                // any number of levels, including more than one.
                if ("#".equals(nl)) {
                    return false;
                }
            } else if (!bl.equals(nl)) {
                // A literal level covers only itself: a "+" or "#" here is the wider of the two.
                return false;
            }

            i++;
        }

        return i == n.length;
    }
}
