package org.restheart.mqtt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.ExchangeKeys.METHOD;
import org.restheart.exchange.JsonRequest;
import org.restheart.exchange.JsonResponse;
import org.restheart.exchange.PipelineInfo;
import org.restheart.exchange.PipelineInfo.PIPELINE_TYPE;
import org.restheart.exchange.Request;
import org.restheart.utils.HttpStatus;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 * Unit tests for MqttStatsService.
 * <p>
 * Invokes {@link MqttStatsService#handle} against a real {@link JsonRequest}/{@link JsonResponse}
 * pair backed by a mocked {@link MqttMessageRouter}, asserting the status code and response body
 * the service actually produces.
 * </p>
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MqttStatsServiceTest {

    /** A request/response pair backed by the same fake exchange. */
    private record Exchange(JsonRequest request, JsonResponse response) {
    }

    /**
     * Builds an {@code MqttStatsService} with the given router injected via reflection
     * into its {@code @Inject}-annotated field, mimicking what the DI container does
     * at runtime.
     */
    private MqttStatsService serviceWithRouter(MqttMessageRouter router) {
        var service = new MqttStatsService();
        try {
            Field routerField = MqttStatsService.class.getDeclaredField("router");
            routerField.setAccessible(true);
            routerField.set(service, router);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return service;
    }

    /**
     * Builds a fresh exchange/request/response triple for the given method. The pipeline info is
     * set to SERVICE so that the default {@code Service#handleOptions} method (which looks it up)
     * does not NPE.
     */
    private Exchange exchangeFor(METHOD method) {
        var exchange = new HttpServerExchange();
        exchange.setRequestMethod(HttpString.tryFromString(method.name()));
        Request.setPipelineInfo(exchange, new PipelineInfo(PIPELINE_TYPE.SERVICE, "/mqtt/stats", "mqtt-stats"));

        var request = JsonRequest.init(exchange);
        var response = JsonResponse.init(exchange);
        return new Exchange(request, response);
    }

    @Test
    @DisplayName("handle(): GET returns 200 with the router's stats mapped to JSON")
    void testHandleGetReturnsStatsAsJson() {
        var router = mock(MqttMessageRouter.class);
        when(router.getStats()).thenReturn(new MqttMessageRouter.RouterStats(3, 5, 2, 100L, 7L));

        var service = serviceWithRouter(router);
        var ex = exchangeFor(METHOD.GET);

        service.handle(ex.request(), ex.response());

        assertEquals(HttpStatus.SC_OK, ex.response().getStatusCode());
        var body = ex.response().getContent();
        assertNotNull(body);
        assertTrue(body.isJsonObject());
        var obj = body.getAsJsonObject();
        assertEquals(3, obj.get("topicFilters").getAsInt());
        assertEquals(5, obj.get("totalListeners").getAsInt());
        assertEquals(2, obj.get("cachedMessages").getAsInt());
        assertEquals(100L, obj.get("messagesReceived").getAsLong());
        assertEquals(7L, obj.get("messagesDropped").getAsLong());
    }

    @Test
    @DisplayName("handle(): a method other than GET or OPTIONS returns 405")
    void testHandleUnsupportedMethodReturns405() {
        var router = mock(MqttMessageRouter.class);
        var service = serviceWithRouter(router);
        var ex = exchangeFor(METHOD.POST);

        service.handle(ex.request(), ex.response());

        assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED, ex.response().getStatusCode());
    }

    @Test
    @DisplayName("handle(): OPTIONS is dispatched without error")
    void testHandleOptionsDoesNotThrow() {
        var router = mock(MqttMessageRouter.class);
        var service = serviceWithRouter(router);
        var ex = exchangeFor(METHOD.OPTIONS);

        assertDoesNotThrow(() -> service.handle(ex.request(), ex.response()));
        assertEquals(HttpStatus.SC_OK, ex.response().getStatusCode());
    }
}
