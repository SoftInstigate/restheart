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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Smoke test proving the {@link MqttITBase} Testcontainers topology works end to end: RESTHeart
 * comes up, the {@code mqtt} module is active in it, and a message published to the broker
 * arrives over {@code /mqtt-sse}.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttTopologyIT extends MqttITBase {

    @Test
    void pingReturns200() throws Exception {
        var req = authedRequest("/ping").build();
        var resp = httpClient().send(req, BodyHandlers.discarding());
        assertEquals(200, resp.statusCode(), "/ping must return 200");
    }

    @Test
    void mqttModuleIsActive() {
        // MqttStatusInitializer.findings() logs exactly this text, at INFO, when every mqtt-*
        // plugin's configuration is consistent with what actually got enabled - which is the
        // case for the rho() overrides MqttITBase applies (mqtt-client, mqtt-sse and the acl are
        // all set explicitly).
        var logs = restheartLogs();
        assertTrue(logs.contains("mqtt module active:"),
            "restheart logs must show the mqtt-status initializer reporting the module active; got:\n" + logs);
    }

    @Test
    void publishedMessageArrivesOverSse() throws Exception {
        var req = authedRequest("/mqtt-sse?topic=sensors/temp").build();

        var linesFuture = CompletableFuture.supplyAsync(() -> {
            try {
                // Three non-blank lines per event: MqttSseService calls Undertow's
                // conn.send(payload, "mqtt-message", eventId, null), which emits an "id:",
                // an "event:" and a "data:" line. Asking for fewer returns before the payload.
                return readSseLines(req, 3, 15);
            } catch (Exception e) {
                return List.<String>of();
            }
        });

        // Small delay to let the SSE connection and its MQTT subscription settle, as
        // ChangeStreamSseIT does for the analogous MongoDB change-stream worker.
        Thread.sleep(1_500);

        publish("sensors/temp", "{\"value\": 21.5}");

        var lines = linesFuture.get(20, TimeUnit.SECONDS);

        assertTrue(lines.stream().anyMatch(l -> l.startsWith("event:mqtt-message")),
            "SSE event must be typed 'mqtt-message'; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("data:")),
            "SSE connection must receive a 'data:' line after publish; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("21.5")),
            "Received event must carry the published payload; got: " + lines);
    }
}
