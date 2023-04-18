/*-
 * ========================LICENSE_START=================================
 * restheart-core
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
package org.restheart.test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetMetricsIT extends HttpClientAbstactIT {

    /**
     *
     */
    public GetMetricsIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetMetrics() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(metricsUri).addHeader("Accept", "application/json"));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check status code");
        assertNotNull(entity.getContentType(), "content type not null");
        assertEquals(Exchange.JSON_MEDIA_TYPE, entity.getContentType().getValue(), "check content type");

        String content = EntityUtils.toString(entity);
        assertTrue(content.contains("\"version\": \"3.0.0\""));
        assertTrue(content.contains("\"METRICS.GET\": {"));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetMetricsPrometheus() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(metricsUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check status code");
        assertNotNull(entity.getContentType(), "content type not null");
        assertEquals("text/plain; version=0.0.4", entity.getContentType().getValue(), "check content type");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetMetricsForUnknownCollection() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(metricsUnknownCollectionUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        // configuration says: it is not activated for collections, so it should return
        // 404, default restheart answer
        assertEquals(HttpStatus.SC_NOT_FOUND, statusLine.getStatusCode(), "check status code");
        assertNotNull(entity.getContentType(), "content type not null");
        assertEquals("application/json", entity.getContentType().getValue(), "check content type");
    }
}
