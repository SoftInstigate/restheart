/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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
package org.restheart.mongodb.handlers.metrics;

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
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.MongoServiceConfiguration;
import static org.restheart.mongodb.MongoServiceConfiguration.METRICS_GATHERING_LEVEL.COLLECTION;
import static org.restheart.mongodb.MongoServiceConfiguration.METRICS_GATHERING_LEVEL.DATABASE;
import static org.restheart.mongodb.MongoServiceConfiguration.METRICS_GATHERING_LEVEL.OFF;
import static org.restheart.mongodb.MongoServiceConfiguration.METRICS_GATHERING_LEVEL.ROOT;
public class MetricsHandlerTest {

    private static final String URI_METRICS_ROOT = "/_metrics";
    private static final String URI_METRICS_DATABASE = "/foo/_metrics";
    private static final String URI_METRICS_COLLECTION = "/foo/bar/_metrics";

    MetricsHandler handler;

    /**
     *
     */
    @Before
    public void setUp() {
        handler = new MetricsHandler(null);
        handler.metrics = mock(SharedMongoMetricRegistryProxy.class);
    }

    /**
     *
     */
    @Test
    public void testRequestContextForMetricsRequestToRoot() {
        assertRequestContextForMetricsRequest(createRequest(URI_METRICS_ROOT), "_metrics", null);
    }

    /**
     *
     */
    @Test
    public void testRequestContextForMetricsRequestToDatabase() {
        assertRequestContextForMetricsRequest(createRequest(URI_METRICS_DATABASE), "foo", "_metrics");
    }

    /**
     *
     */
    @Test
    public void testRequestContextForMetricsRequestToCollection() {
        assertRequestContextForMetricsRequest(createRequest(URI_METRICS_COLLECTION), "foo", "bar");
    }

    private void assertRequestContextForMetricsRequest(MongoRequest request, String expectedDatabaseName, String expectedCollectionName) {
        assertEquals(expectedDatabaseName, request.getDBName());
        assertEquals(expectedCollectionName, request.getCollectionName());
    }

    /**
     *
     */
    @Test
    public void testMetricsLevelForRequestToRoot() {
        assertMetricsLevelForRequest(URI_METRICS_ROOT, OFF, ROOT, ROOT, ROOT);
    }

    /**
     *
     */
    @Test
    public void testMetricsLevelForRequestToDatabase() {
        assertMetricsLevelForRequest(URI_METRICS_DATABASE, OFF, OFF, DATABASE, DATABASE);
    }

    /**
     *
     */
    @Test
    public void testMetricsLevelForRequestToCollection() {
        assertMetricsLevelForRequest(URI_METRICS_COLLECTION, OFF, OFF, OFF, COLLECTION);
    }

    private void assertMetricsLevelForRequest(String resourceUri, MongoServiceConfiguration.METRICS_GATHERING_LEVEL expectedLevelForConfigOff,
            MongoServiceConfiguration.METRICS_GATHERING_LEVEL expectedLevelForConfigRoot, MongoServiceConfiguration.METRICS_GATHERING_LEVEL expectedLevelForConfigDatabase,
            MongoServiceConfiguration.METRICS_GATHERING_LEVEL expectedLevelForConfigCollection) {

        var request = createRequest(resourceUri);

        handler.configuration = configWith(OFF);
        assertEquals(expectedLevelForConfigOff, handler.getMetricsLevelForRequest(request));

        handler.configuration = configWith(ROOT);
        assertEquals(expectedLevelForConfigRoot, handler.getMetricsLevelForRequest(request));

        handler.configuration = configWith(DATABASE);
        assertEquals(expectedLevelForConfigDatabase, handler.getMetricsLevelForRequest(request));

        handler.configuration = configWith(COLLECTION);
        assertEquals(expectedLevelForConfigCollection, handler.getMetricsLevelForRequest(request));
    }

    private MongoServiceConfiguration configWith(MongoServiceConfiguration.METRICS_GATHERING_LEVEL mgl) {
        return new MongoServiceConfiguration() {

            @Override
            public METRICS_GATHERING_LEVEL getMetricsGatheringLevel() {
                return mgl;
            }
        };
    }

    /**
     *
     */
    @Test
    public void testMetricsRegistryForRequestToRoot() {
        var requestContext = createRequest(URI_METRICS_ROOT);
        handler.getMetricsRegistry(requestContext, ROOT);
        verify(handler.metrics, times(1)).registry();
    }

    /**
     *
     */
    @Test
    public void testMetricsRegistryForRequestToDatabase() {
        var requestContext = createRequest(URI_METRICS_DATABASE);
        handler.getMetricsRegistry(requestContext, DATABASE);
        verify(handler.metrics, times(1)).registry(eq(requestContext.getDBName()));
    }

    /**
     *
     */
    @Test
    public void testMetricsRegistryForRequestToCollection() {
        var requestContext = createRequest(URI_METRICS_COLLECTION);
        handler.getMetricsRegistry(requestContext, COLLECTION);
        verify(handler.metrics, times(1)).registry(eq(requestContext.getDBName()), eq(requestContext.getCollectionName()));
    }

    private MongoRequest createRequest(String resourceUri) {
        HttpServerExchange httpServerExchange = mock(HttpServerExchange.class);

        when(httpServerExchange.getStatusCode()).thenReturn(200);
        when(httpServerExchange.getRequestMethod()).thenReturn(Methods.GET);
        when(httpServerExchange.getRequestPath()).thenReturn("/");
        return MongoRequest.init(httpServerExchange, "/", resourceUri);
    }
}
