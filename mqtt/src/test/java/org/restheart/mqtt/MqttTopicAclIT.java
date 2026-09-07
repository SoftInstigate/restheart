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

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/**
 * Proves {@link MqttTopicAuthorizer} actually gates {@code /mqtt-sse}: no credentials at all is
 * refused, a filter under the {@code sensors/#} prefix {@link MqttITBase#rho()} grants the
 * {@code admin} role streams normally, and a filter outside that grant is refused with 403 and -
 * the point of this class - never reaches {@code MqttMessageRouter#subscribe}, so a denied
 * request can never observe traffic on a topic it was not authorized for.
 * <p>
 * Every test uses a topic literal no other test in this class (or {@link MqttSseStreamIT})
 * publishes to, for the same replay reason documented there: {@code /mqtt-sse} replays a topic's
 * last cached message to a newly connecting client, so a shared topic would let one test read
 * another's stale payload as its only event.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttTopicAclIT extends MqttITBase {

    @Test
    void noCredentialsIsUnauthorized() throws Exception {
        // Built directly with HttpRequest.newBuilder(), not authedRequest(), which always sets
        // the Authorization header - this request must not carry one.
        var req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl() + "/mqtt-sse?topic=sensors/acl-anon"))
            .GET()
            .build();

        var resp = httpClient().send(req, BodyHandlers.discarding());

        assertEquals(401, resp.statusCode(), "a request with no Authorization header must be denied with 401");
    }

    @Test
    void grantedFilterStreams() throws Exception {
        var topic = "sensors/acl-granted";
        var req = authedRequest("/mqtt-sse?topic=" + topic).build();

        var linesFuture = subscribeAsync(() -> readSseLines(req, 3, 15));

        Thread.sleep(1_500);

        publish(topic, "{\"acl\": \"granted\"}");

        var lines = linesFuture.get(20, TimeUnit.SECONDS);

        assertTrue(lines.stream().anyMatch(l -> l.startsWith("event:mqtt-message")),
            "a filter under the granted 'sensors/#' prefix must stream normally; got: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("granted")),
            "the streamed event must carry the published payload; got: " + lines);
    }

    @Test
    void ungrantedFilterIsRefusedAndNeverSubscribes() throws Exception {
        // "alarms/#" is outside the only prefix ("sensors/#") MqttITBase.rho() grants the
        // admin role; '#' must be percent-encoded or the request line is malformed.
        var deniedFilter = "alarms/#";
        var req = authedRequest("/mqtt-sse?topic=" + deniedFilter.replace("#", "%23")).build();

        var resp = httpClient().send(req, BodyHandlers.ofString());

        assertEquals(403, resp.statusCode(), "a filter outside the granted ACL must be refused with 403");

        // SseHandshakeResponse.setInError follows the same JSON error shape as JsonResponse:
        // {"msg": "...", "exception": "..."}. MqttTopicAuthorizer.handle calls setInError with
        // "Not authorized for topic: " + topicFilter as the message, so that text is the "msg"
        // property's value, not the raw response body.
        assertTrue(resp.body().contains("\"msg\""),
            "the 403 body must carry the authorizer's message under the 'msg' JSON property; got: " + resp.body());
        assertTrue(resp.body().contains("Not authorized for topic: " + deniedFilter),
            "the 403 body must name the denied topic filter; got: " + resp.body());

        // The whole point of this test: a 403 that still let the broker subscription through
        // would be exactly the fail-open MqttTopicAuthorizer exists to prevent. The interceptor
        // runs at REQUEST_AFTER_AUTH, strictly before MqttSseService.onConnect ever calls
        // MqttMessageRouter#subscribe, so no "Subscribed to topic filter: alarms/# ..." line
        // must ever appear in the server log for the denied filter.
        var logs = restheartLogs();
        assertFalse(logs.contains("Subscribed to topic filter: " + deniedFilter),
            "a 403-denied filter must never reach MqttMessageRouter#subscribe - a denial that "
                + "still subscribed would be the fail-open this authorizer exists to prevent; logs:\n"
                + logs);
    }

}
