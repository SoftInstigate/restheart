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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.mongodb.RHMongoClients;
import org.restheart.utils.HttpStatus;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import kong.unirest.Unirest;
import com.mongodb.client.MongoCollection;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetCollectionIT extends HttpClientAbstactIT {

    /**
     *
     */
    public GetCollectionIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetCollection() throws Exception {
        testGetCollection(collection1Uri);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetCollectionRemappedAll() throws Exception {
        testGetCollection(collection1UriRemappedAll);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetCollectionRemappedDb() throws Exception {
        testGetCollection(collection1UriRemappedDb);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetCollectionRemappedCollection() throws Exception {
        testGetCollection(collection1UriRemappedCollection);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetCollectionCountAndPaging() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(docsCollectionUriCountAndPaging));

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
            fail("@@@ Failed parsing received json");
        }

        if (json == null) {
            fail("@@@ Failed parsing received json");
            json = new JsonObject(); // just to remove complier warning message (json might be null)
        }

        assertNotNull(json.get("_links"), "check not null _link");
        assertTrue((json.get("_links") instanceof JsonObject), "check _link to be a json object");

        assertEquals(2, json.get("_returned").asInt(), "check _returned value to be 2");
        assertEquals(10, json.get("_size").asInt(), "check _size value to be 10");
        assertEquals(5, json.get("_total_pages").asInt(), "check _total_pages value to be 5");

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull(links.get("self"), "check not null self");
        assertNotNull(links.get("rh:db"), "check not null rh:db");
        assertNotNull(links.get("rh:paging"), "check not null rh:paging");
        assertNotNull(links.get("next"), "check not null next");
        assertNotNull(links.get("first"), "check not null first");
        assertNotNull(links.get("last"), "check not null last");
        assertNotNull(links.get("previous"), "check not null previous");

        Response respSelf = adminExecutor.execute(Request
                .Get(docsCollectionUriCountAndPaging.resolve(links.get("self").asObject().get("href").asString())));
        HttpResponse httpRespSelf = respSelf.returnResponse();
        assertNotNull(httpRespSelf, "check not null get self response");
        assertEquals(HttpStatus.SC_OK,
                httpRespSelf.getStatusLine().getStatusCode(), "check get self response status code");

        Response respRhdb = adminExecutor.execute(Request
                .Get(docsCollectionUriCountAndPaging.resolve(links.get("rh:db").asObject().get("href").asString())));
        HttpResponse httpRespRhdb = respRhdb.returnResponse();
        assertNotNull(httpRespRhdb, "check not null rh:doc self response");
        assertEquals(HttpStatus.SC_OK,
                httpRespRhdb.getStatusLine().getStatusCode(), "check get rh:doc response status code");

        Response respNext = adminExecutor.execute(Request
                .Get(docsCollectionUriCountAndPaging.resolve(links.get("next").asObject().get("href").asString())));
        HttpResponse httpRespNext = respNext.returnResponse();
        assertNotNull(httpRespNext, "check not null get self response");
        assertEquals(HttpStatus.SC_OK,
                httpRespSelf.getStatusLine().getStatusCode(), "check get self response status code");

        Response respPrevious = adminExecutor.execute(Request
                .Get(docsCollectionUriCountAndPaging.resolve(links.get("previous").asObject().get("href").asString())));
        HttpResponse httpRespPrevious = respPrevious.returnResponse();
        assertNotNull(httpRespPrevious, "check not null get previous response");
        assertEquals(HttpStatus.SC_OK,
                httpRespSelf.getStatusLine().getStatusCode(), "check get self previous status code");

        Response respFirst = adminExecutor
                .execute(Request.Get(dbUriPaging.resolve(links.get("first").asObject().get("href").asString())));
        HttpResponse respRespFirst = respFirst.returnResponse();
        assertNotNull(respRespFirst, "check not null get first response");
        assertEquals(HttpStatus.SC_OK,
                respRespFirst.getStatusLine().getStatusCode(), "check get self first status code");

        Response respLast = adminExecutor
                .execute(Request.Get(dbUriPaging.resolve(links.get("last").asObject().get("href").asString())));
        HttpResponse httpRespLast = respLast.returnResponse();
        assertNotNull(httpRespLast, "check not null get last response");
        assertEquals(HttpStatus.SC_OK,
                httpRespLast.getStatusLine().getStatusCode(), "check get last response status code");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetCollectionPaging() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(docsCollectionUriPaging));

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
            fail("@@@ Failed parsing received json");
        }

        assertNotNull(json, "check json not null");

        assertNotNull(json.get("_links"), "check not null _link");
        assertTrue((json.get("_links") instanceof JsonObject), "check _link to be a json object");

        assertNotNull(json.get("_returned"), "check not null _returned property");
        assertNull(json.get("_size"), "check null _size");
        assertNull(json.get("_total_pages"), "check null _total_pages");

        assertEquals(2, json.get("_returned").asInt(), "check _returned value to be 2");

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull(links.get("self"), "check not null self");
        assertNotNull(links.get("rh:db"), "check not null rh:db");
        assertNotNull(links.get("rh:paging"), "check not null rh:paging");
        assertNotNull(links.get("rh:filter"), "check not null rh:filter");
        assertNotNull(links.get("rh:filter"), "check not null rh:sort");
        assertNotNull(links.get("next"), "check not null next");
        assertNotNull(links.get("first"), "check not null first");
        assertNull(links.get("last"), "check null last");
        assertNull(links.get("previous"), "check null previous");

        Response respSelf = adminExecutor.execute(
                Request.Get(docsCollectionUriPaging.resolve(links.get("self").asObject().get("href").asString())));
        HttpResponse httpRespSelf = respSelf.returnResponse();
        assertNotNull(httpRespSelf);

        Response respDb = adminExecutor.execute(
                Request.Get(docsCollectionUriPaging.resolve(links.get("rh:db").asObject().get("href").asString())));
        HttpResponse httpRespDb = respDb.returnResponse();
        assertNotNull(httpRespDb);

        Response respNext = adminExecutor.execute(
                Request.Get(docsCollectionUriPaging.resolve(links.get("next").asObject().get("href").asString())));
        HttpResponse httpRespNext = respNext.returnResponse();
        assertNotNull(httpRespNext);
    }

    private void testGetCollection(URI uri) throws Exception {
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

        assertNotNull(content);

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("@@@ Failed parsing received json");
        }

        assertNotNull(json, "check json not null");
        assertNotNull(json.get("_type"), "check not null _type property");
        assertNotNull(json.get("_etag"), "check not null _etag property");
        assertNotNull(json.get("_returned"), "check not null _returned property");
        assertNull(json.get("_size"), "check null _size property");
        assertNull(json.get("_total_pages"), "check null _total_pages property");

        assertNotNull(json.get("_embedded"), "check not null _embedded");

        assertTrue((json.get("_embedded") instanceof JsonObject), "check _embedded to be a json object");

        JsonObject embedded = (JsonObject) json.get("_embedded");

        assertNotNull(embedded.get("rh:doc"), "check not null _embedded.rh:doc");

        assertTrue((embedded.get("rh:doc") instanceof JsonArray), "check _embedded.rh:doc to be a json array");

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull(rhdoc.get(0), "check not null _embedded.rh:doc[0]");

        assertTrue((rhdoc.get(0) instanceof JsonObject), "check _embedded.rh:coll[0] to be a json object");

        JsonObject rhdoc0 = (JsonObject) rhdoc.get(0);

        assertNotNull(rhdoc0.get("_id"), "check not null _embedded.rh:doc[0]._id");

        assertNotNull(rhdoc0.get("_links"), "check not null _embedded.rh:doc[0]._links");

        assertTrue((rhdoc0.get("_links") instanceof JsonObject),
                "check _embedded.rh:doc[0]._links to be a json object");

        JsonObject rhdoc0Links = (JsonObject) rhdoc0.get("_links");

        assertNotNull(rhdoc0Links.get("self"), "check not null _embedded.rh:doc[0]._links.self");

        assertTrue((rhdoc0Links.get("self") instanceof JsonObject),
                "check _embedded.rh:doc[0]._links.self to be a json object");

        JsonObject rhdb0LinksSelf = (JsonObject) rhdoc0Links.get("self");

        assertNotNull(rhdb0LinksSelf.get("href"), "check not null _embedded.rh:doc[0]._links.self.href");

        assertTrue((rhdb0LinksSelf.get("href").isString()),
                "check _embedded.rh:doc[0]._links.self.href to be a string");

        try {
            new URI(rhdb0LinksSelf.get("href").asString());
        } catch (URISyntaxException use) {
            fail("check _embedded.rh:doc[0]._links.self.href to be a valid URI");
        }

        assertNotNull(json.get("_links"), "check not null _link");
        assertTrue((json.get("_links") instanceof JsonObject), "check _link to be a json object");

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull(links.get("self"), "check not null self");
        if (!uri.equals(collection1UriRemappedCollection)) {
            assertNotNull(links.get("rh:db"), "check not null rh:db");
        } else {
            assertNull(links.get("rh:db"), "check null rh:db");
        }

        assertNotNull(links.get("rh:paging"), "check not null rh:paging");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetCollectionSort() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(docsCollectionUriSort));

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
            fail("@@@ Failed parsing received json");
        }

        assertNotNull(json, "check json not null");

        assertNotNull(json.get("_embedded"), "check not null _embedded");

        assertTrue((json.get("_embedded") instanceof JsonObject), "check _embedded to be a json object");

        JsonObject embedded = (JsonObject) json.get("_embedded");

        assertNotNull(embedded.get("rh:doc"), "check not null _embedded.rh:doc");

        assertTrue((embedded.get("rh:doc") instanceof JsonArray), "check _embedded.rh:doc to be a json array");

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull(rhdoc.get(0), "check not null _embedded.rh:doc[0]");

        assertTrue((rhdoc.get(0) instanceof JsonObject), "check _embedded.rh:coll[0] to be a json object");

        JsonObject rhdoc0 = (JsonObject) rhdoc.get(0);

        assertNotNull(rhdoc0.get("_id"), "check not null _embedded.rh:doc[0]._id");
        assertNotNull(rhdoc0.get("name"), "check not null _embedded.rh:doc[0].name");
        assertEquals("Morrissey", rhdoc0.get("name").asString(), "check not null _embedded.rh:doc[1].name");

        JsonObject rhdoc1 = (JsonObject) rhdoc.get(1);

        assertNotNull(rhdoc1.get("_id"), "check not null _embedded.rh:doc[1]._id");
        assertNotNull(rhdoc1.get("name"), "check not null _embedded.rh:doc[1].name");
        assertEquals("Ian", rhdoc1.get("name").asString(), "check not null _embedded.rh:doc[1].name");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetCollectionFilter() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(docsCollectionUriFilter));

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
            fail("@@@ Failed parsing received json");
        }

        assertNotNull(json, "check json not null");

        assertNotNull(json.get("_size"), "check _size not null");

        assertEquals(2, json.get("_size").asInt(), "check _size value to be 2");

        assertNotNull(json.get("_returned"), "check _returned not null");

        assertEquals(2, json.get("_returned").asInt(), "check _returned value to be 2");

        assertNotNull(json.get("_embedded"), "check not null _embedded");

        assertTrue((json.get("_embedded") instanceof JsonObject), "check _embedded to be a json object");

        JsonObject embedded = (JsonObject) json.get("_embedded");

        assertNotNull(embedded.get("rh:doc"), "check not null _embedded.rh:doc");

        assertTrue((embedded.get("rh:doc") instanceof JsonArray), "check _embedded.rh:doc to be a json array");

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull(rhdoc.get(0), "check not null _embedded.rh:doc[0]");

        assertTrue((rhdoc.get(0) instanceof JsonObject), "check _embedded.rh:coll[0] to be a json object");

        JsonObject rhdoc0 = (JsonObject) rhdoc.get(0);

        assertNotNull(rhdoc0.get("_id"), "check not null _embedded.rh:doc[0]._id");
        assertNotNull(rhdoc0.get("name"), "check not null _embedded.rh:doc[0].name");

        assertEquals("Mark", rhdoc0.get("name").asString(), "check _embedded.rh:doc[1].name value to be Mark");

        JsonObject rhdoc1 = (JsonObject) rhdoc.get(1);

        assertNotNull(rhdoc1.get("_id"), "check not null _embedded.rh:doc[1]._id");
        assertNotNull(rhdoc1.get("name"), "check not null _embedded.rh:doc[1].name");
        assertEquals("Nick", rhdoc1.get("name").asString(), "check _embedded.rh:doc[1].name value to be Nick");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testBinaryProperty() throws Exception {
        byte[] data = "DqnEq7hiWZ1jHoYf/YJpNHevlGrRmT5V9NGN7daoPYetiTvgeP4C9n4j8Gu5mduhEYzWDFK2a3gO+CvzrDgM3BBFG07fF6qabHXDsGTo92m93QohjGtqn8nkNP6KVnWIcbgBbw=="
                .getBytes();

        MongoCollection<Document> coll = RHMongoClients.mclient().getDatabase(dbName).getCollection(collection1Name);

        Document doc = new Document();

        doc.append("_id", "bin");
        doc.append("data", data);

        coll.insertOne(doc);

        URI documentUri = buildURI("/" + dbName + "/" + collection1Name + "/bin");

        String url = documentUri.toString();

        kong.unirest.HttpResponse<String> resp = Unirest.get(url)
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(200, resp.getStatus(), "get document with binary property");
    }
}
