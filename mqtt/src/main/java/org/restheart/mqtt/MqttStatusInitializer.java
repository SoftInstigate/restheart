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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.restheart.configuration.Configuration;
import org.restheart.plugins.InitPoint;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Inject;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostic sentinel for the {@code mqtt} module.
 * <p>
 * The module is dormant in two tiers: {@code mqtt-client} is the switch for the whole module,
 * and {@code mqtt-sse} / {@code mqtt-rest} / {@code mqtt-mongo-writer} each opt back in
 * separately. Because a disabled plugin is never instantiated, none of those plugins can warn
 * about its own misconfiguration - by the time something would need to log a warning, it does
 * not exist. This initializer is the one thing that runs regardless, so it is the only place
 * that can compare what the configuration <em>says</em> ({@link Configuration#toMap()}) against
 * what the {@link PluginsRegistry} says actually happened.
 * </p>
 * <p>
 * <strong>The enablement trap.</strong> {@link PluginRecord#isEnabled(boolean, Map)} returns the
 * plugin's compiled default when its configuration block exists but carries no {@code enabled}
 * key. A block such as
 * <pre>
 * mqtt-client:
 *   broker-url: "tcp://broker:1883"
 * </pre>
 * therefore leaves the plugin at its compiled default - disabled, for every Tier 1/2 mqtt plugin
 * - with configuration that looks correct and produces no error, because a disabled plugin is
 * never instantiated to complain about it. Flagging this is this class's single most valuable
 * job.
 * </p>
 * <p>
 * All decision logic lives in the package-private, static {@link #findings(Configuration,
 * PluginsRegistry)}, which is exercised directly by unit tests; {@link #init()} is a thin
 * wrapper that logs each {@link Finding} and never lets an unexpected configuration shape
 * propagate and take the server down.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-status",
    description = "Logs a startup diagnostic summary of the mqtt module's enablement and configuration",
    enabledByDefault = true,
    initPoint = InitPoint.AFTER_STARTUP
)
public class MqttStatusInitializer implements Initializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttStatusInitializer.class);

    /**
     * The six plugins that make up the {@code mqtt} module, in the order they are reported.
     */
    private static final List<String> MQTT_PLUGIN_NAMES = List.of(
        "mqtt-client", "mqtt-router", "mqtt-sse", "mqtt-rest", "mqtt-mongo-writer", "mqtt-topic-authorizer");

    /**
     * The full RESTHeart configuration, used to read the configuration blocks of the other
     * {@code mqtt-*} plugins - {@code getConfArgs()} on their own {@link PluginRecord} would not
     * help here, since a disabled plugin (which is exactly the case this class needs to inspect)
     * is never instantiated and so never gets a {@code PluginRecord} at all.
     */
    @Inject("rh-config")
    private Configuration config;

    /**
     * The plugin registry, used to determine which plugins are actually active - as opposed to
     * merely configured - since {@link PluginsRegistry}'s getters only ever return enabled
     * plugins.
     */
    @Inject("registry")
    private PluginsRegistry registry;

    /**
     * Severity of a {@link Finding}.
     */
    enum Level {
        INFO, WARN
    }

    /**
     * A single diagnostic conclusion, paired with the level it should be logged at.
     *
     * @param level the severity to log this finding at
     * @param message the human-readable diagnostic message
     */
    record Finding(Level level, String message) {
    }

    /**
     * Runs the diagnostic once, at {@code AFTER_STARTUP}, and logs every finding.
     * <p>
     * Wrapped in a catch-all: an unexpected shape in the configuration map (e.g. a plugin's
     * block present but not a {@code Map}) must never prevent - or even distract from - the
     * server having already started successfully, so any {@link RuntimeException} raised while
     * evaluating the diagnostic is logged at DEBUG and swallowed here.
     * </p>
     */
    @Override
    public void init() {
        try {
            for (var finding : findings(config, registry)) {
                switch (finding.level()) {
                    case WARN -> LOGGER.warn(finding.message());
                    case INFO -> LOGGER.info(finding.message());
                }
            }
        } catch (RuntimeException e) {
            LOGGER.debug("mqtt-status: skipping mqtt module diagnostic, configuration had an unexpected shape", e);
        }
    }

    /**
     * Computes the diagnostic findings for the current configuration and registry state.
     * <p>
     * Package-private and static so it can be exercised by unit tests against hand-built
     * {@link Configuration}/{@link PluginsRegistry} inputs, with no live server required.
     * </p>
     *
     * @param config the full RESTHeart configuration
     * @param registry the plugin registry
     * @return the list of findings; never empty - a fully healthy setup still returns a single
     *         {@link Level#INFO} summary
     */
    static List<Finding> findings(Configuration config, PluginsRegistry registry) {
        var confMap = config.toMap();
        var findings = new ArrayList<Finding>();

        var clientActive = isActive(registry, "mqtt-client");
        // Say nothing at all when nobody has expressed any intent to use the module. Since the
        // module ships with RESTHeart, an unconditional "installed but inactive" line would greet
        // every installation at every startup - including the majority that will never use MQTT -
        // inviting a question the operator never asked. The advice is worth printing only once
        // some mqtt-* configuration exists and has not taken effect, which is the trap this
        // sentinel was written for.
        if (!clientActive && anyMqttBlockPresent(confMap)) {
            findings.add(new Finding(Level.INFO,
                "mqtt module is installed but inactive: mqtt-client is the root of its injection graph and is "
                    + "disabled, so mqtt-router, mqtt-sse, mqtt-rest, mqtt-mongo-writer and mqtt-topic-authorizer "
                    + "are all unreachable regardless of their own configuration; set /mqtt-client/enabled to "
                    + "true to activate the module"));
        }

        for (var name : MQTT_PLUGIN_NAMES) {
            var block = asMap(confMap.get(name));
            if (block != null && !block.containsKey("enabled") && !isActive(registry, name)) {
                findings.add(new Finding(Level.WARN,
                    "configuration block '" + name + "' is present but has no 'enabled' key, and " + name
                        + " is not currently active; without an explicit 'enabled: true' this configuration has "
                        + "no effect"));
            }
        }

        var restActive = isActive(registry, "mqtt-rest");
        var sseActive = isActive(registry, "mqtt-sse");
        var mongoWriterActive = isActive(registry, "mqtt-mongo-writer");

        var routerBlock = asMap(confMap.get("mqtt-router"));
        var lastMessageCacheEnabled = asBoolean(routerBlock == null ? null : routerBlock.get("last-message-cache"), true);
        var hasSubscriptions = isNonEmptyList(routerBlock == null ? null : routerBlock.get("subscriptions"));

        var mongoWriterBlock = asMap(confMap.get("mqtt-mongo-writer"));
        var hasMongoSink = isNonEmptyList(mongoWriterBlock == null ? null : mongoWriterBlock.get("mongo-sink"));

        if (restActive && (!lastMessageCacheEnabled || (!hasSubscriptions && !hasMongoSink && !sseActive))) {
            findings.add(new Finding(Level.WARN,
                "mqtt-rest is active but nothing will populate its last-message cache (no mqtt-router.subscriptions, "
                    + "no mqtt-mongo-writer.mongo-sink, and mqtt-sse is not active) or mqtt-router.last-message-cache "
                    + "is disabled; GET /mqtt will answer 404 until something subscribes"));
        }

        if (mongoWriterActive && !hasMongoSink) {
            findings.add(new Finding(Level.WARN,
                "mqtt-mongo-writer is active but mqtt-mongo-writer.mongo-sink is empty or absent; its drain loop "
                    + "will run but write nothing to MongoDB"));
        }

        var authorizerBlock = asMap(confMap.get("mqtt-topic-authorizer"));
        var hasAcl = isNonEmptyMap(authorizerBlock == null ? null : authorizerBlock.get("acl"));
        if ((sseActive || restActive) && !hasAcl) {
            findings.add(new Finding(Level.WARN,
                "mqtt-sse or mqtt-rest is active while mqtt-topic-authorizer.acl is empty or absent; every request "
                    + "will be denied with 403"));
        }

        // The summary is worth a line only once someone is using the module. Since it ships with
        // RESTHeart, printing it unconditionally would put an mqtt line in front of every
        // operator at every startup, most of whom never asked for MQTT.
        if (findings.isEmpty() && anyMqttBlockPresent(confMap)) {
            var active = MQTT_PLUGIN_NAMES.stream().filter(name -> isActive(registry, name)).toList();
            var inactive = MQTT_PLUGIN_NAMES.stream().filter(name -> !isActive(registry, name)).toList();
            findings.add(new Finding(Level.INFO,
                "mqtt module active: " + String.join(", ", active)
                    + (inactive.isEmpty() ? "" : "; inactive: " + String.join(", ", inactive))));
        }

        return findings;
    }

    /**
     * Whether the plugin named {@code name} is currently active, i.e. present and enabled in one
     * of the registry's five plugin-kind collections. {@code mqtt-client} and {@code mqtt-router}
     * are {@code Provider}s, {@code mqtt-sse} is an {@code SseService}, {@code mqtt-rest} is a
     * {@code Service}, {@code mqtt-mongo-writer} is an {@code Initializer} and
     * {@code mqtt-topic-authorizer} is an {@code Interceptor}; checking all five collections
     * rather than special-casing each name keeps this correct even if that mapping changes.
     */
    private static boolean isActive(PluginsRegistry registry, String name) {
        return containsActive(registry.getProviders(), name)
            || containsActive(registry.getServices(), name)
            || containsActive(registry.getSseServices(), name)
            || containsActive(registry.getInitializers(), name)
            || containsActive(registry.getInterceptors(), name);
    }

    private static boolean containsActive(Set<? extends PluginRecord<?>> plugins, String name) {
        return plugins.stream().anyMatch(pr -> name.equals(pr.getName()) && pr.isEnabled());
    }

    @SuppressWarnings("unchecked")
    /**
     * Whether the configuration carries any {@code mqtt-*} block at all, i.e. whether anyone has
     * touched this module's configuration.
     *
     * @param confMap the full configuration as a map
     * @return {@code true} if at least one {@code mqtt-*} plugin has a configuration block
     */
    private static boolean anyMqttBlockPresent(Map<String, Object> confMap) {
        return MQTT_PLUGIN_NAMES.stream().anyMatch(confMap::containsKey);
    }

    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map<?, ?> m) ? (Map<String, Object>) m : null;
    }

    private static boolean isNonEmptyList(Object o) {
        return (o instanceof List<?> l) && !l.isEmpty();
    }

    private static boolean isNonEmptyMap(Object o) {
        return (o instanceof Map<?, ?> m) && !m.isEmpty();
    }

    private static boolean asBoolean(Object o, boolean defaultValue) {
        return (o instanceof Boolean b) ? b : defaultValue;
    }
}
