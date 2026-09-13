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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse.BodyHandlers;

import org.junit.jupiter.api.Test;

/**
 * Proves {@code /mqtt-rest} (plugin name {@code mqtt-rest}, mounted at {@code /mqtt}) answers per
 * {@code MqttRestService}'s own Javadoc: 400 with no {@code topic} query parameter, 404 for a
 * topic nothing has ever published to, and 200 with the cached message once one has.
 * <p>
 * No {@code rho()}/RHO override is needed for any of this: every configuration this class relies
 * on is static and lives entirely in {@code src/test/resources/etc/it-overrides.yml}. In
 * particular, read that file's own comments for why {@code mqtt-rest} answering anything at all
 * needs a startup subscription on {@code sensors/#} - {@code mqtt-rest} is
 * {@code enabledByDefault = false} and its cache is filled only by the router's publish consumer;
 * the broker delivers nothing that nothing has subscribed to, and without that subscription this
 * endpoint would answer 404 forever regardless of what was published. {@code last-message-cache}
 * already defaults to {@code true}, so nothing further is needed to enable caching itself.
 * </p>
 * <p>
 * Every test that publishes uses a topic literal of its own under {@code sensors/}, so one test's
 * cached message can never be mistaken for another's.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttRestLastValueIT extends MqttITBase {

    @Test
    void missingTopicParameterIsBadRequest() throws Exception {
        var req = authedRequest("/mqtt").build();
        var resp = httpClient().send(req, BodyHandlers.ofString());

        assertEquals(400, resp.statusCode(), "GET /mqtt with no 'topic' query parameter must be a 400");
    }

    @Test
    void topicNeverPublishedToIsNotFound() throws Exception {
        var req = authedRequest("/mqtt?topic=sensors/never-published").build();
        var resp = httpClient().send(req, BodyHandlers.ofString());

        assertEquals(404, resp.statusCode(),
            "GET /mqtt for a topic nothing has ever published to must be a 404; got body: " + resp.body());
    }

    @Test
    void lastPublishedMessageIsReturned() throws Exception {
        var topic = "sensors/rest-last-value";

        publish(topic, "{\"value\": 42}");

        // Brief settle so the message travels broker -> mqtt-client -> MqttMessageRouter's
        // publish consumer -> last-message cache before the GET below reads it.
        Thread.sleep(1_500);

        var req = authedRequest("/mqtt?topic=" + topic).build();
        var resp = httpClient().send(req, BodyHandlers.ofString());

        assertEquals(200, resp.statusCode(),
            "GET /mqtt for a topic that was published to must be a 200; got body: " + resp.body());
        assertTrue(resp.body().contains("\"topic\":\"" + topic + "\""),
            "the response body must carry the 'topic' property MqttRestService adds; got: " + resp.body());
        assertTrue(resp.body().contains("42"),
            "the response body must carry the published payload; got: " + resp.body());
    }
}
