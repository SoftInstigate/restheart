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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.mqtt.model.MqttMessage;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import com.google.gson.JsonObject;

/**
 * REST service that exposes the last-known MQTT message per topic.
 * <p>
 * Clients poll via {@code GET /mqtt?topic=sensors/temp} to retrieve the most
 * recent message received on that topic from the router's cache.
 * </p>
 * <p>
 * Returns 200 with a JSON object containing topic, payload, receivedAt, and qos
 * if a cached message exists. Returns 404 if no message has been received for
 * the requested topic, or 400 if the topic parameter is missing.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-rest",
    description = "REST endpoint for polling last MQTT message per topic",
    defaultURI = "/mqtt",
    secure = false
)
public class MqttRestService implements JsonService {

    @Inject("mqtt-router")
    private MqttMessageRouter router;

    @Override
    public void handle(JsonRequest request, JsonResponse response) {
        switch (request.getMethod()) {
            case GET -> handleGet(request, response);
            case OPTIONS -> handleOptions(request);
            default -> response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    private void handleGet(JsonRequest request, JsonResponse response) {
        String topic = extractTopic(request);

        if (topic == null || topic.isEmpty()) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Missing required query parameter: topic");
            response.setContent(error);
            return;
        }

        MqttMessage cached = router.getLastMessage(topic);

        if (cached == null) {
            response.setStatusCode(HttpStatus.SC_NOT_FOUND);
            JsonObject error = new JsonObject();
            error.addProperty("error", "No message cached for topic: " + topic);
            response.setContent(error);
            return;
        }

        JsonObject result = new JsonObject();
        result.addProperty("topic", cached.getTopic());
        result.addProperty("payload", cached.getPayload());
        result.addProperty("receivedAt", cached.getReceivedAt().toString());
        result.addProperty("qos", cached.getQos());
        response.setContent(result);
        response.setStatusCode(HttpStatus.SC_OK);
    }

    private String extractTopic(JsonRequest request) {
        var topicParam = request.getQueryParameters().get("topic");
        if (topicParam != null && !topicParam.isEmpty()) {
            return URLDecoder.decode(topicParam.getFirst(), StandardCharsets.UTF_8);
        }
        return null;
    }
}
