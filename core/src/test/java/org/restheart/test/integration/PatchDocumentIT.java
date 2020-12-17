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
        resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put db", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        resp = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put coll1", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpdoc
        resp = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp doc", resp, HttpStatus.SC_CREATED);

        // try to patch without body
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check patch tmp doc without data", resp, HttpStatus.SC_NOT_ACCEPTABLE);

        // try to patch without etag forcing checkEtag
        resp = adminExecutor.execute(Request.Patch(addCheckEtag(documentTmpUri)).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check patch tmp doc without etag forcing checkEtag", resp, HttpStatus.SC_CONFLICT);

        // try to patch without etag no checkEtag
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check patch tmp doc without etag", resp, HttpStatus.SC_OK);

        // try to patch with wrong etag
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE).addHeader(Headers.IF_MATCH_STRING, "pippoetag"));
        check("check patch tmp doc with wrong etag", resp, HttpStatus.SC_PRECONDITION_FAILED);

        resp = adminExecutor.execute(Request.Get(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        JsonObject content = Json.parse(resp.returnContent().asString()).asObject();

        String etag = content.get("_etag").asObject().get("$oid").asString();

        // try to patch with correct etag
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).bodyString("{b:2}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE).addHeader(Headers.IF_MATCH_STRING, etag));
        check("check patch tmp doc with correct etag", resp, HttpStatus.SC_OK);

        resp = adminExecutor.execute(Request.Get(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        content = Json.parse(resp.returnContent().asString()).asObject();
        assertNotNull("check patched content", content.get("a"));
        assertNotNull("check patched content", content.get("b"));
        assertTrue("check patched content", content.get("a").asInt() == 1 && content.get("b").asInt() == 2);
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
    public void testPatchDocumentDotNotation() throws Exception {
        resp = Unirest.put(url(DB, COLL, "docid1"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{ 'array': [] }")
                .asString();

        Assert.assertEquals("check response status of create test data", org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.patch(url(DB, COLL, "docid1"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{ 'doc.number': 1, 'array.0': {'string': 'ciao'} }")
                .asString();

        Assert.assertEquals("check response status of update test data", org.apache.http.HttpStatus.SC_OK, resp.getStatus());

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

        JsonValue array = rbody.asObject().get("array");

        Assert.assertTrue("check data to have the 'array' json array",
                array != null
                && array.isArray());

        JsonValue element = array.asArray().get(0);

        Assert.assertTrue("check array to have an object element",
                element != null
                && element.isObject());

        JsonValue string = element.asObject().get("string");

        Assert.assertTrue("check the array element to have the 'string' property",
                string != null
                && string.isString()
                && string.asString().equals("ciao"));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPatchDocumentOperators() throws Exception {
        resp = Unirest.put(url(DB, COLL, "docid2"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{ }")
                .asString();

        Assert.assertEquals("check response status of create test data", org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.patch(url(DB, COLL, "docid2"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{ '$push': {'array': 'a'}, '$inc': { 'count': 100 }, '$currentDate': {'timestamp': true } }")
                .asString();

        Assert.assertEquals("check response status of patch test data", org.apache.http.HttpStatus.SC_OK, resp.getStatus());

        resp = Unirest.get(url(DB, COLL, "docid2"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("check response status of get test data", org.apache.http.HttpStatus.SC_OK, resp.getStatus());

        JsonValue rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("check data to be a json object",
                rbody != null
                && rbody.isObject());

        JsonValue array = rbody.asObject().get("array");

        Assert.assertTrue("check data to have the 'array' array with one element",
                array != null
                && array.isArray()
                && array.asArray().size() == 1);

        JsonValue element = array.asArray().get(0);

        Assert.assertTrue("check array element to be the string 'a'",
                element != null
                && element.isString()
                && element.asString().equals("a"));

        JsonValue count = rbody.asObject().get("count");

        Assert.assertTrue("check count property to be 100",
                count != null
                && count.isNumber()
                && count.asInt() == 100);

        JsonValue timestamp = rbody.asObject().get("timestamp");

        Assert.assertTrue("check timestamp to be an object",
                timestamp != null
                && timestamp.isObject());

        JsonValue $date = timestamp.asObject().get("$date");

        Assert.assertTrue("check $date to be numeric",
                $date != null
                && $date.isNumber());
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
                .header("content-type", "application/json")
                .body("{ '$addToSet':{ 'addresses':{ 'addressType' : 'N', 'line2':'line 2' } } }")
                .asString();

        Assert.assertEquals("check response status of patch test data", org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.patch(url(DB, COLL, "issue232"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{ '$addToSet':{ 'addresses':{ 'addressType' : 'N', 'line2':'line 2' } } }")
                .asString();

        Assert.assertEquals("check response status of patch test data", org.apache.http.HttpStatus.SC_OK, resp.getStatus());
    }
}
