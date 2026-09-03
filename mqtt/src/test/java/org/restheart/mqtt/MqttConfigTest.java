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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link MqttConfig.Builder} validation and for the client-id and
 * broker-url-scheme resolution behaviour implemented by {@link MqttClientSingleton}.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttConfigTest {

    private static MqttConfig.Builder validBuilder() {
        return new MqttConfig.Builder()
            .brokerUrl("tcp://localhost:1883")
            .protocolVersion(3)
            .clientId("test-client")
            .cleanSession(false)
            .keepAliveSeconds(60)
            .sessionExpirySeconds(0xFFFFFFFFL)
            .connectTimeoutSeconds(10)
            .tlsEnabled(false);
    }

    @Test
    @DisplayName("build() accepts a minimal valid configuration")
    public void testValidConfigurationBuilds() {
        MqttConfig config = validBuilder().build();
        assertEquals("tcp://localhost:1883", config.getBrokerUrl());
    }

    @ParameterizedTest(name = "protocol-version {0} is accepted")
    @ValueSource(ints = {3, 5})
    @DisplayName("build() accepts protocol-version 3 and 5")
    public void testAcceptedProtocolVersions(int version) {
        MqttConfig config = validBuilder().protocolVersion(version).build();
        assertEquals(version, config.getProtocolVersion());
    }

    @ParameterizedTest(name = "protocol-version {0} is rejected")
    @ValueSource(ints = {0, 1, 2, 4, 6, -1})
    @DisplayName("build() rejects any protocol-version other than 3 or 5, naming the key")
    public void testRejectedProtocolVersions(int version) {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> validBuilder().protocolVersion(version).build());
        assertTrue(ex.getMessage().contains("protocol-version"));
    }

    @Test
    @DisplayName("build() rejects a null broker-url, naming the key")
    public void testNullBrokerUrlRejected() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> validBuilder().brokerUrl(null).build());
        assertTrue(ex.getMessage().contains("broker-url"));
    }

    @Test
    @DisplayName("build() rejects a blank broker-url, naming the key")
    public void testBlankBrokerUrlRejected() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> validBuilder().brokerUrl("   ").build());
        assertTrue(ex.getMessage().contains("broker-url"));
    }

    @Test
    @DisplayName("build() rejects a broker-url without a host, naming the key")
    public void testBrokerUrlWithoutHostRejected() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> validBuilder().brokerUrl("tcp://").build());
        assertTrue(ex.getMessage().contains("broker-url"));
    }

    @Test
    @DisplayName("build() rejects a broker-url with an unsupported scheme, listing supported schemes")
    public void testBrokerUrlUnsupportedSchemeRejected() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> validBuilder().brokerUrl("http://localhost:1883").build());
        assertTrue(ex.getMessage().contains("broker-url"));
        assertTrue(ex.getMessage().contains("tcp"));
        assertTrue(ex.getMessage().contains("wss"));
    }

    @Test
    @DisplayName("build() rejects a negative keep-alive-seconds, naming the key")
    public void testNegativeKeepAliveRejected() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> validBuilder().keepAliveSeconds(-1).build());
        assertTrue(ex.getMessage().contains("keep-alive-seconds"));
    }

    @Test
    @DisplayName("build() accepts a zero keep-alive-seconds")
    public void testZeroKeepAliveAccepted() {
        MqttConfig config = validBuilder().keepAliveSeconds(0).build();
        assertEquals(0, config.getKeepAliveSeconds());
    }

    @Test
    @DisplayName("build() rejects a zero connect-timeout-seconds, naming the key")
    public void testZeroConnectTimeoutRejected() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> validBuilder().connectTimeoutSeconds(0).build());
        assertTrue(ex.getMessage().contains("connect-timeout-seconds"));
    }

    @Test
    @DisplayName("build() rejects a negative connect-timeout-seconds, naming the key")
    public void testNegativeConnectTimeoutRejected() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> validBuilder().connectTimeoutSeconds(-5).build());
        assertTrue(ex.getMessage().contains("connect-timeout-seconds"));
    }

    @ParameterizedTest(name = "will qos {0} is rejected")
    @ValueSource(ints = {-1, 3, 10})
    @DisplayName("build() rejects an out-of-range will qos, naming the key")
    public void testInvalidWillQosRejected(int qos) {
        var willConfig = new MqttConfig.WillConfig("topic", "payload", qos, false, 0L, null);
        var ex = assertThrows(IllegalArgumentException.class,
            () -> validBuilder().willConfig(willConfig).build());
        assertTrue(ex.getMessage().contains("will/qos"));
    }

    @ParameterizedTest(name = "will qos {0} is accepted")
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("build() accepts will qos in 0..2")
    public void testValidWillQosAccepted(int qos) {
        var willConfig = new MqttConfig.WillConfig("topic", "payload", qos, false, 0L, null);
        MqttConfig config = validBuilder().willConfig(willConfig).build();
        assertEquals(qos, config.getWillConfig().getWillQos());
    }

    @Test
    @DisplayName("client-id null falls back to a restheart- prefixed generated id")
    public void testNullClientIdGenerated() {
        MqttConfig config = validBuilder().clientId(null).build();
        assertTrue(config.getClientId().startsWith("restheart-"));
    }

    @Test
    @DisplayName("client-id blank falls back to a restheart- prefixed generated id")
    public void testBlankClientIdGenerated() {
        MqttConfig config = validBuilder().clientId("  ").build();
        assertTrue(config.getClientId().startsWith("restheart-"));
    }

    @Test
    @DisplayName("explicit client-id is used verbatim")
    public void testExplicitClientIdUsedVerbatim() {
        MqttConfig config = validBuilder().clientId("my-explicit-id").build();
        assertEquals("my-explicit-id", config.getClientId());
    }

    @Test
    @DisplayName("two null client-ids generate two different ids")
    public void testGeneratedClientIdsAreUnique() {
        MqttConfig a = validBuilder().clientId(null).build();
        MqttConfig b = validBuilder().clientId(null).build();
        assertFalse(a.getClientId().equals(b.getClientId()));
    }

    // --- broker-url scheme -> transport/port resolution (MqttClientSingleton.resolveEndpoint) ---

    @Test
    @DisplayName("tcp:// resolves to plain TCP on the default port 1883")
    public void testTcpSchemeDefaultsPort1883() {
        var endpoint = MqttClientSingleton.resolveEndpoint("tcp://broker.example.com", false);
        assertEquals("broker.example.com", endpoint.host());
        assertEquals(1883, endpoint.port());
        assertFalse(endpoint.tls());
        assertFalse(endpoint.webSocket());
    }

    @Test
    @DisplayName("tcp:// with an explicit port keeps that port")
    public void testTcpSchemeExplicitPort() {
        var endpoint = MqttClientSingleton.resolveEndpoint("tcp://broker.example.com:1234", false);
        assertEquals(1234, endpoint.port());
    }

    @Test
    @DisplayName("ssl:// resolves to TLS on the default port 8883")
    public void testSslSchemeDefaultsPort8883() {
        var endpoint = MqttClientSingleton.resolveEndpoint("ssl://broker.example.com", false);
        assertEquals(8883, endpoint.port());
        assertTrue(endpoint.tls());
        assertFalse(endpoint.webSocket());
    }

    @Test
    @DisplayName("mqtts:// resolves to TLS on the default port 8883")
    public void testMqttsSchemeDefaultsPort8883() {
        var endpoint = MqttClientSingleton.resolveEndpoint("mqtts://broker.example.com", false);
        assertEquals(8883, endpoint.port());
        assertTrue(endpoint.tls());
        assertFalse(endpoint.webSocket());
    }

    @Test
    @DisplayName("ws:// resolves to WebSocket on the default port 80")
    public void testWsSchemeDefaultsPort80() {
        var endpoint = MqttClientSingleton.resolveEndpoint("ws://broker.example.com", false);
        assertEquals(80, endpoint.port());
        assertFalse(endpoint.tls());
        assertTrue(endpoint.webSocket());
    }

    @Test
    @DisplayName("wss:// resolves to WebSocket over TLS on the default port 443")
    public void testWssSchemeDefaultsPort443() {
        var endpoint = MqttClientSingleton.resolveEndpoint("wss://broker.example.com", false);
        assertEquals(443, endpoint.port());
        assertTrue(endpoint.tls());
        assertTrue(endpoint.webSocket());
    }

    @Test
    @DisplayName("tls override forces TLS and the TLS default port for the tcp:// scheme")
    public void testTlsOverrideForcesTlsOnTcpScheme() {
        var endpoint = MqttClientSingleton.resolveEndpoint("tcp://broker.example.com", true);
        assertTrue(endpoint.tls());
        assertFalse(endpoint.webSocket());
        // flipping 'tls: true' on the default tcp:// URL must not keep the plaintext port
        assertEquals(8883, endpoint.port());
    }

    @Test
    @DisplayName("tls override forces the wss default port for the ws:// scheme")
    public void testTlsOverrideForcesTlsOnWsScheme() {
        var endpoint = MqttClientSingleton.resolveEndpoint("ws://broker.example.com", true);
        assertTrue(endpoint.tls());
        assertTrue(endpoint.webSocket());
        assertEquals(443, endpoint.port());
    }

    @Test
    @DisplayName("an explicit port always wins over the tls override default")
    public void testExplicitPortWinsOverTlsOverride() {
        var endpoint = MqttClientSingleton.resolveEndpoint("tcp://broker.example.com:1234", true);
        assertTrue(endpoint.tls());
        assertEquals(1234, endpoint.port());
    }

    @Test
    @DisplayName("an unrecognised scheme fails fast, listing the supported schemes")
    public void testUnknownSchemeFailsFast() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> MqttClientSingleton.resolveEndpoint("http://broker.example.com", false));
        assertTrue(ex.getMessage().contains("tcp"));
        assertTrue(ex.getMessage().contains("wss"));
    }

    @Test
    @DisplayName("a broker-url without a host fails fast")
    public void testMissingHostFailsFast() {
        assertThrows(IllegalArgumentException.class,
            () -> MqttClientSingleton.resolveEndpoint("tcp://", false));
    }
}
