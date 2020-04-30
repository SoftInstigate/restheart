/*-
 * ========================LICENSE_START=================================
 * restheart-core
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
package org.restheart.test.integration;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import io.undertow.util.Headers;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Test;
import org.restheart.exchange.Exchange;
import static org.restheart.test.integration.HttpClientAbstactIT.adminExecutor;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DeleteCollectionIT extends HttpClientAbstactIT {

    /**
     *
     */
    public DeleteCollectionIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testDeleteCollection() throws Exception {
        Response resp;

        // *** PUT tmpdb
        resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put db", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        resp = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put coll1", resp, HttpStatus.SC_CREATED);

        // try to delete without etag
        resp = adminExecutor.execute(Request.Delete(collectionTmpUri));
        check("check delete tmp doc without etag", resp, HttpStatus.SC_CONFLICT);

        // try to delete with wrong etag
        resp = adminExecutor.execute(Request.Delete(collectionTmpUri).addHeader(Headers.IF_MATCH_STRING, "pippoetag"));
        check("check delete tmp doc with wrong etag", resp, HttpStatus.SC_PRECONDITION_FAILED);

        resp = adminExecutor.execute(Request.Get(collectionTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        //check("getting etag of tmp doc", resp, HttpStatus.SC_OK);

        JsonObject content = Json.parse(resp.returnContent().asString()).asObject();

        String etag = content.get("_etag").asObject().get("$oid").asString();

        // try to delete with correct etag
        resp = adminExecutor.execute(Request.Delete(collectionTmpUri).addHeader(Headers.IF_MATCH_STRING, etag));
        check("check delete tmp doc with correct etag", resp, HttpStatus.SC_NO_CONTENT);

        resp = adminExecutor.execute(Request.Get(collectionTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check get deleted tmp doc", resp, HttpStatus.SC_NOT_FOUND);
    }
}
