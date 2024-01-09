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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import kong.unirest.Unirest;

import io.undertow.util.Headers;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class PostCollectionIT extends HttpClientAbstactIT {

    private final String DB = TEST_DB_PREFIX + "-post-collection-db";
    private final String COLL = "coll";

    @SuppressWarnings("rawtypes")
    private kong.unirest.HttpResponse resp;

    /**
     *
     */
    public PostCollectionIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPostCollection() throws Exception {
        Response response;

        // *** PUT tmpdb
        response = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put db", response, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        response = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put coll1", response, HttpStatus.SC_CREATED);

        response = adminExecutor.execute(Request.Post(collectionTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check post coll1", response, HttpStatus.SC_CREATED);

        // *** POST tmpcoll
        response = adminExecutor.execute(Request.Post(collectionTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        HttpResponse httpResp = check("check post coll1 again", response, HttpStatus.SC_CREATED);

        Header[] headers = httpResp.getHeaders(Headers.LOCATION_STRING);

        assertNotNull(headers, "check loocation header");
        assertTrue(headers.length > 0, "check loocation header");

        Header locationH = headers[0];
        String location = locationH.getValue();

        URI createdDocUri = URI.create(location);

        response = adminExecutor.execute(Request.Get(createdDocUri).addHeader(Headers.CONTENT_TYPE_STRING,
                Exchange.HAL_JSON_MEDIA_TYPE));

        JsonObject content = Json.parse(response.returnContent().asString()).asObject();
        assertNotNull(content.get("_id"), "check created doc content");
        assertNotNull(content.get("_etag"), "check created doc content");
        assertNotNull(content.get("a"), "check created doc content");
        assertTrue(content.get("a").asInt() == 1, "check created doc content");

        String _id = content.get("_id").asObject().get("$oid").asString();
        String _etag = content.get("_etag").asObject().get("$oid").asString();

        // try to post with _id without etag forcing checkEtag
        response = adminExecutor.execute(Request.Post(addCheckEtag(collectionTmpUri))
                .bodyString("{_id:{\"$oid\":\"" + _id + "\"}, a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check post created doc without etag forcing checkEtag", response, HttpStatus.SC_CONFLICT);

        // try to post with wrong etag
        response = adminExecutor.execute(Request.Post(collectionTmpUri)
                .bodyString("{_id:{\"$oid\":\"" + _id + "\"}, a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE)
                .addHeader(Headers.IF_MATCH_STRING, "pippoetag"));
        check("check put created doc with wrong etag", response, HttpStatus.SC_PRECONDITION_FAILED);

        // try to post with correct etag
        response = adminExecutor.execute(Request.Post(collectionTmpUri)
                .bodyString("{_id:{\"$oid\":\"" + _id + "\"}, a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE)
                .addHeader(Headers.IF_MATCH_STRING, _etag));
        check("check post created doc with correct etag", response, HttpStatus.SC_OK);

        // try to post with _id without etag
        response = adminExecutor.execute(Request.Post(collectionTmpUri)
                .bodyString("{_id:{\"$oid\":\"" + _id + "\"}, a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check post created doc without etag", response, HttpStatus.SC_OK);
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
    public void testPostDocumentDotNotation() throws Exception {
        resp = Unirest.post(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{ '_id': 'docid1', 'doc.number': 1 }")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED,
                resp.getStatus(), "check response status of create test data");

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
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPostDocumentOperators() throws Exception {
        resp = Unirest.post(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{ '_id': 'docid2', '$push': {'array': 'a'}, '$inc': { 'count': 100 }}")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_BAD_REQUEST, resp.getStatus(),
                "check response status of create test data");

        resp = Unirest.post(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .queryString("wm", "upsert")
                .header("content-type", "application/json")
                .body("{ '_id': 'docid2', '$currentDate': {'timestamp': true } }")
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
