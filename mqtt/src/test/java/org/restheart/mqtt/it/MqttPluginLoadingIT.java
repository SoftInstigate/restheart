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
package org.restheart.mqtt.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Proves the {@code mqtt} module is installed, discovered by RESTHeart's plugin scanner, and
 * comes up with exactly the plugins {@code it-overrides.yml}'s {@code mqtt-*} block enables - and
 * that it stays entirely dormant when nothing switches it on.
 * <p>
 * {@code mqttModuleIsActiveWithExpectedPlugins} and {@code moduleStaysDormantWhenNotSwitchedOn}
 * are back after being dropped from the version of this class that ran inside {@code core}'s IT
 * suite: there, a single RESTHeart instance was shared across the whole suite, so its log rolled
 * over the course of a full {@code mvn verify} (forcing a "last match" search on the summary
 * line) and there was nowhere to stand a second, differently configured instance for the dormant
 * case at all. A per-class harness removes both obstacles - the log here is freshly created for
 * this class alone, and starting a second instance is just another call to
 * {@link MqttITBase#startRestheart(Path, List, String)}.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttPluginLoadingIT extends MqttITBase {

    @Test
    void mqttModuleIsActiveWithExpectedPlugins() throws Exception {
        var logs = restheartLogs();
        var summaryLine = summaryLine(logs);
        assertNotNull(summaryLine,
            "restheart logs must show the mqtt-status initializer's 'mqtt module active:' summary; got:\n" + logs);

        for (var expectedActive : List.of("mqtt-client", "mqtt-router", "mqtt-sse", "mqtt-rest",
                "mqtt-topic-authorizer")) {
            assertTrue(activePart(summaryLine).contains(expectedActive),
                expectedActive + " must be named as active; got: " + summaryLine);
        }
        assertTrue(inactivePart(summaryLine).contains("mqtt-mongo-writer"),
            "mqtt-mongo-writer must be named as inactive; got: " + summaryLine);
    }

    @Test
    void mqttSseIsBound() throws Exception {
        var req = authedRequest("/mqtt-sse?topic=sensors/probe").build();
        var resp = httpClient().send(req, BodyHandlers.ofInputStream());
        try {
            // Not merely "not a 404": RESTHeart's RequestNotManagedHandler answers every unbound
            // path with 502, so 200 here is the one status that actually distinguishes "bound"
            // from "unbound".
            assertEquals(200, resp.statusCode(), "/mqtt-sse must be bound when mqtt-sse is enabled");
        } finally {
            resp.body().close();
        }
    }

    @Test
    void mqttRestIsBound() throws Exception {
        // No 'topic' query parameter: MqttRestService's own documented answer for that case is
        // 400, which - like mqttSseIsBound's 200 above - distinguishes "bound" from the 502 an
        // unbound path would get. This is the mqtt-rest half of the same claim.
        var req = authedRequest("/mqtt").build();
        var resp = httpClient().send(req, BodyHandlers.ofString());
        assertEquals(400, resp.statusCode(),
            "/mqtt with no 'topic' query parameter must be bound and answer 400; got body: " + resp.body());
    }

    @Test
    void moduleStaysDormantWhenNotSwitchedOn() throws Exception {
        // it-overrides-dormant.yml carries no mqtt-* configuration at all: the module is
        // installed (same plugins directory as the primary instance) but never opted in to. No
        // broker-url is passed through RHO either - a dormant instance must have no mqtt-*
        // configuration whatsoever, not even a broker it never connects to.
        var dormantOverrides = Path.of("src", "test", "resources", "etc", "it-overrides-dormant.yml")
            .toAbsolutePath().normalize();

        // try/finally, not try-with-resources with a var declared in the try clause: the
        // assertions below need the instance in scope, and RestheartInstance.close() is itself
        // safe to call unconditionally.
        var dormant = startRestheart(dormantOverrides, List.of(), "MqttPluginLoadingIT-dormant.log");
        try {
            var req = HttpRequest.newBuilder()
                .uri(URI.create(dormant.baseUrl() + "/mqtt-sse"))
                .header("Authorization", ADMIN_BASIC)
                .GET()
                .build();
            var resp = httpClient().send(req, BodyHandlers.discarding());
            // Not 404: RESTHeart's own RequestNotManagedHandler answers every request that
            // matches no registered path with 502 Bad Gateway, not 404 - there is no "not found"
            // routing outcome in this server for a path nothing ever claimed.
            assertEquals(502, resp.statusCode(),
                "/mqtt-sse must be unbound - answered by RequestNotManagedHandler's 502 - when "
                    + "mqtt-client is never switched on");

            var logs = dormant.logs();
            assertTrue(logs.contains("mqtt module is installed but inactive"),
                "restheart logs must show the mqtt-status initializer's 'installed but inactive' finding; got:\n"
                    + logs);
        } finally {
            dormant.close();
        }
    }

    /**
     * Extracts the {@code mqtt-status} initializer's single-line summary ("mqtt module active: ...")
     * from the captured server log.
     * <p>
     * Takes the FIRST match: unlike the shared, suite-long {@code core} instance this class used
     * to run against, this instance's log is per-class and freshly created for every run, so
     * there is no roll/append hazard here that would require picking the LAST match instead.
     * </p>
     *
     * @param logs the captured RESTHeart log
     * @return the summary line, without the log line's own timestamp/level prefix, or {@code null}
     *         if it never appeared
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
