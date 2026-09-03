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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.exchange.PipelineInfo;
import org.restheart.exchange.PipelineInfo.PIPELINE_TYPE;
import org.restheart.exchange.Request;
import org.restheart.mqtt.model.MqttMessage;
import org.restheart.utils.HttpStatus;

import com.hivemq.client.mqtt.MqttClient;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Unit tests for MqttRestService.
 * <p>
 * The first group of tests exercises {@link MqttMessageRouter}'s cache directly
 * (kept for backward compatibility, though this coverage now overlaps with
 * {@code MqttMessageRouterTest#testGetLastMessageKeepsExactTopicSemantics}).
 * The second group invokes {@link MqttRestService#handle} itself against a real
 * {@link JsonRequest}/{@link JsonResponse} pair, asserting the status code and
 * response body the service actually produces.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttRestServiceTest {

    /** A request/response pair backed by the same fake exchange. */
    private record Exchange(JsonRequest request, JsonResponse response) {
    }

    private MqttMessageRouter router;

    @BeforeEach
    void setUp() {
        router = new MqttMessageRouter(mock(MqttClient.class), 5000, true, 1000);
    }

    private void cacheMessage(String topic, String payload, int qos) {
        cacheMessage(topic, payload, qos, Instant.now());
    }

    private void cacheMessage(String topic, String payload, int qos, Instant receivedAt) {
        MqttMessage msg = new MqttMessage(topic, payload, qos, receivedAt);
        // Use the router's internal cache via reflection
        try {
            Field cacheField = MqttMessageRouter.class.getDeclaredField("lastMessageCache");
            cacheField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, MqttMessage> cache = (Map<String, MqttMessage>) cacheField.get(router);
            cache.put(topic, msg);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds an {@code MqttRestService} with the given router injected via reflection
     * into its {@code @Inject}-annotated field, mimicking what the DI container does
     * at runtime.
     */
    private MqttRestService serviceWithRouter(MqttMessageRouter router) {
        var service = new MqttRestService();
        try {
            Field routerField = MqttRestService.class.getDeclaredField("router");
            routerField.setAccessible(true);
            routerField.set(service, router);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return service;
    }

    /**
     * Builds a fresh exchange/request/response triple for the given method and,
     * optionally, a raw (possibly URL-encoded) {@code topic} query parameter value.
     * The pipeline info is set to SERVICE so that the default
     * {@code Service#handleOptions} method (which looks it up) does not NPE.
     */
    private Exchange exchangeFor(METHOD method, String rawTopicQueryValue) {
        var exchange = new HttpServerExchange();
        exchange.setRequestMethod(HttpString.tryFromString(method.name()));
        if (rawTopicQueryValue != null) {
            exchange.addQueryParam("topic", rawTopicQueryValue);
        }
        Request.setPipelineInfo(exchange, new PipelineInfo(PIPELINE_TYPE.SERVICE, "/mqtt", "mqtt-rest"));

        var request = JsonRequest.init(exchange);
        var response = JsonResponse.init(exchange);
        return new Exchange(request, response);
    }

    @Test
    @DisplayName("GET with existing topic returns 200 with JSON payload")
    void testGetExistingTopic() throws Exception {
        cacheMessage("sensors/temp", "{\"temp\":25}", 1);

        MqttMessage result = router.getLastMessage("sensors/temp");
        assertNotNull(result, "Cached message should be retrievable");
        assertEquals("sensors/temp", result.getTopic());
        assertEquals("{\"temp\":25}", result.getPayload());
        assertEquals(1, result.getQos());
    }

    @Test
    @DisplayName("GET with non-existing topic returns null")
    void testGetNonExistingTopic() {
        MqttMessage result = router.getLastMessage("sensors/nonexistent");
        assertNull(result, "Non-existing topic should return null");
    }

    @Test
    @DisplayName("Cached message has receivedAt timestamp")
    void testCachedMessageHasTimestamp() {
        cacheMessage("sensors/temp", "{\"temp\":25}", 1);

        MqttMessage result = router.getLastMessage("sensors/temp");
        assertNotNull(result);
        assertNotNull(result.getReceivedAt());
    }

    @Test
    @DisplayName("Multiple topics cached independently")
    void testMultipleTopicsCached() {
        cacheMessage("sensors/temp", "{\"temp\":25}", 1);
        cacheMessage("sensors/humidity", "{\"humidity\":60}", 0);

        MqttMessage temp = router.getLastMessage("sensors/temp");
        MqttMessage humidity = router.getLastMessage("sensors/humidity");

        assertNotNull(temp);
        assertNotNull(humidity);
        assertEquals("{\"temp\":25}", temp.getPayload());
        assertEquals("{\"humidity\":60}", humidity.getPayload());
    }

    @Test
    @DisplayName("Cache overwrites previous message for same topic")
    void testCacheOverwrites() {
        cacheMessage("sensors/temp", "{\"temp\":20}", 1);
        cacheMessage("sensors/temp", "{\"temp\":25}", 1);

        MqttMessage result = router.getLastMessage("sensors/temp");
        assertNotNull(result);
        assertEquals("{\"temp\":25}", result.getPayload());
    }

    // --- handle(...) behaviour --------------------------------------------

    @Test
    @DisplayName("handle(): GET on a cached topic returns 200 with topic, payload, receivedAt and qos")
    void testHandleGetCachedTopicReturns200WithFullBody() {
        var receivedAt = Instant.parse("2026-01-01T00:00:00Z");
        cacheMessage("sensors/temp", "{\"temp\":25}", 1, receivedAt);

        var service = serviceWithRouter(router);
        var ex = exchangeFor(METHOD.GET, "sensors/temp");

        service.handle(ex.request(), ex.response());

        assertEquals(HttpStatus.SC_OK, ex.response().getStatusCode());
        var body = ex.response().getContent();
        assertNotNull(body);
        assertTrue(body.isJsonObject());
        var obj = body.getAsJsonObject();
        assertEquals("sensors/temp", obj.get("topic").getAsString());
        assertEquals("{\"temp\":25}", obj.get("payload").getAsString());
        assertEquals(receivedAt.toString(), obj.get("receivedAt").getAsString());
        assertEquals(1, obj.get("qos").getAsInt());
    }

    @Test
    @DisplayName("handle(): GET on a topic with nothing cached returns 404 naming the topic")
    void testHandleGetUncachedTopicReturns404WithErrorNamingTopic() {
        var service = serviceWithRouter(router);
        var ex = exchangeFor(METHOD.GET, "sensors/missing");

        service.handle(ex.request(), ex.response());

        assertEquals(HttpStatus.SC_NOT_FOUND, ex.response().getStatusCode());
        var body = ex.response().getContent();
        assertNotNull(body);
        var obj = body.getAsJsonObject();
        assertNotNull(obj.get("error"));
        assertTrue(obj.get("error").getAsString().contains("sensors/missing"));
    }

    @Test
    @DisplayName("handle(): GET without a topic parameter returns 400 with an error property")
    void testHandleGetMissingTopicReturns400() {
        var service = serviceWithRouter(router);
        var ex = exchangeFor(METHOD.GET, null);

        service.handle(ex.request(), ex.response());

        assertEquals(HttpStatus.SC_BAD_REQUEST, ex.response().getStatusCode());
        var obj = ex.response().getContent().getAsJsonObject();
        assertNotNull(obj.get("error"));
    }

    @Test
    @DisplayName("handle(): GET with an empty topic parameter returns 400 with an error property")
    void testHandleGetEmptyTopicReturns400() {
        var service = serviceWithRouter(router);
        var ex = exchangeFor(METHOD.GET, "");

        service.handle(ex.request(), ex.response());

        assertEquals(HttpStatus.SC_BAD_REQUEST, ex.response().getStatusCode());
        var obj = ex.response().getContent().getAsJsonObject();
        assertNotNull(obj.get("error"));
    }

    @Test
    @DisplayName("handle(): a URL-encoded topic query parameter resolves to the decoded topic")
    void testHandleGetUrlEncodedTopicResolvesToDecodedTopic() {
        cacheMessage("sensors/temp", "{\"temp\":25}", 0);

        var service = serviceWithRouter(router);
        // "sensors%2Ftemp" decodes to "sensors/temp"
        var ex = exchangeFor(METHOD.GET, "sensors%2Ftemp");

        service.handle(ex.request(), ex.response());

        assertEquals(HttpStatus.SC_OK, ex.response().getStatusCode());
        var obj = ex.response().getContent().getAsJsonObject();
        assertEquals("sensors/temp", obj.get("topic").getAsString());
    }

    @Test
    @DisplayName("handle(): a method other than GET or OPTIONS returns 405")
    void testHandleUnsupportedMethodReturns405() {
        var service = serviceWithRouter(router);
        var ex = exchangeFor(METHOD.POST, null);

        service.handle(ex.request(), ex.response());

        assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED, ex.response().getStatusCode());
    }

    @Test
    @DisplayName("handle(): OPTIONS is dispatched without error")
    void testHandleOptionsDoesNotThrow() {
        var service = serviceWithRouter(router);
        var ex = exchangeFor(METHOD.OPTIONS, null);

        assertDoesNotThrow(() -> service.handle(ex.request(), ex.response()));
        assertEquals(HttpStatus.SC_OK, ex.response().getStatusCode());
    }
}
