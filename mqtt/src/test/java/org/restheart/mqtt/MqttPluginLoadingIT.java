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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Proves the {@code mqtt} module is installed and discovered by RESTHeart as an external plugin,
 * that it comes up with exactly the plugins {@link MqttITBase#rho()} enables, and that it stays
 * entirely dormant - {@code /mqtt-sse} unbound - when nothing switches it on.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttPluginLoadingIT extends MqttITBase {

    @Test
    void pingReturns200() throws Exception {
        var req = authedRequest("/ping").build();
        var resp = httpClient().send(req, BodyHandlers.discarding());
        assertEquals(200, resp.statusCode(), "/ping must return 200");
    }

    @Test
    void mqttModuleIsActiveWithExpectedPlugins() throws Exception {
        var logs = restheartLogs();
        var summaryLine = summaryLine(logs);
        assertNotNull(summaryLine,
            "restheart logs must show the mqtt-status initializer's 'mqtt module active:' summary; got:\n" + logs);

        // MqttClientProvider cannot instantiate at all without the HiveMQ client classes, which
        // ship only in mqtt/target/lib (copied by MqttITBase.newRestheartContainer to
        // /opt/restheart/plugins/custom/lib, alongside the plugin jar). mqtt-client - and
        // everything downstream of it - being reported active below is therefore itself the
        // proof that lib/ really is on the classpath the container ran with; there is no
        // separate, more direct way to observe that from outside the container.
        for (var expectedActive : List.of("mqtt-client", "mqtt-router", "mqtt-sse", "mqtt-topic-authorizer")) {
            assertTrue(activePart(summaryLine).contains(expectedActive),
                expectedActive + " must be named as active; got: " + summaryLine);
        }
        for (var expectedInactive : List.of("mqtt-rest", "mqtt-mongo-writer")) {
            assertTrue(inactivePart(summaryLine).contains(expectedInactive),
                expectedInactive + " must be named as inactive; got: " + summaryLine);
        }
    }

    @Test
    void mqttSseIsBound() throws Exception {
        var req = authedRequest("/mqtt-sse?topic=sensors/probe").build();
        var resp = httpClient().send(req, BodyHandlers.ofInputStream());
        try {
            // Not merely "not a 404": RESTHeart's RequestNotManagedHandler answers every
            // unbound path with 502 (see moduleStaysDormantWhenNotSwitchedOn below), so 200 here
            // is the one status that actually distinguishes "bound" from "unbound".
            assertEquals(200, resp.statusCode(), "/mqtt-sse must be bound when mqtt-sse is enabled");
        } finally {
            resp.body().close();
        }
    }

    @Test
    void moduleStaysDormantWhenNotSwitchedOn() throws Exception {
        // Only what installing the jar unconditionally needs (a reachable listener and an admin
        // password); no mqtt-* block at all, so the module is installed but never opted in to.
        var dormantRho = List.of(
            "/http-listener/host->\"0.0.0.0\"",
            "/fileRealmAuthenticator/users[userid='admin']/password->\"secret\"");

        // try-with-resources: GenericContainer.close() calls stop(), so this second container
        // cannot outlive the test even if an assertion below throws.
        try (var dormant = newRestheartContainer(dormantRho)) {
            dormant.start();

            var dormantBaseUrl = "http://" + dormant.getHost() + ":" + dormant.getMappedPort(8080);
            var req = HttpRequest.newBuilder()
                .uri(URI.create(dormantBaseUrl + "/mqtt-sse"))
                .header("Authorization", ADMIN_BASIC)
                .GET()
                .build();
            var resp = httpClient().send(req, BodyHandlers.discarding());
            // Not 404: RESTHeart's own RequestNotManagedHandler (core) answers every request that
            // matches no registered path with 502 Bad Gateway, not 404 - there is no "not found"
            // routing outcome in this server for a path nothing ever claimed.
            assertEquals(502, resp.statusCode(),
                "/mqtt-sse must be unbound - answered by RequestNotManagedHandler's 502 - when "
                    + "mqtt-client is never switched on");

            var logs = dormant.getLogs();
            assertTrue(logs.contains("mqtt module is installed but inactive"),
                "restheart logs must show the mqtt-status initializer's 'installed but inactive' finding; got:\n"
                    + logs);
        }
    }

    /**
     * Extracts the {@code mqtt-status} initializer's single-line summary ("mqtt module active: ...")
     * from the captured container log, or {@code null} if it never appeared.
     *
     * @param logs the captured RESTHeart container log
     * @return the summary line, without the log line's own timestamp/level prefix, or {@code null}
     */
    private static String summaryLine(String logs) {
        var matcher = Pattern.compile("mqtt module active:[^\\r\\n]*").matcher(logs);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * @param summaryLine the line returned by {@link #summaryLine(String)}
     * @return the comma-separated list of active plugin names the summary line carries
     */
    private static String activePart(String summaryLine) {
        var afterPrefix = summaryLine.substring("mqtt module active:".length());
        var semicolon = afterPrefix.indexOf(';');
        return semicolon >= 0 ? afterPrefix.substring(0, semicolon) : afterPrefix;
    }

    /**
     * @param summaryLine the line returned by {@link #summaryLine(String)}
     * @return the comma-separated list of inactive plugin names the summary line carries, or an
     *         empty string if the summary line names no inactive plugin at all
     */
    private static String inactivePart(String summaryLine) {
        var idx = summaryLine.indexOf("inactive:");
        return idx >= 0 ? summaryLine.substring(idx + "inactive:".length()) : "";
    }
}
