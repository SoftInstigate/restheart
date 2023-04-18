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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.restheart.utils.HttpStatus;

import io.undertow.util.Headers;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class ContentEncodingIT extends HttpClientAbstactIT {

    /**
     *
     */
    protected static Executor notDecompressingExecutor = null;

    /**
     *
     * @throws Exception
     */
    @BeforeAll
    public static void init() throws Exception {
        notDecompressingExecutor = Executor.newInstance(HttpClients.custom()
                .disableContentCompression().build())
                .authPreemptive(HTTP_HOST)
                .auth(new HttpHost(HTTP_HOST.getHostName()), "admin", "secret");
    }

    /**
     *
     */
    public ContentEncodingIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGzipAcceptEncoding() throws Exception {
        Response resp = notDecompressingExecutor.execute(
                Request.Get(rootUri).addHeader(
                        Headers.ACCEPT_ENCODING_STRING, Headers.GZIP.toString()));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        String content = EntityUtils.toString(entity);

        Header h = httpResp.getFirstHeader("Content-Encoding");

        assertNotNull(h, "check accept encoding header not null");
        assertEquals(Headers.GZIP.toString(), h.getValue(), "check accept encoding header value");

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check status code");

        try {
            GZIPInputStream gzipis = new GZIPInputStream(
                    new ByteArrayInputStream(
                            content.getBytes(StandardCharsets.ISO_8859_1)));

            while (gzipis.read() > 0) {

            }
        } catch (Exception ex) {
            fail("check decompressing content");
        }
    }
}
