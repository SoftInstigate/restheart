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
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.mashape.unirest.http.Unirest;
import java.net.URI;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
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
public class GetIndexesIT extends HttpClientAbstactIT {

    /**
     *
     */
    public GetIndexesIT() {
    }

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

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Exchange.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull("check json not null", json);
        assertNotNull("check not null _returned property", json.get("_returned"));
        assertNotNull("check not null _size property", json.get("_size"));
        assertEquals("check _size value to be 5", 5, json.get("_size").asInt());
        assertEquals("check _returned value to be 5", 5, json.get("_returned").asInt());

        assertNotNull("check not null _link", json.get("_links"));
        assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull("check not null self", links.get("self"));
        assertNotNull("check not null rh:coll", links.get("rh:coll"));

        assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) json.get("_embedded");

        assertNotNull("check not null _embedded.rh:index", embedded.get("rh:index"));

        assertTrue("check _embedded.rh:index to be a json array", (embedded.get("rh:index") instanceof JsonArray));

        JsonArray rhindex = (JsonArray) embedded.get("rh:index");

        assertNotNull("check not null _embedded.rh:index[0]", rhindex.get(0));

        assertTrue("check _embedded.rh:index[0] to be a json object", (rhindex.get(0) instanceof JsonObject));

        JsonObject rhindex0 = (JsonObject) rhindex.get(0);

        assertNotNull("check not null _embedded.rh:index[0]._id", rhindex0.get("_id"));
        assertNotNull("check not null _embedded.rh:index[0].key", rhindex0.get("key"));
    }

    private final String DB = TEST_DB_PREFIX + "-indexes-db";
    private final String COLL = "coll";

    /**
     *
     * @throws Exception
     */
    @Before
    @SuppressWarnings("rawtypes")
    public void createTestData() throws Exception {
        // create test db
        com.mashape.unirest.http.HttpResponse resp = Unirest.put(url(DB))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("create db " + DB, org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        // create collection
        resp = Unirest.put(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("create collection " + DB.concat("/").concat(COLL), org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        // create indexes
        resp = Unirest.put(url(DB, COLL, "_indexes", TEST_DB_PREFIX + "_idx_pos"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'keys': {'a':1}}")
                .asString();

        Assert.assertEquals("create index " + DB.concat("/").concat(COLL).concat("/_indexes/test_idx_pos"), org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.put(url(DB, COLL, "_indexes", TEST_DB_PREFIX + "_idx_neg"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'keys':{'a':-1}}")
                .asString();

        Assert.assertEquals("create index " + DB.concat("/").concat(COLL).concat("/_indexes/test_idx_neg"), org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        // create docs
        resp = Unirest.put(url(DB, COLL, "one"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'a':1}")
                .asString();

        Assert.assertEquals("create doc one", org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.put(url(DB, COLL, "two"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body("{'a':2}")
                .asString();

        Assert.assertEquals("create doc two", org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());
    }

    /**
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("rawtypes")
    public void testGetHintStringFormat() throws Exception {
        com.mashape.unirest.http.HttpResponse resp = Unirest.get(url(DB, COLL))
                .queryString("hint", "a")
                .queryString("sort", "{}")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("test get collection", org.apache.http.HttpStatus.SC_OK, resp.getStatus());

        JsonValue _rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("response body is a document", _rbody != null && _rbody.isObject());

        JsonObject rbody = _rbody.asObject();

        assertNotNull("check not null _embedded", rbody.get("_embedded"));

        assertTrue("check _embedded to be a json object", (rbody.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) rbody.get("_embedded");

        assertNotNull("check not null _embedded.rh:doc", embedded.get("rh:doc"));

        assertTrue("check _embedded.rh:doc to be a json array", (embedded.get("rh:doc") instanceof JsonArray));

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull("check not null _embedded.rh:doc[0]", rhdoc.get(0));

        assertTrue("check _embedded.rh:coll[0] to be a json object", (rhdoc.get(0) instanceof JsonObject));

        JsonObject doc = (JsonObject) rhdoc.get(0);

        JsonValue a = doc.get("a");

        Assert.assertTrue("doc prop a is a number", a.isNumber());
        Assert.assertTrue("doc prop a equals 1", a.asInt() == 1);

        resp = Unirest.get(url(DB, COLL))
                .queryString("hint", "-a")
                .queryString("sort", "{}")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("test get collection", org.apache.http.HttpStatus.SC_OK, resp.getStatus());

        _rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("response body is a document", _rbody != null && _rbody.isObject());

        rbody = _rbody.asObject();

        assertNotNull("check not null _embedded", rbody.get("_embedded"));

        assertTrue("check _embedded to be a json object", (rbody.get("_embedded") instanceof JsonObject));

        embedded = (JsonObject) rbody.get("_embedded");

        assertNotNull("check not null _embedded.rh:doc", embedded.get("rh:doc"));

        assertTrue("check _embedded.rh:doc to be a json array", (embedded.get("rh:doc") instanceof JsonArray));

        rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull("check not null _embedded.rh:doc[0]", rhdoc.get(0));

        assertTrue("check _embedded.rh:coll[0] to be a json object", (rhdoc.get(0) instanceof JsonObject));

        doc = (JsonObject) rhdoc.get(0);

        a = doc.get("a");

        Assert.assertTrue("doc prop a is a number", a.isNumber());
        Assert.assertTrue("doc prop a equals 2", a.asInt() == 2);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    @SuppressWarnings("rawtypes")
    public void testGetHintObjectFormat() throws Exception {
        com.mashape.unirest.http.HttpResponse resp = Unirest.get(url(DB, COLL))
                .queryString("hint", "{'a':1}")
                .queryString("sort", "{}")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("test get collection", org.apache.http.HttpStatus.SC_OK, resp.getStatus());

        JsonValue _rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("response body is a document", _rbody != null && _rbody.isObject());

        JsonObject rbody = _rbody.asObject();

        assertNotNull("check not null _embedded", rbody.get("_embedded"));

        assertTrue("check _embedded to be a json object", (rbody.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) rbody.get("_embedded");

        assertNotNull("check not null _embedded.rh:doc", embedded.get("rh:doc"));

        assertTrue("check _embedded.rh:doc to be a json array", (embedded.get("rh:doc") instanceof JsonArray));

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull("check not null _embedded.rh:doc[0]", rhdoc.get(0));

        assertTrue("check _embedded.rh:coll[0] to be a json object", (rhdoc.get(0) instanceof JsonObject));

        JsonObject doc = (JsonObject) rhdoc.get(0);

        JsonValue a = doc.get("a");

        Assert.assertTrue("doc prop a is a number", a.isNumber());
        Assert.assertTrue("doc prop a equals 1", a.asInt() == 1);

        resp = Unirest.get(url(DB, COLL))
                .queryString("hint", "{'a':-1}")
                .queryString("sort", "{}")
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("test get collection", org.apache.http.HttpStatus.SC_OK, resp.getStatus());

        _rbody = Json.parse(resp.getBody().toString());

        Assert.assertTrue("response body is a document", _rbody != null && _rbody.isObject());

        rbody = _rbody.asObject();

        assertNotNull("check not null _embedded", rbody.get("_embedded"));

        assertTrue("check _embedded to be a json object", (rbody.get("_embedded") instanceof JsonObject));

        embedded = (JsonObject) rbody.get("_embedded");

        assertNotNull("check not null _embedded.rh:doc", embedded.get("rh:doc"));

        assertTrue("check _embedded.rh:doc to be a json array", (embedded.get("rh:doc") instanceof JsonArray));

        rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull("check not null _embedded.rh:doc[0]", rhdoc.get(0));

        assertTrue("check _embedded.rh:coll[0] to be a json object", (rhdoc.get(0) instanceof JsonObject));

        doc = (JsonObject) rhdoc.get(0);

        a = doc.get("a");

        Assert.assertTrue("doc prop a is a number", a.isNumber());
        Assert.assertTrue("doc prop a equals 2", a.asInt() == 2);
    }
}
