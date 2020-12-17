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
import com.mashape.unirest.http.Unirest;
import com.mongodb.client.MongoCollection;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.bson.Document;
import static org.junit.Assert.*;
import org.junit.Test;
import org.restheart.exchange.Exchange;
import org.restheart.mongodb.db.MongoClientSingleton;
import org.restheart.utils.HttpStatus;

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

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Exchange.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("@@@ Failed parsing received json");
        }

        assertNotNull("check not null _link", json.get("_links"));
        assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        assertEquals("check _returned value to be 2", 2, json.get("_returned").asInt());
        assertEquals("check _size value to be 10", 10, json.get("_size").asInt());
        assertEquals("check _total_pages value to be 5", 5, json.get("_total_pages").asInt());

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull("check not null self", links.get("self"));
        assertNotNull("check not null rh:db", links.get("rh:db"));
        assertNotNull("check not null rh:paging", links.get("rh:paging"));
        assertNotNull("check not null next", links.get("next"));
        assertNotNull("check not null first", links.get("first"));
        assertNotNull("check not null last", links.get("last"));
        assertNotNull("check not null previous", links.get("previous"));

        Response respSelf = adminExecutor.execute(Request.Get(docsCollectionUriCountAndPaging.resolve(links.get("self").asObject().get("href").asString())));
        HttpResponse httpRespSelf = respSelf.returnResponse();
        assertNotNull("check not null get self response", httpRespSelf);
        assertEquals("check get self response status code", HttpStatus.SC_OK, httpRespSelf.getStatusLine().getStatusCode());

        Response respRhdb = adminExecutor.execute(Request.Get(docsCollectionUriCountAndPaging.resolve(links.get("rh:db").asObject().get("href").asString())));
        HttpResponse httpRespRhdb = respRhdb.returnResponse();
        assertNotNull("check not null rh:doc self response", httpRespRhdb);
        assertEquals("check get rh:doc response status code", HttpStatus.SC_OK, httpRespRhdb.getStatusLine().getStatusCode());

        Response respNext = adminExecutor.execute(Request.Get(docsCollectionUriCountAndPaging.resolve(links.get("next").asObject().get("href").asString())));
        HttpResponse httpRespNext = respNext.returnResponse();
        assertNotNull("check not null get self response", httpRespNext);
        assertEquals("check get self response status code", HttpStatus.SC_OK, httpRespSelf.getStatusLine().getStatusCode());

        Response respPrevious = adminExecutor.execute(Request.Get(docsCollectionUriCountAndPaging.resolve(links.get("previous").asObject().get("href").asString())));
        HttpResponse httpRespPrevious = respPrevious.returnResponse();
        assertNotNull("check not null get previous response", httpRespPrevious);
        assertEquals("check get self previous status code", HttpStatus.SC_OK, httpRespSelf.getStatusLine().getStatusCode());

        Response respFirst = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("first").asObject().get("href").asString())));
        HttpResponse respRespFirst = respFirst.returnResponse();
        assertNotNull("check not null get first response", respRespFirst);
        assertEquals("check get self first status code", HttpStatus.SC_OK, respRespFirst.getStatusLine().getStatusCode());

        Response respLast = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("last").asObject().get("href").asString())));
        HttpResponse httpRespLast = respLast.returnResponse();
        assertNotNull("check not null get last response", httpRespLast);
        assertEquals("check get last response status code", HttpStatus.SC_OK, httpRespLast.getStatusLine().getStatusCode());
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

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Exchange.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("@@@ Failed parsing received json");
        }

        assertNotNull("check json not null", json);

        assertNotNull("check not null _link", json.get("_links"));
        assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        assertNotNull("check not null _returned property", json.get("_returned"));
        assertNull("check null _size", json.get("_size"));
        assertNull("check null _total_pages", json.get("_total_pages"));

        assertEquals("check _returned value to be 2", 2, json.get("_returned").asInt());

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull("check not null self", links.get("self"));
        assertNotNull("check not null rh:db", links.get("rh:db"));
        assertNotNull("check not null rh:paging", links.get("rh:paging"));
        assertNotNull("check not null rh:filter", links.get("rh:filter"));
        assertNotNull("check not null rh:sort", links.get("rh:filter"));
        assertNotNull("check not null next", links.get("next"));
        assertNotNull("check not null first", links.get("first"));
        assertNull("check null last", links.get("last"));
        assertNull("check null previous", links.get("previous"));

        Response respSelf = adminExecutor.execute(Request.Get(docsCollectionUriPaging.resolve(links.get("self").asObject().get("href").asString())));
        HttpResponse httpRespSelf = respSelf.returnResponse();
        assertNotNull(httpRespSelf);

        Response respDb = adminExecutor.execute(Request.Get(docsCollectionUriPaging.resolve(links.get("rh:db").asObject().get("href").asString())));
        HttpResponse httpRespDb = respDb.returnResponse();
        assertNotNull(httpRespDb);

        Response respNext = adminExecutor.execute(Request.Get(docsCollectionUriPaging.resolve(links.get("next").asObject().get("href").asString())));
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

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Exchange.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull(content);

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("@@@ Failed parsing received json");
        }

        assertNotNull("check json not null", json);
        assertNotNull("check not null _type property", json.get("_type"));
        assertNotNull("check not null _etag property", json.get("_etag"));
        assertNotNull("check not null _returned property", json.get("_returned"));
        assertNull("check null _size property", json.get("_size"));
        assertNull("check null _total_pages property", json.get("_total_pages"));

        assertNotNull("check not null _embedded", json.get("_embedded"));

        assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) json.get("_embedded");

        assertNotNull("check not null _embedded.rh:doc", embedded.get("rh:doc"));

        assertTrue("check _embedded.rh:doc to be a json array", (embedded.get("rh:doc") instanceof JsonArray));

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull("check not null _embedded.rh:doc[0]", rhdoc.get(0));

        assertTrue("check _embedded.rh:coll[0] to be a json object", (rhdoc.get(0) instanceof JsonObject));

        JsonObject rhdoc0 = (JsonObject) rhdoc.get(0);

        assertNotNull("check not null _embedded.rh:doc[0]._id", rhdoc0.get("_id"));

        assertNotNull("check not null _embedded.rh:doc[0]._links", rhdoc0.get("_links"));

        assertTrue("check _embedded.rh:doc[0]._links to be a json object", (rhdoc0.get("_links") instanceof JsonObject));

        JsonObject rhdoc0Links = (JsonObject) rhdoc0.get("_links");

        assertNotNull("check not null _embedded.rh:doc[0]._links.self", rhdoc0Links.get("self"));

        assertTrue("check _embedded.rh:doc[0]._links.self to be a json object", (rhdoc0Links.get("self") instanceof JsonObject));

        JsonObject rhdb0LinksSelf = (JsonObject) rhdoc0Links.get("self");

        assertNotNull("check not null _embedded.rh:doc[0]._links.self.href", rhdb0LinksSelf.get("href"));

        assertTrue("check _embedded.rh:doc[0]._links.self.href to be a string", (rhdb0LinksSelf.get("href").isString()));

        try {
            new URI(rhdb0LinksSelf.get("href").asString());
        } catch (URISyntaxException use) {
            fail("check _embedded.rh:doc[0]._links.self.href to be a valid URI");
        }

        assertNotNull("check not null _link", json.get("_links"));
        assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull("check not null self", links.get("self"));
        if (!uri.equals(collection1UriRemappedCollection)) {
            assertNotNull("check not null rh:db", links.get("rh:db"));
        } else {
            assertNull("check null rh:db", links.get("rh:db"));
        }

        assertNotNull("check not null rh:paging", links.get("rh:paging"));
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

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Exchange.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("@@@ Failed parsing received json");
        }

        assertNotNull("check json not null", json);

        assertNotNull("check not null _embedded", json.get("_embedded"));

        assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) json.get("_embedded");

        assertNotNull("check not null _embedded.rh:doc", embedded.get("rh:doc"));

        assertTrue("check _embedded.rh:doc to be a json array", (embedded.get("rh:doc") instanceof JsonArray));

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull("check not null _embedded.rh:doc[0]", rhdoc.get(0));

        assertTrue("check _embedded.rh:coll[0] to be a json object", (rhdoc.get(0) instanceof JsonObject));

        JsonObject rhdoc0 = (JsonObject) rhdoc.get(0);

        assertNotNull("check not null _embedded.rh:doc[0]._id", rhdoc0.get("_id"));
        assertNotNull("check not null _embedded.rh:doc[0].name", rhdoc0.get("name"));
        assertEquals("check not null _embedded.rh:doc[1].name", "Morrissey", rhdoc0.get("name").asString());

        JsonObject rhdoc1 = (JsonObject) rhdoc.get(1);

        assertNotNull("check not null _embedded.rh:doc[1]._id", rhdoc1.get("_id"));
        assertNotNull("check not null _embedded.rh:doc[1].name", rhdoc1.get("name"));
        assertEquals("check not null _embedded.rh:doc[1].name", "Ian", rhdoc1.get("name").asString());
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

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Exchange.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("@@@ Failed parsing received json");
        }

        assertNotNull("check json not null", json);

        assertNotNull("check _size not null", json.get("_size"));

        assertEquals("check _size value to be 2", 2, json.get("_size").asInt());

        assertNotNull("check _returned not null", json.get("_returned"));

        assertEquals("check _returned value to be 2", 2, json.get("_returned").asInt());

        assertNotNull("check not null _embedded", json.get("_embedded"));

        assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) json.get("_embedded");

        assertNotNull("check not null _embedded.rh:doc", embedded.get("rh:doc"));

        assertTrue("check _embedded.rh:doc to be a json array", (embedded.get("rh:doc") instanceof JsonArray));

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        assertNotNull("check not null _embedded.rh:doc[0]", rhdoc.get(0));

        assertTrue("check _embedded.rh:coll[0] to be a json object", (rhdoc.get(0) instanceof JsonObject));

        JsonObject rhdoc0 = (JsonObject) rhdoc.get(0);

        assertNotNull("check not null _embedded.rh:doc[0]._id", rhdoc0.get("_id"));
        assertNotNull("check not null _embedded.rh:doc[0].name", rhdoc0.get("name"));
        assertEquals("check _embedded.rh:doc[1].name value to be Mark", "Mark", rhdoc0.get("name").asString());

        JsonObject rhdoc1 = (JsonObject) rhdoc.get(1);

        assertNotNull("check not null _embedded.rh:doc[1]._id", rhdoc1.get("_id"));
        assertNotNull("check not null _embedded.rh:doc[1].name", rhdoc1.get("name"));
        assertEquals("check _embedded.rh:doc[1].name value to be Nick", "Nick", rhdoc1.get("name").asString());
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testBinaryProperty() throws Exception {
        byte[] data = "DqnEq7hiWZ1jHoYf/YJpNHevlGrRmT5V9NGN7daoPYetiTvgeP4C9n4j8Gu5mduhEYzWDFK2a3gO+CvzrDgM3BBFG07fF6qabHXDsGTo92m93QohjGtqn8nkNP6KVnWIcbgBbw==".getBytes();

        MongoCollection<Document> coll = MongoClientSingleton.getInstance()
                .getClient().getDatabase(dbName).getCollection(collection1Name);

        Document doc = new Document();

        doc.append("_id", "bin");
        doc.append("data", data);

        coll.insertOne(doc);

        URI documentUri = buildURI("/" + dbName + "/" + collection1Name + "/bin");

        String url = documentUri.toString();

        com.mashape.unirest.http.HttpResponse<String> resp = Unirest.get(url)
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals("get document with binary property", 200, resp.getStatus());
    }
}
