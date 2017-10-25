package org.restheart.handlers.applicationlogic;

import com.codahale.metrics.MetricRegistry;

import org.junit.Before;
import org.junit.Test;
import org.restheart.Configuration;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.SharedMetricRegistryProxy;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MetricsHandlerTest {

    class MyMetricRegistry extends MetricRegistry {
        String name;
        MyMetricRegistry(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Before
    public void setUp() throws Exception {
        handler = new MetricsHandler(null, null);
        handler.configuration = new Configuration();
        handler.metrics = new SharedMetricRegistryProxy(){
            @Override
            public MetricRegistry registry() {
                return new MyMetricRegistry("ROOT");
            }

            @Override
            public MetricRegistry registry(String dbName) {
                return new MyMetricRegistry(dbName);
            }

            @Override
            public MetricRegistry registry(String dbName, String collectionName) {
                return new MyMetricRegistry(dbName + "/" + collectionName);
            }
        };

    }

    MetricsHandler handler;

    private Configuration configWith(Configuration.METRICS_GATHERING_LEVEL mgl) {
        return new Configuration() {
            @Override
            public METRICS_GATHERING_LEVEL getMetricsGatheringLevel() {
                return mgl;
            }
        };
    }

    @Test
    public void testGetCorrectMetricRegistry() throws Exception {
        HttpServerExchange httpServerExchange = mock(HttpServerExchange.class);
        when(httpServerExchange.getStatusCode()).thenReturn(200);
        when(httpServerExchange.getRequestMethod()).thenReturn(Methods.GET);
        when(httpServerExchange.getRequestPath()).thenReturn("/");

        RequestContext requestContext = new RequestContext(httpServerExchange, "/", "/foo/bar");

        handler.configuration = configWith(Configuration.METRICS_GATHERING_LEVEL.ROOT);
        MetricRegistry registry = handler.getCorrectMetricRegistry(requestContext);
        assertNull(registry);

        handler.configuration = configWith(Configuration.METRICS_GATHERING_LEVEL.DATABASE);
        registry = handler.getCorrectMetricRegistry(requestContext);
        assertNull(registry);

        handler.configuration = configWith(Configuration.METRICS_GATHERING_LEVEL.COLLECTION);
        registry = handler.getCorrectMetricRegistry(requestContext);
        assertTrue(registry instanceof MyMetricRegistry);
        assertEquals("foo/bar", ((MyMetricRegistry) registry).getName());

        when(httpServerExchange.getRequestPath()).thenReturn("/");
        requestContext = new RequestContext(httpServerExchange, "/", "/foo");

        handler.configuration = configWith(Configuration.METRICS_GATHERING_LEVEL.ROOT);
        registry = handler.getCorrectMetricRegistry(requestContext);
        assertNull(registry);

        handler.configuration = configWith(Configuration.METRICS_GATHERING_LEVEL.DATABASE);
        registry = handler.getCorrectMetricRegistry(requestContext);
        assertTrue(registry instanceof MyMetricRegistry);
        assertEquals("foo", ((MyMetricRegistry) registry).getName());

        handler.configuration = configWith(Configuration.METRICS_GATHERING_LEVEL.COLLECTION);
        registry = handler.getCorrectMetricRegistry(requestContext);
        assertTrue(registry instanceof MyMetricRegistry);
        assertEquals("foo", ((MyMetricRegistry) registry).getName());

        when(httpServerExchange.getRequestPath()).thenReturn("/");
        requestContext = new RequestContext(httpServerExchange, "/", "/");

        handler.configuration = configWith(Configuration.METRICS_GATHERING_LEVEL.ROOT);
        registry = handler.getCorrectMetricRegistry(requestContext);
        assertTrue(registry instanceof MyMetricRegistry);
        assertEquals("ROOT", ((MyMetricRegistry) registry).getName());

        handler.configuration = configWith(Configuration.METRICS_GATHERING_LEVEL.DATABASE);
        registry = handler.getCorrectMetricRegistry(requestContext);
        assertTrue(registry instanceof MyMetricRegistry);
        assertEquals("ROOT", ((MyMetricRegistry) registry).getName());

        handler.configuration = configWith(Configuration.METRICS_GATHERING_LEVEL.COLLECTION);
        registry = handler.getCorrectMetricRegistry(requestContext);
        assertTrue(registry instanceof MyMetricRegistry);
        assertEquals("ROOT", ((MyMetricRegistry) registry).getName());
    }
}