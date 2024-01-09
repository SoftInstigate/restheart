/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2024 SoftInstigate
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
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
public class PutDBIT extends HttpClientAbstactIT {

    /**
     *
     */
    public PutDBIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPutCollection() throws Exception {
        Response resp;

        // *** PUT tmpdb
        resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put db", resp, HttpStatus.SC_CREATED);

        // try to put without etag forcing checkEtag
        resp = adminExecutor.execute(Request.Put(addCheckEtag(dbTmpUri)).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp db without etag forcing checkEtag", resp, HttpStatus.SC_CONFLICT);

        // try to put without etag
        resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp db without etag", resp, HttpStatus.SC_OK);

        // try to put with wrong etag
        resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE)
                .addHeader(Headers.IF_MATCH_STRING, "pippoetag"));
        check("check put tmp db with wrong etag forcing checkEtag", resp, HttpStatus.SC_PRECONDITION_FAILED);

        resp = adminExecutor
                .execute(Request.Get(dbTmpUri).addHeader(Headers.CONTENT_TYPE_STRING,
                        Exchange.HAL_JSON_MEDIA_TYPE));

        JsonObject content = Json.parse(resp.returnContent().asString()).asObject();

        String etag = content.get("_etag").asObject().get("$oid").asString();

        // try to put with correct etag
        resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{b:2}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE)
                .addHeader(Headers.IF_MATCH_STRING, etag));
        check("check put tmp db with correct etag", resp, HttpStatus.SC_OK);

        resp = adminExecutor
                .execute(Request.Get(dbTmpUri).addHeader(Headers.CONTENT_TYPE_STRING,
                        Exchange.HAL_JSON_MEDIA_TYPE));

        content = Json.parse(resp.returnContent().asString()).asObject();
        assertNull(content.get("a"), "check put content");
        assertNotNull(content.get("b"), "check put content");
        assertTrue(content.get("b").asInt() == 2, "check put content");
    }
}
