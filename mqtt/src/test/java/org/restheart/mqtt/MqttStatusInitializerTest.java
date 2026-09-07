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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.configuration.Configuration;
import org.restheart.mqtt.MqttStatusInitializer.Finding;
import org.restheart.mqtt.MqttStatusInitializer.Level;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.Interceptor;
import org.restheart.plugins.Plugin;
import org.restheart.plugins.PluginRecord;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.Provider;
import org.restheart.plugins.Service;
import org.restheart.plugins.SseService;

/**
 * Unit tests for {@link MqttStatusInitializer#findings(Configuration, PluginsRegistry)}, the
 * package-private decision logic behind the {@code mqtt-status} sentinel.
 * <p>
 * Every test hand-builds a {@link Configuration} mock (stubbing only {@code toMap()}, as the
 * sentinel reads nothing else from it) and a {@link PluginsRegistry} mock backed by real
 * {@link PluginRecord} instances - mirroring the fixtures in {@link MqttRouterProviderTest} - so
 * that {@code isEnabled()} runs its real logic rather than being stubbed to a fixed answer.
 * Findings are asserted on the returned list, never on log output.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttStatusInitializerTest {

    private static Configuration configOf(Map<String, Object> confMap) {
        var config = mock(Configuration.class);
        when(config.toMap()).thenReturn(confMap);
        return config;
    }

    private static <T extends Plugin> PluginRecord<T> record(String name, boolean enabledByDefault, Map<String, Object> confArgs) {
        return new PluginRecord<>(name, "test plugin " + name, false, enabledByDefault, "TestClass", null, confArgs);
    }

    /**
     * Builds a registry in which exactly the plugins named in {@code activeNames} are active
     * (present, with no {@code enabled: false} override), correctly distributed across the
     * registry's five plugin-kind collections the way the real {@code mqtt} plugins are:
     * {@code mqtt-client}/{@code mqtt-router} as providers, {@code mqtt-sse} as an SSE service,
     * {@code mqtt-rest} as a service, {@code mqtt-mongo-writer} as an initializer and
     * {@code mqtt-topic-authorizer} as an interceptor.
     */
    private static PluginsRegistry registryWithActive(String... activeNames) {
        var active = Set.of(activeNames);
        var registry = mock(PluginsRegistry.class);

        Set<PluginRecord<Provider<?>>> providers = new java.util.HashSet<>();
        if (active.contains("mqtt-client")) {
            providers.add(record("mqtt-client", false, Map.of("enabled", true)));
        }
        if (active.contains("mqtt-router")) {
            providers.add(record("mqtt-router", true, Map.of()));
        }
        when(registry.getProviders()).thenReturn(providers);

        Set<PluginRecord<SseService>> sseServices = new java.util.HashSet<>();
        if (active.contains("mqtt-sse")) {
            sseServices.add(record("mqtt-sse", false, Map.of("enabled", true)));
        }
        when(registry.getSseServices()).thenReturn(sseServices);

        Set<PluginRecord<Service<?, ?>>> services = new java.util.HashSet<>();
        if (active.contains("mqtt-rest")) {
            services.add(record("mqtt-rest", false, Map.of("enabled", true)));
        }
        when(registry.getServices()).thenReturn(services);

        Set<PluginRecord<Initializer>> initializers = new java.util.HashSet<>();
        if (active.contains("mqtt-mongo-writer")) {
            initializers.add(record("mqtt-mongo-writer", false, Map.of("enabled", true)));
        }
        when(registry.getInitializers()).thenReturn(initializers);

        Set<PluginRecord<Interceptor<?, ?>>> interceptors = new java.util.HashSet<>();
        if (active.contains("mqtt-topic-authorizer")) {
            interceptors.add(record("mqtt-topic-authorizer", true, Map.of()));
        }
        when(registry.getInterceptors()).thenReturn(interceptors);

        return registry;
    }

    private static boolean anyWarnContaining(List<Finding> findings, String substring) {
        return findings.stream().anyMatch(f -> f.level() == Level.WARN && f.message().contains(substring));
    }

    private static boolean anyInfoContaining(List<Finding> findings, String substring) {
        return findings.stream().anyMatch(f -> f.level() == Level.INFO && f.message().contains(substring));
    }

    // --- Case 1: module installed but off ---

    @Test
    @DisplayName("Case 1: with an mqtt block configured but the module off, one INFO names /mqtt-client/enabled")
    void testModuleInstalledButOff() {
        // Some mqtt configuration exists, so the operator has shown intent to use the module -
        // which is what makes the advice worth printing. The block carries an explicit "enabled"
        // key so it does not also trip the missing-enabled-key trap, which is Case 2's subject.
        var config = configOf(Map.of("mqtt-client", Map.of("enabled", false)));
        var registry = registryWithActive(); // nothing active

        var findings = MqttStatusInitializer.findings(config, registry);

        assertEquals(1, findings.size(), "only the module-off INFO should fire");
        assertEquals(Level.INFO, findings.get(0).level());
        assertTrue(findings.get(0).message().contains("/mqtt-client/enabled"),
            "the message must name the switch that would activate the module");
    }

    @Test
    @DisplayName("Case 1b: with no mqtt configuration at all the sentinel is silent, because the "
        + "module ships with RESTHeart and most installations never use it")
    void testUntouchedModuleIsSilent() {
        var config = configOf(Map.of());
        var registry = registryWithActive(); // nothing active

        var findings = MqttStatusInitializer.findings(config, registry);

        assertTrue(findings.isEmpty(),
            "nobody has configured anything under mqtt-*, so there is no misconfiguration to warn "
                + "about and no reason to put an mqtt line in front of an operator who never asked "
                + "for MQTT; got: " + findings);
    }

    // --- Case 2: the enablement trap (negative control) ---

    @Test
    @DisplayName("Case 2 (negative control - the trap): mqtt-client block with no 'enabled' key and mqtt-client inactive produces a WARN naming mqtt-client")
    void testEnablementTrapOnMqttClient() {
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("mqtt-client", Map.of("broker-url", "tcp://broker:1883")); // no 'enabled' key
        var config = configOf(confMap);
        var registry = registryWithActive(); // mqtt-client not active: enabledByDefault is false

        var findings = MqttStatusInitializer.findings(config, registry);

        assertTrue(anyWarnContaining(findings, "mqtt-client"),
            "a config block with no 'enabled' key, for a plugin that is not active, must produce a WARN naming it");
    }

    @Test
    @DisplayName("Case 2: a block that does carry an 'enabled' key (even if false) is not the trap and produces no WARN for it")
    void testExplicitEnabledKeyIsNotTheTrap() {
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("mqtt-client", Map.of("enabled", false, "broker-url", "tcp://broker:1883"));
        var config = configOf(confMap);
        var registry = registryWithActive();

        var findings = MqttStatusInitializer.findings(config, registry);

        assertFalse(anyWarnContaining(findings, "no 'enabled' key"),
            "an explicit 'enabled: false' is a deliberate choice, not the silent-default trap");
    }

    @Test
    @DisplayName("Case 2: a block with no 'enabled' key for a plugin that IS active produces no WARN")
    void testNoTrapWarnWhenPluginIsActiveDespiteMissingEnabledKey() {
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("mqtt-router", Map.of("max-inflight-messages-per-second", 5000)); // no 'enabled' key
        var config = configOf(confMap);
        var registry = registryWithActive("mqtt-client", "mqtt-router");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertFalse(anyWarnContaining(findings, "mqtt-router"),
            "mqtt-router defaults to enabled and is active here, so the missing key is not a trap");
    }

    // --- Case 3: mqtt-rest active but nothing populates the cache ---

    @Test
    @DisplayName("Case 3: mqtt-rest active, no subscriptions/mongo-sink, mqtt-sse inactive -> WARN about 404")
    void testMqttRestActiveWithNothingToPopulateCache() {
        var config = configOf(Map.of());
        var registry = registryWithActive("mqtt-client", "mqtt-rest");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertTrue(anyWarnContaining(findings, "404"),
            "mqtt-rest with nothing subscribing and no cache source must warn about answering 404");
    }

    @Test
    @DisplayName("Case 3: mqtt-rest active with last-message-cache explicitly false -> WARN, even if subscriptions exist")
    void testMqttRestActiveWithCacheDisabled() {
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("mqtt-router", Map.of("last-message-cache", false,
            "subscriptions", List.of(Map.of("topic", "sensors/#", "qos", 1))));
        var config = configOf(confMap);
        var registry = registryWithActive("mqtt-client", "mqtt-router", "mqtt-rest");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertTrue(anyWarnContaining(findings, "404"),
            "an explicitly disabled cache means mqtt-rest can never be populated, regardless of subscriptions");
    }

    @Test
    @DisplayName("Case 3: mqtt-rest active with router subscriptions configured produces no cache WARN")
    void testMqttRestActiveWithSubscriptionsIsFine() {
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("mqtt-router", Map.of("subscriptions", List.of(Map.of("topic", "sensors/#", "qos", 1))));
        var config = configOf(confMap);
        var registry = registryWithActive("mqtt-client", "mqtt-router", "mqtt-rest");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertFalse(anyWarnContaining(findings, "404"), "configured subscriptions populate the cache; no warning is due");
    }

    @Test
    @DisplayName("Case 3: mqtt-rest active with mqtt-sse also active produces no cache WARN")
    void testMqttRestActiveWithSseActiveIsFine() {
        var config = configOf(Map.of());
        var registry = registryWithActive("mqtt-client", "mqtt-rest", "mqtt-sse");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertFalse(anyWarnContaining(findings, "404"), "an active SSE consumer can populate the cache; no warning is due");
    }

    // --- Case 4: mqtt-mongo-writer active with empty/absent mongo-sink ---

    @Test
    @DisplayName("Case 4: mqtt-mongo-writer active with mongo-sink absent -> WARN")
    void testMongoWriterActiveWithAbsentSink() {
        var config = configOf(Map.of());
        var registry = registryWithActive("mqtt-client", "mqtt-mongo-writer");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertTrue(anyWarnContaining(findings, "mongo-sink"), "an absent mongo-sink means the drain loop writes nothing");
    }

    @Test
    @DisplayName("Case 4: mqtt-mongo-writer active with mongo-sink an empty list -> WARN")
    void testMongoWriterActiveWithEmptySink() {
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("mqtt-mongo-writer", Map.of("mongo-sink", List.of()));
        var config = configOf(confMap);
        var registry = registryWithActive("mqtt-client", "mqtt-mongo-writer");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertTrue(anyWarnContaining(findings, "mongo-sink"));
    }

    @Test
    @DisplayName("Case 4: mqtt-mongo-writer active with a non-empty mongo-sink produces no WARN")
    void testMongoWriterActiveWithConfiguredSinkIsFine() {
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("mqtt-mongo-writer", Map.of("mongo-sink",
            List.of(Map.of("topic", "sensors/#", "database", "iot", "collection", "sensor-events"))));
        var config = configOf(confMap);
        var registry = registryWithActive("mqtt-client", "mqtt-mongo-writer");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertFalse(anyWarnContaining(findings, "mongo-sink"));
    }

    // --- Case 5: mqtt-sse/mqtt-rest active with no ACL ---

    @Test
    @DisplayName("Case 5: mqtt-sse active with mqtt-topic-authorizer.acl absent -> WARN about 403")
    void testSseActiveWithAbsentAcl() {
        var config = configOf(Map.of());
        var registry = registryWithActive("mqtt-client", "mqtt-sse", "mqtt-topic-authorizer");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertTrue(anyWarnContaining(findings, "403"));
    }

    @Test
    @DisplayName("Case 5: mqtt-rest active with mqtt-topic-authorizer.acl an empty map -> WARN about 403")
    void testRestActiveWithEmptyAcl() {
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("mqtt-topic-authorizer", Map.of("acl", Map.of()));
        var config = configOf(confMap);
        var registry = registryWithActive("mqtt-client", "mqtt-rest", "mqtt-topic-authorizer");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertTrue(anyWarnContaining(findings, "403"));
    }

    @Test
    @DisplayName("Case 5: mqtt-sse active with a non-empty ACL produces no 403 WARN")
    void testSseActiveWithConfiguredAclIsFine() {
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("mqtt-topic-authorizer", Map.of("acl", Map.of("iot-reader", List.of("sensors/#"))));
        var config = configOf(confMap);
        var registry = registryWithActive("mqtt-client", "mqtt-sse", "mqtt-topic-authorizer");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertFalse(anyWarnContaining(findings, "403"));
    }

    @Test
    @DisplayName("Case 5: neither mqtt-sse nor mqtt-rest active means an absent ACL is irrelevant")
    void testNoAclWarnWhenNeitherEndpointActive() {
        var config = configOf(Map.of());
        var registry = registryWithActive("mqtt-client", "mqtt-router");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertFalse(anyWarnContaining(findings, "403"));
    }

    // --- Case 6: fully healthy setup ---

    @Test
    @DisplayName("Case 6: a fully-configured healthy setup produces exactly one INFO and no WARN")
    void testFullyHealthySetupProducesExactlyOneInfoAndNoWarn() {
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("mqtt-client", Map.of("enabled", true, "broker-url", "tcp://broker:1883"));
        confMap.put("mqtt-router", Map.of("enabled", true, "last-message-cache", true,
            "subscriptions", List.of(Map.of("topic", "sensors/#", "qos", 1))));
        confMap.put("mqtt-sse", Map.of("enabled", true));
        confMap.put("mqtt-rest", Map.of("enabled", true));
        confMap.put("mqtt-mongo-writer", Map.of("enabled", true, "mongo-sink",
            List.of(Map.of("topic", "sensors/#", "database", "iot", "collection", "sensor-events"))));
        confMap.put("mqtt-topic-authorizer", Map.of("acl", Map.of("iot-reader", List.of("sensors/#"))));

        var config = configOf(confMap);
        var registry = registryWithActive(
            "mqtt-client", "mqtt-router", "mqtt-sse", "mqtt-rest", "mqtt-mongo-writer", "mqtt-topic-authorizer");

        var findings = MqttStatusInitializer.findings(config, registry);

        assertEquals(1, findings.size(), "a healthy setup must be reported in exactly one line: " + findings);
        assertEquals(Level.INFO, findings.get(0).level());
        assertTrue(anyInfoContaining(findings, "mqtt module active"));
    }

    @Test
    @DisplayName("Case 6: with an mqtt block configured and nothing active, the module-off INFO is "
        + "the only finding - no separate summary line")
    void testConfiguredButInactiveProducesOnlyModuleOffInfo() {
        var config = configOf(Map.of("mqtt-client", Map.of("enabled", false)));
        var registry = registryWithActive();

        var findings = MqttStatusInitializer.findings(config, registry);

        assertEquals(1, findings.size());
        assertEquals(Level.INFO, findings.get(0).level());
    }

    // --- Robustness: init() must never throw, even with defensively-hostile inputs ---

    @Test
    @DisplayName("findings() tolerates a config block that is not a Map (e.g. a String) without throwing")
    void testNonMapBlockDoesNotThrow() {
        Map<String, Object> confMap = new HashMap<>();
        confMap.put("mqtt-client", "not-a-map");
        var config = configOf(confMap);
        var registry = registryWithActive();

        var findings = MqttStatusInitializer.findings(config, registry);

        assertEquals(1, findings.size(), "a non-Map block is simply not treated as a config block; only the module-off INFO fires");
    }

    @Test
    @DisplayName("init() never throws even if the injected Configuration.toMap() itself throws")
    void testInitSwallowsUnexpectedRuntimeExceptions() throws Exception {
        var config = mock(Configuration.class);
        when(config.toMap()).thenThrow(new RuntimeException("boom"));
        var registry = registryWithActive();

        var initializer = new MqttStatusInitializer();
        setField(initializer, "config", config);
        setField(initializer, "registry", registry);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(initializer::init,
            "a diagnostic that takes the server down is worse than no diagnostic");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
