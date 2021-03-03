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
import com.eclipsesource.json.JsonValue;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import io.undertow.util.Headers;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PutDocumentIT extends HttpClientAbstactIT {

    private final String DB = TEST_DB_PREFIX + "-put-document-db";
    private final String COLL = "coll";

    @SuppressWarnings("rawtypes")
    private HttpResponse resp;

    /**
     *
     */
    public PutDocumentIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPutDocument() throws Exception {
        Response response;

        // *** PUT tmpdb
        response = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put db", response, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        response = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put coll1", response, HttpStatus.SC_CREATED);

        // *** PUT tmpdoc
        response = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp doc", response, HttpStatus.SC_CREATED);

        // try to put without etag
        response = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp doc without etag", response, HttpStatus.SC_OK);

        // try to put without etag forcing checkEtag
        response = adminExecutor.execute(Request.Put(addCheckEtag(documentTmpUri)).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp doc without etag forcing checkEtag", response, HttpStatus.SC_CONFLICT);

        // try to put with wrong etag
        response = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE).addHeader(Headers.IF_MATCH_STRING, "pippoetag"));
        check("check put tmp doc with wrong etag", response, HttpStatus.SC_PRECONDITION_FAILED);

        response = adminExecutor.execute(Request.Get(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        JsonObject content = Json.parse(response.returnContent().asString()).asObject();

        String etag = content.get("_etag").asObject().get("$oid").asString();

        // try to put with correct etag
        response = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{b:2}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE).addHeader(Headers.IF_MATCH_STRING, etag));
        check("check put tmp doc with correct etag", response, HttpStatus.SC_OK);

        response = adminExecutor.execute(Request.Get(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        content = Json.parse(response.returnContent().asString()).asObject();
        assertNull("check put content", content.get("a"));
        assertNotNull("check put content", content.get("b"));
        assertTrue("check put content", content.get("b").asInt() == 2);
    }

    /**
     *
     * @throws Exception
     */
    @Before
    public void createTestData() throws Exception {
        // create test db
        resp = Unirest.put(url(DB))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("create db " + DB, org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        // create collection
        resp = Unirest.put(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("create collection " + DB.concat("/").concat(COLL), org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPutDocumentWithNotMatchingFilter() throws Exception {
        resp = Unirest.put(url(DB, COLL, "testPutWithWrongFilter"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .body("{'a':1}")
                .asString();

        Assert.assertEquals(HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.put(url(DB, COLL, "testPutWithWrongFilter"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .queryString("filter", "{'a':2}")
                .body("{'modified':true}")
                .asString();

        Assert.assertEquals(HttpStatus.SC_CONFLICT, resp.getStatus());
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPutDocumentDotNotation() throws Exception {
        resp = Unirest.put(url(DB, COLL, "docid1"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .body("{ 'doc.number': 1 }")
                .asString();

        Assert.assertEquals("check response status of create test data", org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.get(url(DB, COLL, "docid1"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("check response status of get test data", org.apache.http.HttpStatus.SC_OK, resp.getStatus());

        JsonValue rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("check data to be a json object",
                rbody != null
                && rbody.isObject());

        JsonValue doc = rbody.asObject().get("doc");

        Assert.assertTrue("check data to have the 'doc' json object",
                doc != null
                && doc.isObject());

        JsonValue number = doc.asObject().get("number");

        Assert.assertTrue("check doc to have the 'number' property",
                number != null
                && number.isNumber()
                && number.asInt() == 1);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPutDocumentOperators() throws Exception {
        resp = Unirest.put(url(DB, COLL, "docid2"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .body("{ '$push': {'array': 'a'}, '$inc': { 'count': 100 } }")
                .asString();

        Assert.assertEquals("check response status of create test data", org.apache.http.HttpStatus.SC_BAD_REQUEST, resp.getStatus());

        resp = Unirest.put(url(DB, COLL, "docid2"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .body("{ '$currentDate': {'timestamp': { '$type': 'date' }}}")
                .asString();

        Assert.assertEquals("check response status of create test data with $currentDate operator", org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.get(url(DB, COLL, "docid2"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("check response status of get test data", org.apache.http.HttpStatus.SC_OK, resp.getStatus());

        JsonValue rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("check data to be a json object",
                rbody != null
                && rbody.isObject());

        JsonValue timestamp = rbody.asObject().get("timestamp");

        Assert.assertTrue("check timestamp to be an object",
                timestamp != null
                && timestamp.isObject());

        JsonValue $date = timestamp.asObject().get("$date");

        Assert.assertTrue("check $date to be numeric",
                $date != null
                && $date.isNumber());
    }
}
