package org.restheart.examples;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.mqtt.MqttMessageRouter;
import org.restheart.mqtt.model.MqttMessage;
import org.restheart.plugins.Inject;
import org.restheart.plugins.JsonService;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.utils.GsonUtils;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * A JSON service that demonstrates MQTT message consumption via the injected
 * {@code mqtt-router} provider.
 *
 * <p>On initialization, subscribes to a configured MQTT topic (default
 * {@code sensors/#}) with QoS 1, collecting received messages in a bounded
 * in-memory buffer (max 100 entries, FIFO with oldest dropped), and logging
 * each to the console at INFO level.
 *
 * <p>A GET request to the service endpoint returns a JSON response containing
 * the subscribed topic and the collected messages so far, each with its topic,
 * payload, QoS level, and receive timestamp. Any other HTTP method returns 405.
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
@RegisterPlugin(
    name = "mqtt-logger",
    description = "logs messages received from an MQTT topic",
    defaultURI = "/mqtt-logger",
    secure = true
)
public class MqttLoggerService implements JsonService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttLoggerService.class);
    private static final int MAX_MESSAGES = 100;

    /**
     * The MQTT router, injected from the {@code mqtt-router} provider.
     */
    @Inject("mqtt-router")
    private MqttMessageRouter router;

    /**
     * Plugin configuration, injected by RESTHeart.
     */
    @Inject("config")
    private Map<String, Object> config;

    private String subscribedTopic;
    private final List<MqttMessage> messageBuffer = Collections.synchronizedList(new ArrayList<>());

    /**
     * Initializes the service: subscribes to the configured MQTT topic and
     * registers a listener that buffers messages and logs them.
     */
    @OnInit
    public void init() {
        subscribedTopic = (String) config.getOrDefault("topic", "sensors/#");

        router.subscribe(subscribedTopic, MqttQos.AT_LEAST_ONCE, msg -> {
            // Add message to buffer, maintaining max size (FIFO drop oldest)
            synchronized (messageBuffer) {
                if (messageBuffer.size() >= MAX_MESSAGES) {
                    messageBuffer.remove(0);
                }
                messageBuffer.add(msg);
            }
            // Log the received message
            LOGGER.info("MQTT message received - topic: {}, qos: {}, payload: {}",
                msg.getTopic(), msg.getQos(), msg.getPayload());
        });

        LOGGER.info("MqttLoggerService initialized, subscribed to: {}", subscribedTopic);
    }

    /**
     * Handles incoming HTTP requests. GET returns the subscribed topic and
     * collected messages in JSON format; all other methods return 405.
     *
     * @param req the HTTP request
     * @param res the HTTP response
     */
    @Override
    public void handle(JsonRequest req, JsonResponse res) {
        switch (req.getMethod()) {
            case GET -> handleGet(res);
            default -> res.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    /**
     * Handles GET requests: returns the subscribed topic and collected messages.
     *
     * @param res the HTTP response
     */
    private void handleGet(JsonResponse res) {
        JsonObject responseBody = new JsonObject();
        responseBody.addProperty("topic", subscribedTopic);

        JsonArray messagesArray = new JsonArray();
        synchronized (messageBuffer) {
            for (MqttMessage msg : messageBuffer) {
                JsonObject msgObj = new JsonObject();
                msgObj.addProperty("topic", msg.getTopic());
                msgObj.addProperty("payload", msg.getPayload());
                msgObj.addProperty("qos", msg.getQos());
                msgObj.addProperty("receivedAt", msg.getReceivedAt().toString());
                messagesArray.add(msgObj);
            }
        }

        responseBody.add("messages", messagesArray);
        res.setContent(responseBody);
    }
}
