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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

import io.undertow.util.Headers;

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
        response = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put db", response, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        response = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put coll1", response, HttpStatus.SC_CREATED);

        // *** PUT tmpdoc
        response = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp doc", response, HttpStatus.SC_CREATED);

        // try to put without etag
        response = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp doc without etag", response, HttpStatus.SC_OK);

        // try to put without etag forcing checkEtag
        response = adminExecutor.execute(Request.Put(addCheckEtag(documentTmpUri)).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp doc without etag forcing checkEtag", response, HttpStatus.SC_CONFLICT);

        // try to put with wrong etag
        response = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE)
                .addHeader(Headers.IF_MATCH_STRING, "pippoetag"));
        check("check put tmp doc with wrong etag", response, HttpStatus.SC_PRECONDITION_FAILED);

        response = adminExecutor.execute(Request.Get(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING,
                Exchange.HAL_JSON_MEDIA_TYPE));

        JsonObject content = Json.parse(response.returnContent().asString()).asObject();

        String etag = content.get("_etag").asObject().get("$oid").asString();

        // try to put with correct etag
        response = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{b:2}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE)
                .addHeader(Headers.IF_MATCH_STRING, etag));
        check("check put tmp doc with correct etag", response, HttpStatus.SC_OK);

        response = adminExecutor.execute(Request.Get(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING,
                Exchange.HAL_JSON_MEDIA_TYPE));

        content = Json.parse(response.returnContent().asString()).asObject();
        assertNull(content.get("a"), "check put content");
        assertNotNull(content.get("b"), "check put content");
        assertTrue(content.get("b").asInt() == 2, "check put content");
    }

    /**
     *
     * @throws Exception
     */
    @BeforeEach
    public void createTestData() throws Exception {
        // create test db
        resp = Unirest.put(url(DB))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(), "create db " + DB);

        // create collection
        resp = Unirest.put(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(),
                "create collection " + DB.concat("/").concat(COLL));
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

        assertEquals(HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.put(url(DB, COLL, "testPutWithWrongFilter"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .queryString("filter", "{'a':2}")
                .body("{'modified':true}")
                .asString();

        assertEquals(HttpStatus.SC_CONFLICT, resp.getStatus());
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

        assertEquals(org.apache.http.HttpStatus.SC_CREATED,
                resp.getStatus(), "check response status of create test data");

        resp = Unirest.get(url(DB, COLL, "docid1"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_OK,
                resp.getStatus(), "check response status of get test data");

        JsonValue rbody = Json.parse(resp.getBody().toString());

        assertTrue(rbody != null
                && rbody.isObject(),
                "check data to be a json object");

        JsonValue doc = rbody.asObject().get("doc");

        assertTrue(doc != null
                && doc.isObject(),
                "check data to have the 'doc' json object");

        JsonValue number = doc.asObject().get("number");

        assertTrue(number != null
                && number.isNumber()
                && number.asInt() == 1,
                "check doc to have the 'number' property");
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

        assertEquals(org.apache.http.HttpStatus.SC_BAD_REQUEST, resp.getStatus(),
                "check response status of create test data");

        resp = Unirest.put(url(DB, COLL, "docid2"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .body("{ '$currentDate': {'timestamp': { '$type': 'date' }}}")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(),
                "check response status of create test data with $currentDate operator");

        resp = Unirest.get(url(DB, COLL, "docid2"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_OK,
                resp.getStatus(), "check response status of get test data");

        JsonValue rbody = Json.parse(resp.getBody().toString());

        assertTrue(
                rbody != null
                        && rbody.isObject(),
                "check data to be a json object");

        JsonValue timestamp = rbody.asObject().get("timestamp");

        assertTrue(
                timestamp != null
                        && timestamp.isObject(),
                "check timestamp to be an object");

        JsonValue $date = timestamp.asObject().get("$date");

        assertTrue(
                $date != null
                        && $date.isNumber(),
                "check $date to be numeric");
    }
}
