/*-
 * ========================LICENSE_START=================================
 * restheart-mongoclient-provider
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
 * Unit tests for MqttClientProvider configuration parsing and initialization
 * Tests verify that the provider correctly parses configuration and initializes
 * MqttClientSingleton with the expected values.
 * 
 * @author Harshit Sharma {@literal <harshitsharma635@gmail.com>}
 */
public class MqttClientProviderTest {
    
    private MqttClientProvider provider;
    private Map<String, Object> config;

    @BeforeEach
    public void setUp() {
        provider = new MqttClientProvider();
        config = new HashMap<>();
        //Reset MqttClientSingleton state between tests
        resetMqttClientSingleton();
    }

    @AfterEach
    public void tearDown() {
        // Clean up singleton state after each test
        resetMqttClientSingleton();
    }

    /**
     * Resets the MqttClientSingleton state using reflection
     */
    private void resetMqttClientSingleton() {
        try {
            Field initializedField = MqttClientSingleton.class.getDeclaredField("initialized");
            initializedField.setAccessible(true);
            initializedField.setBoolean(null, false);
        } catch (Exception e) {
            // Ignore if field doesn't exist or can't be accessed
        }
    }
    /**
     * Injects the config map into the provider using reflection.
     * @throws Exception
     */
    private void injectConfig() throws Exception {
        Field configField = MqttClientProvider.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(provider, config);
    }

