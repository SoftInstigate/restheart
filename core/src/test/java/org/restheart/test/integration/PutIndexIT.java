/*-
 * ========================LICENSE_START=================================
 * restheart-core
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
package org.restheart.test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import io.undertow.util.Headers;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PutIndexIT extends HttpClientAbstactIT {

    /**
     *
     */
    public PutIndexIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPutIndex() throws Exception {
        Response resp;

        // *** PUT tmpdb
        resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put db", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        resp = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put coll1", resp, HttpStatus.SC_CREATED);

        // *** PUT wrong index
        // resp = adminExecutor.execute(Request.Put(indexTmpUri).bodyString("{a:1}",
        // halCT).addHeader(Headers.CONTENT_TYPE_STRING,
        // Representation.HAL_JSON_MEDIA_TYPE));
        // check("check put wrong index", resp, HttpStatus.SC_BAD_REQUEST);
        resp = adminExecutor
                .execute(Request.Put(indexTmpUri).bodyString("{ keys: {a:1,b:2}, ops: { name: \"ciao\"} }", halCT)
                        .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put correct index", resp, HttpStatus.SC_CREATED);

        resp = adminExecutor.execute(
                Request.Get(indexesTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check status code");
        assertNotNull(entity.getContentType(), "content type not null");
        assertTrue(entity.getContentType().getValue().startsWith(Exchange.HAL_JSON_MEDIA_TYPE), "check content type");

        String content = EntityUtils.toString(entity);

        assertNotNull(content, "");

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull(json, "check json not null");
        assertNotNull(json.get("_returned"), "check not null _returned property");
        assertNotNull(json.get("_size"), "check not null _size property");
        assertEquals(2, json.get("_size").asInt(), "check _size value to be 2");
        assertEquals(2, json.get("_returned").asInt(), "check _returned value to be 2");
    }
}
