package org.restheart.handlers.metrics;

import org.restheart.handlers.metrics.MetricsHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.COLLECTION;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.DATABASE;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.OFF;
import static org.restheart.Configuration.METRICS_GATHERING_LEVEL.ROOT;

import org.restheart.Configuration;
import org.restheart.handlers.RequestContext;
import org.restheart.handlers.metrics.SharedMetricRegistryProxy;

public class MetricsHandlerTest {

    private static final String URI_METRICS_ROOT = "/_metrics";
    private static final String URI_METRICS_DATABASE = "/foo/_metrics";
    private static final String URI_METRICS_COLLECTION = "/foo/bar/_metrics";

    MetricsHandler handler;

    @Before
    public void setUp() {
        handler = new MetricsHandler(null, null);
        handler.metrics = mock(SharedMetricRegistryProxy.class);
    }

    @Test
    public void testRequestContextForMetricsRequestToRoot() {
        assertRequestContextForMetricsRequest(createRequestContext(URI_METRICS_ROOT), "_metrics", null);
    }

    @Test
    public void testRequestContextForMetricsRequestToDatabase() {
        assertRequestContextForMetricsRequest(createRequestContext(URI_METRICS_DATABASE), "foo", "_metrics");
    }

    @Test
    public void testRequestContextForMetricsRequestToCollection() {
        assertRequestContextForMetricsRequest(createRequestContext(URI_METRICS_COLLECTION), "foo", "bar");
    }

    private void assertRequestContextForMetricsRequest(RequestContext requestContext, String expectedDatabaseName, String expectedCollectionName) {
        assertEquals(expectedDatabaseName, requestContext.getDBName());
        assertEquals(expectedCollectionName, requestContext.getCollectionName());
    }

    @Test
    public void testMetricsLevelForRequestToRoot() {
        assertMetricsLevelForRequest(URI_METRICS_ROOT, OFF, ROOT, ROOT, ROOT);
    }

    @Test
    public void testMetricsLevelForRequestToDatabase() {
        assertMetricsLevelForRequest(URI_METRICS_DATABASE, OFF, OFF, DATABASE, DATABASE);
    }

    @Test
    public void testMetricsLevelForRequestToCollection() {
        assertMetricsLevelForRequest(URI_METRICS_COLLECTION, OFF, OFF, OFF, COLLECTION);
    }

    private void assertMetricsLevelForRequest(String requestUri, Configuration.METRICS_GATHERING_LEVEL expectedLevelForConfigOff,
            Configuration.METRICS_GATHERING_LEVEL expectedLevelForConfigRoot, Configuration.METRICS_GATHERING_LEVEL expectedLevelForConfigDatabase,
            Configuration.METRICS_GATHERING_LEVEL expectedLevelForConfigCollection) {

        RequestContext requestContext = createRequestContext(requestUri);

        handler.configuration = configWith(OFF);
        assertEquals(expectedLevelForConfigOff, handler.getMetricsLevelForRequest(requestContext));

        handler.configuration = configWith(ROOT);
        assertEquals(expectedLevelForConfigRoot, handler.getMetricsLevelForRequest(requestContext));

        handler.configuration = configWith(DATABASE);
        assertEquals(expectedLevelForConfigDatabase, handler.getMetricsLevelForRequest(requestContext));

        handler.configuration = configWith(COLLECTION);
        assertEquals(expectedLevelForConfigCollection, handler.getMetricsLevelForRequest(requestContext));
    }

    private Configuration configWith(Configuration.METRICS_GATHERING_LEVEL mgl) {
        return new Configuration() {

            @Override
            public METRICS_GATHERING_LEVEL getMetricsGatheringLevel() {
                return mgl;
            }
        };
    }

    @Test
    public void testMetricsRegistryForRequestToRoot() {
        RequestContext requestContext = createRequestContext(URI_METRICS_ROOT);
        handler.getMetricsRegistry(requestContext, ROOT);
        verify(handler.metrics, times(1)).registry();
    }

    @Test
    public void testMetricsRegistryForRequestToDatabase() {
        RequestContext requestContext = createRequestContext(URI_METRICS_DATABASE);
        handler.getMetricsRegistry(requestContext, DATABASE);
        verify(handler.metrics, times(1)).registry(eq(requestContext.getDBName()));
    }

    @Test
    public void testMetricsRegistryForRequestToCollection() {
        RequestContext requestContext = createRequestContext(URI_METRICS_COLLECTION);
        handler.getMetricsRegistry(requestContext, COLLECTION);
        verify(handler.metrics, times(1)).registry(eq(requestContext.getDBName()), eq(requestContext.getCollectionName()));
    }

    private RequestContext createRequestContext(String requestUri) {
        HttpServerExchange httpServerExchange1 = mock(HttpServerExchange.class);
        when(httpServerExchange1.getStatusCode()).thenReturn(200);
        when(httpServerExchange1.getRequestMethod()).thenReturn(Methods.GET);
        when(httpServerExchange1.getRequestPath()).thenReturn("/");
        HttpServerExchange httpServerExchange = httpServerExchange1;
        return new RequestContext(httpServerExchange, "/", requestUri);
    }
}