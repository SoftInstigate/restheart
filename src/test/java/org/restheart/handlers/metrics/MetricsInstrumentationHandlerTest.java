package org.restheart.handlers.metrics;

import com.codahale.metrics.MetricRegistry;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;
import org.restheart.Configuration;
import org.restheart.handlers.exchange.BsonRequest;

/**
 *
 * @author uji
 */
public class MetricsInstrumentationHandlerTest {

    /**
     *
     */
    @Before
    public void setUp() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testIfFilledAndNotMetrics() throws Exception {
        assertTrue(MetricsInstrumentationHandler.isFilledAndNotMetrics("foobar"));
        assertTrue(MetricsInstrumentationHandler.isFilledAndNotMetrics("rainbow"));
        assertFalse(MetricsInstrumentationHandler.isFilledAndNotMetrics("_metrics"));
        assertFalse(MetricsInstrumentationHandler.isFilledAndNotMetrics(""));
        assertFalse(MetricsInstrumentationHandler.isFilledAndNotMetrics("  "));
        assertFalse(MetricsInstrumentationHandler.isFilledAndNotMetrics(null));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testAddMetrics() throws Exception {
        Configuration config = mock(Configuration.class);
        when(config.gatheringAboveOrEqualToLevel(Configuration.METRICS_GATHERING_LEVEL.ROOT)).thenReturn(true);
        when(config.gatheringAboveOrEqualToLevel(Configuration.METRICS_GATHERING_LEVEL.DATABASE)).thenReturn(true);
        when(config.gatheringAboveOrEqualToLevel(Configuration.METRICS_GATHERING_LEVEL.COLLECTION)).thenReturn(true);

        MetricRegistry registry = new MetricRegistry();
        MetricRegistry registryDb = new MetricRegistry();
        MetricRegistry registryColl = new MetricRegistry();
        SharedMetricRegistryProxy proxy = new SharedMetricRegistryProxy() {
            @Override
            public MetricRegistry registry() {
                return registry;
            }

            @Override
            public MetricRegistry registry(String dbName) {
                return registryDb;
            }

            @Override
            public MetricRegistry registry(String dbName, String collectionName) {
                return registryColl;
            }
        };

        MetricsInstrumentationHandler mih = new MetricsInstrumentationHandler(null);
        mih.configuration = config;
        mih.metrics = proxy;
        
        HttpServerExchange httpServerExchange = mock(HttpServerExchange.class);
        when(httpServerExchange.getStatusCode()).thenReturn(200);
        when(httpServerExchange.getRequestMethod()).thenReturn(Methods.GET);
        when(httpServerExchange.getRequestPath()).thenReturn("/foo/bar");
        
        var request = BsonRequest.init(httpServerExchange, "foo", "bar");
        
        when(httpServerExchange.getAttachment(anyObject())).thenReturn(request);

        mih.addMetrics(0, httpServerExchange);

        mih.addMetrics(0, httpServerExchange);

        assertEquals(3, registry.getTimers().size());
        assertEquals(3, registryDb.getTimers().size());
        assertEquals(3, registryColl.getTimers().size());
    }
}