    /**
     * Gets a static field value from MqttClientSingleton using reflection
     * @param fieldName
     * @return
     * @throws Exception
     */
    private Object getSingletonField(String fieldName) throws Exception {
        Field field = MqttClientSingleton.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    /**
     * Calls init() without triggering actual MQTT connection by mocking getInstance()
     * @throws Exception
     */
    private void callInitWithoutConnection() throws Exception {
        injectConfig();

        // Mock the singleton instance to prevent actual connection
        MqttClientSingleton mockSingleton = mock(MqttClientSingleton.class);

        // Mock the static getInstance method to return the mocked object
        try (var mockedStatic = mockStatic(MqttClientSingleton.class, Mockito.CALLS_REAL_METHODS)) {
            mockedStatic.when(MqttClientSingleton::getInstance).thenReturn(mockSingleton);

            provider.init();
        }
    }

    @Test
    @DisplayName("Test default configuration values")
    public void testDefaultConfiguration() throws Exception {
        // Empty config should use all defaults
        callInitWithoutConnection();

        // Verify defaults were set in singleton
        assertTrue(MqttClientSingleton.isInitialized());
        assertEquals("tcp://localhost:1883", getSingletonField("brokerUrl"));
        assertEquals(3, getSingletonField("protocolVersion"));
        assertTrue(((String) getSingletonField("clientId")).startsWith("restheart-"));
        assertNull(getSingletonField("username"));
        assertNull(getSingletonField("password"));
        assertEquals(false, getSingletonField("cleanSession"));
        assertEquals(60, getSingletonField("keepAliveSeconds"));
        assertEquals(0xFFFFFFFFL, getSingletonField("sessionExpirySeconds"));
        assertEquals(10, getSingletonField("connectTimeoutSeconds"));
        assertEquals(false, getSingletonField("tlsEnabled"));
        assertEquals(true, getSingletonField("reconnectEnabled"));
        assertEquals(1000L, getSingletonField("initialDelayMs"));
        assertEquals(30000L, getSingletonField("maxDelayMs"));
        assertNull(getSingletonField("willTopic"));
        assertNull(getSingletonField("willPayload"));
        assertEquals(0, getSingletonField("willQos"));
        assertEquals(false, getSingletonField("willRetain"));
        assertEquals(0L, getSingletonField("willDelaySeconds"));
        assertNull(getSingletonField("willMessageExpirySeconds"));
    }

    @Test
    @DisplayName("Test custom broker URL configuration")
    public void testCustomBrokerUrl() throws Exception {
        config.put("broker-url", "ssl://mqtt.example.com:8883");
        callInitWithoutConnection();

        assertEquals("ssl://mqtt.example.com:8883", getSingletonField("brokerUrl"));
    }

    @Test
    @DisplayName("Test MQTT 5 protocol version is parsed correctly")
    public void testMqtt5ProtocolVersion() throws Exception {
        config.put("protocol-version", 5);
        callInitWithoutConnection();

        assertEquals(5, getSingletonField("protocolVersion"));
    }

    @Test
    @DisplayName("Test authentication credentials are parsed correctly")
    public void testAuthenticationConfiguration()throws Exception {
        config.put("username", "testuser");
        config.put("password", "testpass");
        callInitWithoutConnection();

        assertEquals("testuser", getSingletonField("username"));
        assertEquals("testpass", getSingletonField("password"));
    }

    @Test
    @DisplayName("Test session configuration is parsed correctly")
    public void testSessionConfiguration() throws Exception {
        config.put("clean-session", true);
        config.put("keep-alive-seconds", 120);
        config.put("session-expiry-seconds", 3600L);
        callInitWithoutConnection();

        assertEquals(true, getSingletonField("cleanSession"));
        assertEquals(120, getSingletonField("keepAliveSeconds"));
        assertEquals(3600L, getSingletonField("sessionExpirySeconds"));
    }

    @Test
    @DisplayName("Test TLS configuration is parsed correctly")
    public void testTlsConfiguration() throws Exception {
        config.put("tls", true);
        callInitWithoutConnection();

        assertEquals(true, getSingletonField("tlsEnabled"));
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

        assertEquals(false, getSingletonField("reconnectEnabled"));
        assertEquals(2000L, getSingletonField("initialDelayMs"));
        assertEquals(60000L, getSingletonField("maxDelayMs"));
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

        assertEquals("device/status", getSingletonField("willTopic"));
        assertEquals("offline", getSingletonField("willPayload"));
        assertEquals(1, getSingletonField("willQos"));
        assertEquals(true, getSingletonField("willRetain"));
        assertEquals(0L, getSingletonField("willDelaySeconds")); // Default
        assertNull(getSingletonField("willMessageExpirySeconds")); // Default
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

        assertEquals("device/status", getSingletonField("willTopic"));
        assertEquals("offline", getSingletonField("willPayload"));
        assertEquals(2, getSingletonField("willQos"));
        assertEquals(false, getSingletonField("willRetain"));
        assertEquals(30L, getSingletonField("willDelaySeconds"));
        assertEquals(3600L, getSingletonField("willMessageExpirySeconds"));
    }

    @Test
    @DisplayName("Test will message with null topic and payload defaults correctly")
    public void testWillMessageWithNullValues() throws Exception {
        Map<String, Object> will = new HashMap<>();
        will.put("topic", null);
        will.put("payload", null);
        config.put("will", will);
        callInitWithoutConnection();

        assertNull(getSingletonField("willTopic"));
        assertNull(getSingletonField("willPayload"));
        assertEquals(0, getSingletonField("willQos")); // Default
        assertEquals(false, getSingletonField("willRetain")); // Default
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

        assertNull(getSingletonField("willMessageExpirySeconds"));
    }

    @Test
    @DisplayName("Test configuration with only required fields uses defaults for optional fields")
    public void testConfigurationWithMissingOptionalFields() throws Exception {
        // Only set broker URL
        config.put("broker-url", "tcp://localhost:1883");
        callInitWithoutConnection();

        // Verify required field is set
        assertEquals("tcp://localhost:1883", getSingletonField("brokerUrl"));

        // Verify optional fields use defaults
        assertNull(getSingletonField("username"));
        assertNull(getSingletonField("password"));
        assertNull(getSingletonField("willTopic"));
        assertNull(getSingletonField("willPayload"));
        assertEquals(true, getSingletonField("reconnectEnabled")); // Default
    }

    @Test
    @DisplayName("Test custom client ID is parsed correctly")
    public void testCustomClientId() throws Exception {
        config.put("client-id", "my-custom-client");
        callInitWithoutConnection();

        assertEquals("my-custom-client", getSingletonField("clientId"));
    }

    @Test
    @DisplayName("Test connect timeout is parsed correctly")
    public void testConnectTimeout() throws Exception {
        config.put("connect-timeout-seconds", 30);
        callInitWithoutConnection();

        assertEquals(30, getSingletonField("connectTimeoutSeconds"));
    }
}
