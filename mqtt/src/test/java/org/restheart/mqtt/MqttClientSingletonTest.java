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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MqttClientSingleton} lifecycle behaviour that require no running
 * MQTT broker: the initialization guard on {@link MqttClientSingleton#getClient()} and the
 * idempotency/safety of {@link MqttClientSingleton#close()}.
 * <p>
 * {@code MqttClientSingleton} uses the initialization-on-demand holder idiom: the single
 * instance is created at most once per classloader and is shared by every test in the JVM.
 * The private constructor never throws, so construction itself needs no setup; these tests
 * still reset the static fields ({@code initialized}, {@code config}) and the singleton
 * instance's own fields via reflection before and after each test, so that state from one
 * test (or from another test class) cannot leak into another.
 * </p>
 * <p>
 * See {@link MqttClientSingletonPoisoningRegressionTest} for the regression test covering
 * the class-initialization poisoning defect this constructor used to be susceptible to.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttClientSingletonTest {

    private static MqttConfig minimalConfig() {
        return new MqttConfig.Builder()
            .brokerUrl("tcp://localhost:1883")
            .protocolVersion(3)
            .clientId("test-client")
            .cleanSession(true)
            .keepAliveSeconds(60)
            .sessionExpirySeconds(0L)
            .connectTimeoutSeconds(5)
            .tlsEnabled(false)
            .reconnectConfig(new MqttConfig.ReconnectConfig(false, 1000L, 30000L))
            .willConfig(new MqttConfig.WillConfig(null, null, 0, false, 0L, null))
            .build();
    }

    private static void setStatic(String fieldName, Object value) throws Exception {
        Field field = MqttClientSingleton.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void setInstanceField(String fieldName, Object value) throws Exception {
        Field field = MqttClientSingleton.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(MqttClientSingleton.getInstance(), value);
    }

    private static void resetInstanceState() throws Exception {
        setInstanceField("mqttClient", null);
        setInstanceField("connected", false);
        setInstanceField("shuttingDown", false);
        setInstanceField("closed", false);
    }

    @BeforeEach
    public void setUp() throws Exception {
        // trigger holder construction if this is the very first access in the JVM; the
        // constructor never throws, so no static state needs to be set up beforehand
        MqttClientSingleton.getInstance();

        resetInstanceState();

        // default every test to "not initialized" unless it opts in explicitly
        setStatic("initialized", false);
        setStatic("config", null);
    }

    @AfterEach
    public void tearDown() throws Exception {
        resetInstanceState();
        setStatic("initialized", false);
        setStatic("config", null);
    }

    @Test
    @DisplayName("getClient() before initialization throws IllegalStateException naming the mqtt-client plugin")
    public void testGetClientBeforeInitThrows() {
        var ex = assertThrows(IllegalStateException.class,
            () -> MqttClientSingleton.getInstance().getClient());
        assertTrue(ex.getMessage().contains("mqtt-client"));
    }

    @Test
    @DisplayName("getClient() after init() but before a client has been built still throws IllegalStateException")
    public void testGetClientAfterInitBeforeConnectThrows() throws Exception {
        setStatic("initialized", true);
        setStatic("config", minimalConfig());

        var ex = assertThrows(IllegalStateException.class,
            () -> MqttClientSingleton.getInstance().getClient());
        assertTrue(ex.getMessage().contains("mqtt-client"));
    }

    @Test
    @DisplayName("close() is a no-op when the client was never connected")
    public void testCloseWithoutConnectIsSafe() throws Exception {
        setStatic("initialized", true);
        setStatic("config", minimalConfig());

        assertDoesNotThrow(() -> MqttClientSingleton.getInstance().close());
    }

    @Test
    @DisplayName("close() is safe to call twice")
    public void testCloseCalledTwiceIsSafe() throws Exception {
        setStatic("initialized", true);
        setStatic("config", minimalConfig());

        MqttClientSingleton singleton = MqttClientSingleton.getInstance();
        assertDoesNotThrow(singleton::close);
        assertDoesNotThrow(singleton::close);
    }

    @Test
    @DisplayName("isConnected() is false before any connection attempt")
    public void testIsConnectedFalseInitially() throws Exception {
        setStatic("initialized", true);
        setStatic("config", minimalConfig());

        assertTrue(!MqttClientSingleton.getInstance().isConnected());
    }
}
