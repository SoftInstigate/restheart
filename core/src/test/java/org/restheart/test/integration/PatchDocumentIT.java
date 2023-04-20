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
public class PatchDocumentIT extends HttpClientAbstactIT {

    private final String DB = TEST_DB_PREFIX + "-patch-document-db";
    private final String COLL = "coll";

    @SuppressWarnings("rawtypes")
    private HttpResponse resp;

    /**
     *
     */
    public PatchDocumentIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPatchDocument() throws Exception {
        Response resp;

        // *** PUT tmpdb
        resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put db", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        resp = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put coll1", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpdoc
        resp = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp doc", resp, HttpStatus.SC_CREATED);

        // try to patch without body
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING,
                Exchange.HAL_JSON_MEDIA_TYPE));
        check("check patch tmp doc without data", resp, HttpStatus.SC_NOT_ACCEPTABLE);

        // try to patch without etag forcing checkEtag
        resp = adminExecutor.execute(Request.Patch(addCheckEtag(documentTmpUri)).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check patch tmp doc without etag forcing checkEtag", resp, HttpStatus.SC_CONFLICT);

        // try to patch without etag no checkEtag
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check patch tmp doc without etag", resp, HttpStatus.SC_OK);

        // try to patch with wrong etag
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE)
                .addHeader(Headers.IF_MATCH_STRING, "pippoetag"));
        check("check patch tmp doc with wrong etag", resp, HttpStatus.SC_PRECONDITION_FAILED);

        resp = adminExecutor.execute(Request.Get(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING,
                Exchange.HAL_JSON_MEDIA_TYPE));

        JsonObject content = Json.parse(resp.returnContent().asString()).asObject();

        String etag = content.get("_etag").asObject().get("$oid").asString();

        // try to patch with correct etag
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).bodyString("{b:2}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE)
                .addHeader(Headers.IF_MATCH_STRING, etag));
        check("check patch tmp doc with correct etag", resp, HttpStatus.SC_OK);

        resp = adminExecutor.execute(Request.Get(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING,
                Exchange.HAL_JSON_MEDIA_TYPE));

        content = Json.parse(resp.returnContent().asString()).asObject();
        assertNotNull(content.get("a"), "check patched content");
        assertNotNull(content.get("b"), "check patched content");
        assertTrue(content.get("a").asInt() == 1 && content.get("b").asInt() == 2, "check patched content");
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
    public void testPatchDocumentDotNotation() throws Exception {
        resp = Unirest.put(url(DB, COLL, "docid1"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .body("{ 'array': [] }")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED,
                resp.getStatus(), "check response status of create test data");

        resp = Unirest.patch(url(DB, COLL, "docid1"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{ 'doc.number': 1, 'array.0': {'string': 'ciao'} }")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_OK,
                resp.getStatus(), "check response status of update test data");

        resp = Unirest.get(url(DB, COLL, "docid1"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_OK,
                resp.getStatus(), "check response status of get test data");

        JsonValue rbody = Json.parse(resp.getBody().toString());

        assertTrue(
                rbody != null
                        && rbody.isObject(),
                "check data to be a json object");

        JsonValue doc = rbody.asObject().get("doc");

        assertTrue(
                doc != null
                        && doc.isObject(),
                "check data to have the 'doc' json object");

        JsonValue number = doc.asObject().get("number");

        assertTrue(
                number != null
                        && number.isNumber()
                        && number.asInt() == 1,
                "check doc to have the 'number' property");

        JsonValue array = rbody.asObject().get("array");

        assertTrue(
                array != null
                        && array.isArray(),
                "check data to have the 'array' json array");

        JsonValue element = array.asArray().get(0);

        assertTrue(
                element != null
                        && element.isObject(),
                "check array to have an object element");

        JsonValue string = element.asObject().get("string");

        assertTrue(
                string != null
                        && string.isString()
                        && string.asString().equals("ciao"),
                "check the array element to have the 'string' property");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPatchDocumentOperators() throws Exception {
        resp = Unirest.put(url(DB, COLL, "docid2"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .body("{ }")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED,
                resp.getStatus(), "check response status of create test data");

        resp = Unirest.patch(url(DB, COLL, "docid2"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{ '$push': {'array': 'a'}, '$inc': { 'count': 100 }, '$currentDate': {'timestamp': true } }")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_OK,
                resp.getStatus(), "check response status of patch test data");

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

        JsonValue array = rbody.asObject().get("array");

        assertTrue(
                array != null
                        && array.isArray()
                        && array.asArray().size() == 1,
                "check data to have the 'array' array with one element");

        JsonValue element = array.asArray().get(0);

        assertTrue(
                element != null
                        && element.isString()
                        && element.asString().equals("a"),
                "check array element to be the string 'a'");

        JsonValue count = rbody.asObject().get("count");

        assertTrue(
                count != null
                        && count.isNumber()
                        && count.asInt() == 100,
                "check count property to be 100");

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

    /**
     * issue https://github.com/SoftInstigate/restheart/issues/232
     *
     * PATCH not existing document with $addToSet operator lead to response code
     * 500 document is however created as expected
     *
     * @throws Exception
     */
    @Test
    public void testPatchDocumentIssue232() throws Exception {
        resp = Unirest.patch(url(DB, COLL, "issue232"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .body("{ '$addToSet':{ 'addresses':{ 'addressType' : 'N', 'line2':'line 2' } } }")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED,
                resp.getStatus(), "check response status of patch test data");

        resp = Unirest.patch(url(DB, COLL, "issue232"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{ '$addToSet':{ 'addresses':{ 'addressType' : 'N', 'line2':'line 2' } } }")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_OK,
                resp.getStatus(), "check response status of patch test data");
    }
}
