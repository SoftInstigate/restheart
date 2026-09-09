package org.restheart.mqtt;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.HttpStatus;

import com.google.gson.JsonObject;

/**
 * REST service that exposes a JSON snapshot of the MQTT router's runtime statistics.
 * <p>
 * Clients poll via {@code GET /mqtt/stats} to retrieve the number of topic filters currently
 * subscribed on the broker, the total number of registered listeners, the number of topics
 * present in the last-message cache, and the cumulative counts of messages received and dropped,
 * as reported by {@link MqttMessageRouter#getStats()}.
 * </p>
 * <p>
 * <strong>Tier 2 of 2 - opt-in surface.</strong> Registered with {@code enabledByDefault = false}
 * so that arming the module (enabling {@code mqtt-client}, Tier 1) does not, by itself, expose
 * this HTTP endpoint: an operator who only wants the injectable {@code mqtt-router} - as
 * {@code examples/mqtt-logger} does - gets it without {@code /mqtt/stats} appearing. Exposing
 * this service is therefore a second, separate opt-in, made once the module is armed. Note that
 * {@link MqttTopicAuthorizer} stays enabled regardless of this switch, so once this service is
 * enabled it is already gated by ACL - there is no fail-open window in between.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-stats",
    description = "JSON snapshot of mqtt router runtime statistics",
    defaultURI = "/mqtt/stats",
    secure = true,
    enabledByDefault = false
)
public class MqttStatsService implements JsonService {

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
        var stats = router.getStats();

        JsonObject result = new JsonObject();
        result.addProperty("topicFilters", stats.getTopicFilters());
        result.addProperty("totalListeners", stats.getTotalListeners());
        result.addProperty("cachedMessages", stats.getCachedMessages());
        result.addProperty("messagesReceived", stats.getMessagesReceived());
        result.addProperty("messagesDropped", stats.getMessagesDropped());
        response.setContent(result);
        response.setStatusCode(HttpStatus.SC_OK);
    }
}
