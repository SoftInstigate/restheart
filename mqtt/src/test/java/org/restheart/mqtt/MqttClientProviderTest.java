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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for MqttClientProvider configuration parsing and initialization.
 * Tests verify that the provider correctly parses configuration and initializes
 * MqttClientSingleton with the expected values.
 *
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttClientProviderTest {

    private MqttClientProvider provider;
    private Map<String, Object> config;

    @BeforeEach
    public void setUp() {
        provider = new MqttClientProvider();
        config = new HashMap<>();
        resetMqttClientSingleton();
    }

    @AfterEach
    public void tearDown() {
        resetMqttClientSingleton();
    }

    private void resetMqttClientSingleton() {
        try {
            Field initializedField = MqttClientSingleton.class.getDeclaredField("initialized");
            initializedField.setAccessible(true);
            initializedField.setBoolean(null, false);

            Field configField = MqttClientSingleton.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(null, null);
        } catch (Exception e) {
            // Ignore if field doesn't exist or can't be accessed
        }
    }

    private void injectConfig() throws Exception {
        Field configField = MqttClientProvider.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(provider, config);
    }

    private MqttConfig getSingletonConfig() {
        return MqttClientSingleton.getConfig();
    }

    private void callInitWithoutConnection() throws Exception {
        injectConfig();

        MqttClientSingleton mockSingleton = mock(MqttClientSingleton.class);

        try (var mockedStatic = mockStatic(MqttClientSingleton.class, Mockito.CALLS_REAL_METHODS)) {
            mockedStatic.when(MqttClientSingleton::getInstance).thenReturn(mockSingleton);
            mockedStatic.when(MqttClientSingleton::getConfig).thenCallRealMethod();
            mockedStatic.when(MqttClientSingleton::isInitialized).thenCallRealMethod();

            provider.init();
        }
    }

    @Test
    @DisplayName("Test default configuration values")
    public void testDefaultConfiguration() throws Exception {
        callInitWithoutConnection();

        assertTrue(MqttClientSingleton.isInitialized());
        MqttConfig cfg = getSingletonConfig();
        assertEquals("tcp://localhost:1883", cfg.getBrokerUrl());
        assertEquals(3, cfg.getProtocolVersion());
        assertTrue(cfg.getClientId().startsWith("restheart-"));
        assertNull(cfg.getUsername());
        assertNull(cfg.getPassword());
        assertEquals(false, cfg.isCleanSession());
        assertEquals(60, cfg.getKeepAliveSeconds());
        assertEquals(0xFFFFFFFFL, cfg.getSessionExpirySeconds());
        assertEquals(10, cfg.getConnectTimeoutSeconds());
        assertEquals(false, cfg.isTlsEnabled());
        assertEquals(true, cfg.getReconnectConfig().isEnabled());
        assertEquals(1000L, cfg.getReconnectConfig().getInitialDelayMs());
        assertEquals(30000L, cfg.getReconnectConfig().getMaxDelayMs());
        assertNull(cfg.getWillConfig().getWillTopic());
        assertNull(cfg.getWillConfig().getWillPayload());
        assertEquals(0, cfg.getWillConfig().getWillQos());
        assertEquals(false, cfg.getWillConfig().getWillRetain());
        assertEquals(0L, cfg.getWillConfig().getWillDelaySeconds());
        assertNull(cfg.getWillConfig().getWillMessageExpirySeconds());
    }

    @Test
    @DisplayName("Test custom broker URL configuration")
    public void testCustomBrokerUrl() throws Exception {
        config.put("broker-url", "ssl://mqtt.example.com:8883");
        callInitWithoutConnection();

        assertEquals("ssl://mqtt.example.com:8883", getSingletonConfig().getBrokerUrl());
    }

    @Test
    @DisplayName("Test MQTT 5 protocol version is parsed correctly")
    public void testMqtt5ProtocolVersion() throws Exception {
        config.put("protocol-version", 5);
        callInitWithoutConnection();

        assertEquals(5, getSingletonConfig().getProtocolVersion());
    }

    @Test
    @DisplayName("Test authentication credentials are parsed correctly")
    public void testAuthenticationConfiguration() throws Exception {
        config.put("username", "testuser");
        config.put("password", "testpass");
        callInitWithoutConnection();

        assertEquals("testuser", getSingletonConfig().getUsername());
        assertEquals("testpass", getSingletonConfig().getPassword());
    }

    @Test
    @DisplayName("Test session configuration is parsed correctly")
    public void testSessionConfiguration() throws Exception {
        config.put("clean-session", true);
        config.put("keep-alive-seconds", 120);
        config.put("session-expiry-seconds", 3600L);
        callInitWithoutConnection();

        assertEquals(true, getSingletonConfig().isCleanSession());
        assertEquals(120, getSingletonConfig().getKeepAliveSeconds());
        assertEquals(3600L, getSingletonConfig().getSessionExpirySeconds());
    }

    @Test
    @DisplayName("Test TLS configuration is parsed correctly")
    public void testTlsConfiguration() throws Exception {
        config.put("tls", true);
        callInitWithoutConnection();

        assertEquals(true, getSingletonConfig().isTlsEnabled());
    }

    @Test
    @DisplayName("Test reconnect configuration with nested map is parsed correctly")
    public void testReconnectConfiguration() throws Exception {
        Map<String, Object> reconnect = new HashMap<>();
        reconnect.put("enabled", false);
        reconnect.put("initial-delay-ms", 2000L);
        reconnect.put("max-delay-ms", 60000L);
        config.put("reconnect", reconnect);
        callInitWithoutConnection();

        assertEquals(false, getSingletonConfig().getReconnectConfig().isEnabled());
        assertEquals(2000L, getSingletonConfig().getReconnectConfig().getInitialDelayMs());
        assertEquals(60000L, getSingletonConfig().getReconnectConfig().getMaxDelayMs());
    }

    @Test
    @DisplayName("Test will message configuration for MQTT 3 is parsed correctly")
    public void testWillMessageConfigurationMqtt3() throws Exception {
        Map<String, Object> will = new HashMap<>();
        will.put("topic", "device/status");
        will.put("payload", "offline");
        will.put("qos", 1);
        will.put("retain", true);
        config.put("will", will);
        config.put("protocol-version", 3);
        callInitWithoutConnection();

        assertEquals("device/status", getSingletonConfig().getWillConfig().getWillTopic());
        assertEquals("offline", getSingletonConfig().getWillConfig().getWillPayload());
        assertEquals(1, getSingletonConfig().getWillConfig().getWillQos());
        assertEquals(true, getSingletonConfig().getWillConfig().getWillRetain());
        assertEquals(0L, getSingletonConfig().getWillConfig().getWillDelaySeconds());
        assertNull(getSingletonConfig().getWillConfig().getWillMessageExpirySeconds());
    }

    @Test
    @DisplayName("Test will message configuration for MQTT 5 with delay and expiry is parsed correctly")
    public void testWillMessageConfigurationMqtt5() throws Exception {
        Map<String, Object> will = new HashMap<>();
        will.put("topic", "device/status");
        will.put("payload", "offline");
        will.put("qos", 2);
        will.put("retain", false);
        will.put("delay-seconds", 30L);
        will.put("message-expiry-seconds", 3600L);
        config.put("will", will);
        config.put("protocol-version", 5);
        callInitWithoutConnection();

        assertEquals("device/status", getSingletonConfig().getWillConfig().getWillTopic());
        assertEquals("offline", getSingletonConfig().getWillConfig().getWillPayload());
        assertEquals(2, getSingletonConfig().getWillConfig().getWillQos());
        assertEquals(false, getSingletonConfig().getWillConfig().getWillRetain());
        assertEquals(30L, getSingletonConfig().getWillConfig().getWillDelaySeconds());
        assertEquals(3600L, getSingletonConfig().getWillConfig().getWillMessageExpirySeconds());
    }

    @Test
    @DisplayName("Test will message with null topic and payload defaults correctly")
    public void testWillMessageWithNullValues() throws Exception {
        Map<String, Object> will = new HashMap<>();
        will.put("topic", null);
        will.put("payload", null);
        config.put("will", will);
        callInitWithoutConnection();

        assertNull(getSingletonConfig().getWillConfig().getWillTopic());
        assertNull(getSingletonConfig().getWillConfig().getWillPayload());
        assertEquals(0, getSingletonConfig().getWillConfig().getWillQos());
        assertEquals(false, getSingletonConfig().getWillConfig().getWillRetain());
    }

    @Test
    @DisplayName("Test will message expiry as null (no expiry) is handled correctly")
    public void testWillMessageExpiryNull() throws Exception {
        Map<String, Object> will = new HashMap<>();
        will.put("topic", "test/topic");
        will.put("payload", "test");
        will.put("message-expiry-seconds", null);
        config.put("will", will);
        callInitWithoutConnection();

        assertNull(getSingletonConfig().getWillConfig().getWillMessageExpirySeconds());
    }

    @Test
    @DisplayName("Test configuration with only required fields uses defaults for optional fields")
    public void testConfigurationWithMissingOptionalFields() throws Exception {
        config.put("broker-url", "tcp://localhost:1883");
        callInitWithoutConnection();

        assertEquals("tcp://localhost:1883", getSingletonConfig().getBrokerUrl());
        assertNull(getSingletonConfig().getUsername());
        assertNull(getSingletonConfig().getPassword());
        assertNull(getSingletonConfig().getWillConfig().getWillTopic());
        assertNull(getSingletonConfig().getWillConfig().getWillPayload());
        assertEquals(true, getSingletonConfig().getReconnectConfig().isEnabled());
    }

    @Test
    @DisplayName("Test custom client ID is parsed correctly")
    public void testCustomClientId() throws Exception {
        config.put("client-id", "my-custom-client");
        callInitWithoutConnection();

        assertEquals("my-custom-client", getSingletonConfig().getClientId());
    }

    @Test
    @DisplayName("Test connect timeout is parsed correctly")
    public void testConnectTimeout() throws Exception {
        config.put("connect-timeout-seconds", 30);
        callInitWithoutConnection();

        assertEquals(30, getSingletonConfig().getConnectTimeoutSeconds());
    }
}
