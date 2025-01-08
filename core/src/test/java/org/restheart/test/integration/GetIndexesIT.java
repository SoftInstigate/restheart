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

import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import kong.unirest.Unirest;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetIndexesIT extends HttpClientAbstactIT {

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetIndexes() throws Exception {
        testGetIndexes(indexesUri);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetIndexesRemappedAll() throws Exception {
        testGetIndexes(indexesUriRemappedAll);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetIndexesRemappedDb() throws Exception {
        testGetIndexes(indexesUriRemappedDb);
    }

    private void testGetIndexes(URI uri) throws Exception {
        System.out.println("@@@@ testGetIndexes URI=" + uri.toString());
        Response resp = adminExecutor.execute(Request.Get(uri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check status code");
        assertNotNull(entity.getContentType(), "content type not null");
        assertEquals(Exchange.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue(), "check content type");

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
        assertEquals(5, json.get("_size").asInt(), "check _size value to be 5");
        assertEquals(5, json.get("_returned").asInt(), "check _returned value to be 5");

        assertNotNull(json.get("_links"), "check not null _link");
        assertTrue((json.get("_links") instanceof JsonObject), "check _link to be a json object");

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull(links.get("self"), "check not null self");
        assertNotNull(links.get("rh:coll"), "check not null rh:coll");

        assertTrue((json.get("_embedded") instanceof JsonObject), "check _embedded to be a json object");

        JsonObject embedded = (JsonObject) json.get("_embedded");

        assertNotNull(embedded.get("rh:index"), "check not null _embedded.rh:index");

        assertTrue((embedded.get("rh:index") instanceof JsonArray),
                "check _embedded.rh:index to be a json array");

        JsonArray rhindex = (JsonArray) embedded.get("rh:index");

        assertNotNull(rhindex.get(0), "check not null _embedded.rh:index[0]");

        assertTrue((rhindex.get(0) instanceof JsonObject), "check _embedded.rh:index[0] to be a json object");

        JsonObject rhindex0 = (JsonObject) rhindex.get(0);

        assertNotNull(rhindex0.get("_id"), "check not null _embedded.rh:index[0]._id");
        assertNotNull(rhindex0.get("key"), "check not null _embedded.rh:index[0].key");
    }

    private final String DB = TEST_DB_PREFIX + "-indexes-db";
    private final String COLL = "coll";

    /**
     *
     * @throws Exception
     */
    @BeforeEach
    @SuppressWarnings("rawtypes")
    public void createTestData() throws Exception {
        // create test db
        kong.unirest.HttpResponse resp = Unirest.put(url(DB))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(), "create db " + DB);

        // create collection
        resp = Unirest.put(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(),
                "create collection " + DB.concat("/").concat(COLL));

        // create indexes
        resp = Unirest.put(url(DB, COLL, "_indexes", TEST_DB_PREFIX + "_idx_pos"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'keys': {'a':1}}")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(),
                "create index " + DB.concat("/").concat(COLL).concat("/_indexes/test_idx_pos"));

        resp = Unirest.put(url(DB, COLL, "_indexes", TEST_DB_PREFIX + "_idx_neg"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'keys':{'a':-1}}")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(),
                "create index " + DB.concat("/").concat(COLL).concat("/_indexes/test_idx_neg"));

        // create docs
        resp = Unirest.put(url(DB, COLL, "one"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .queryString("wm", "upsert")
                .body("{'a':1}")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(), "create doc one");

        resp = Unirest.put(url(DB, COLL, "two"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .queryString("wm", "upsert")
                .body("{'a':2}")
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(), "create doc two");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("rawtypes")
    public void testGetHintStringFormat() throws Exception {
        kong.unirest.HttpResponse resp = Unirest.get(url(DB, COLL))
                .queryString("hint", "+a")
                .queryString("sort", "{}")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_OK, resp.getStatus(), "test get collection");

        JsonValue _rbody = Json.parse(resp.getBody().toString());

        assertTrue(_rbody != null && _rbody.isObject(), "response body is a document");

        JsonObject rbody = _rbody.asObject();

        assertNotNull(rbody.get("_embedded"), "check not null _embedded");

        assertTrue((rbody.get("_embedded") instanceof JsonObject), "check _embedded to be a json object");

        JsonObject embedded = (JsonObject) rbody.get("_embedded");

        assertNotNull(embedded.get("rh:doc"), "check not null _embedded.rh:doc");

        assertTrue((embedded.get("rh:doc") instanceof JsonArray), "check _embedded.rh:doc to be a json array");

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull(rhdoc.get(0), "check not null _embedded.rh:doc[0]");

        assertTrue((rhdoc.get(0) instanceof JsonObject), "check _embedded.rh:coll[0] to be a json object");

        JsonObject doc = (JsonObject) rhdoc.get(0);

        JsonValue a = doc.get("a");

        assertTrue(a.isNumber(), "doc prop a is a number");
        assertTrue(a.asInt() == 1, "doc prop a equals 1");

        resp = Unirest.get(url(DB, COLL))
                .queryString("hint", "-a")
                .queryString("sort", "{}")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_OK, resp.getStatus(), "test get collection");

        _rbody = Json.parse(resp.getBody().toString());

        assertTrue(_rbody != null && _rbody.isObject(), "response body is a document");

        rbody = _rbody.asObject();

        assertNotNull(rbody.get("_embedded"), "check not null _embedded");

        assertTrue((rbody.get("_embedded") instanceof JsonObject), "check _embedded to be a json object");

        embedded = (JsonObject) rbody.get("_embedded");

        assertNotNull(embedded.get("rh:doc"), "check not null _embedded.rh:doc");

        assertTrue((embedded.get("rh:doc") instanceof JsonArray), "check _embedded.rh:doc to be a json array");

        rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull(rhdoc.get(0), "check not null _embedded.rh:doc[0]");

        assertTrue((rhdoc.get(0) instanceof JsonObject), "check _embedded.rh:coll[0] to be a json object");

        doc = (JsonObject) rhdoc.get(0);

        a = doc.get("a");

        assertTrue(a.isNumber(), "doc prop a is a number");
        assertTrue(a.asInt() == 2, "doc prop a equals 2");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("rawtypes")
    public void testGetHintObjectFormat() throws Exception {
        kong.unirest.HttpResponse resp = Unirest.get(url(DB, COLL))
                .queryString("hint", "{'a':1}")
                .queryString("sort", "{}")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_OK, resp.getStatus(), "test get collection");

        JsonValue _rbody = Json.parse(resp.getBody().toString());

        assertTrue(_rbody != null && _rbody.isObject(), "response body is a document");

        JsonObject rbody = _rbody.asObject();

        assertNotNull(rbody.get("_embedded"), "check not null _embedded");

        assertTrue((rbody.get("_embedded") instanceof JsonObject), "check _embedded to be a json object");

        JsonObject embedded = (JsonObject) rbody.get("_embedded");

        assertNotNull(embedded.get("rh:doc"), "check not null _embedded.rh:doc");

        assertTrue((embedded.get("rh:doc") instanceof JsonArray), "check _embedded.rh:doc to be a json array");

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull(rhdoc.get(0), "check not null _embedded.rh:doc[0]");

        assertTrue((rhdoc.get(0) instanceof JsonObject), "check _embedded.rh:coll[0] to be a json object");

        JsonObject doc = (JsonObject) rhdoc.get(0);

        JsonValue a = doc.get("a");

        assertTrue(a.isNumber(), "doc prop a is a number");
        assertTrue(a.asInt() == 1, "doc prop a equals 1");

        resp = Unirest.get(url(DB, COLL))
                .queryString("hint", "{'a':-1}")
                .queryString("sort", "{}")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_OK, resp.getStatus(), "test get collection");

        _rbody = Json.parse(resp.getBody().toString());

        assertTrue(_rbody != null && _rbody.isObject(), "response body is a document");

        rbody = _rbody.asObject();

        assertNotNull(rbody.get("_embedded"), "check not null _embedded");

        assertTrue((rbody.get("_embedded") instanceof JsonObject), "check _embedded to be a json object");

        embedded = (JsonObject) rbody.get("_embedded");

        assertNotNull(embedded.get("rh:doc"), "check not null _embedded.rh:doc");

        assertTrue((embedded.get("rh:doc") instanceof JsonArray), "check _embedded.rh:doc to be a json array");

        rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull(rhdoc.get(0), "check not null _embedded.rh:doc[0]");

        assertTrue((rhdoc.get(0) instanceof JsonObject), "check _embedded.rh:coll[0] to be a json object");

        doc = (JsonObject) rhdoc.get(0);

        a = doc.get("a");

        assertTrue(a.isNumber(), "doc prop a is a number");
        assertTrue(a.asInt() == 2, "doc prop a equals 2");
    }
}
