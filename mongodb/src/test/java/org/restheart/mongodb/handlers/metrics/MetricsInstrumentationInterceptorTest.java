/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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

import com.codahale.metrics.MetricRegistry;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;
import org.restheart.exchange.MongoRequest;
import org.restheart.mongodb.MongoServiceConfiguration;
public class MetricsInstrumentationInterceptorTest {

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
        assertTrue(MetricsInstrumentationInterceptor.isFilledAndNotMetrics("foobar"));
        assertTrue(MetricsInstrumentationInterceptor.isFilledAndNotMetrics("rainbow"));
        assertFalse(MetricsInstrumentationInterceptor.isFilledAndNotMetrics("_metrics"));
        assertFalse(MetricsInstrumentationInterceptor.isFilledAndNotMetrics(""));
        assertFalse(MetricsInstrumentationInterceptor.isFilledAndNotMetrics("  "));
        assertFalse(MetricsInstrumentationInterceptor.isFilledAndNotMetrics(null));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testAddMetrics() throws Exception {
        MongoServiceConfiguration config = mock(MongoServiceConfiguration.class);
        when(config.gatheringAboveOrEqualToLevel(MongoServiceConfiguration.METRICS_GATHERING_LEVEL.ROOT)).thenReturn(true);
        when(config.gatheringAboveOrEqualToLevel(MongoServiceConfiguration.METRICS_GATHERING_LEVEL.DATABASE)).thenReturn(true);
        when(config.gatheringAboveOrEqualToLevel(MongoServiceConfiguration.METRICS_GATHERING_LEVEL.COLLECTION)).thenReturn(true);

        MetricRegistry registry = new MetricRegistry();
        MetricRegistry registryDb = new MetricRegistry();
        MetricRegistry registryColl = new MetricRegistry();
        SharedMongoMetricRegistryProxy proxy = new SharedMongoMetricRegistryProxy() {
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

        MetricsInstrumentationInterceptor mih = new MetricsInstrumentationInterceptor();
        mih.configuration = config;
        mih.metrics = proxy;

        HttpServerExchange httpServerExchange = mock(HttpServerExchange.class);
        when(httpServerExchange.getStatusCode()).thenReturn(200);
        when(httpServerExchange.getRequestMethod()).thenReturn(Methods.GET);
        when(httpServerExchange.getRequestPath()).thenReturn("/foo/bar");

        var request = MongoRequest.init(httpServerExchange, "foo", "bar");

        when(httpServerExchange.getAttachment(any())).thenReturn(request);

        mih.addMetrics(0, httpServerExchange);

        mih.addMetrics(0, httpServerExchange);

        assertEquals(3, registry.getTimers().size());
        assertEquals(3, registryDb.getTimers().size());
        assertEquals(3, registryColl.getTimers().size());
    }
}
