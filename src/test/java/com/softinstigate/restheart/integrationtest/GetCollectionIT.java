/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.softinstigate.restheart.integrationtest;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.softinstigate.restheart.hal.Representation;
import com.softinstigate.restheart.utils.HttpStatus;
import java.net.URI;
import java.net.URISyntaxException;
import junit.framework.Assert;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

/**
 *
 * @author Andrea Di Cesare
 */
public class GetCollectionIT extends AbstactIT {

    public GetCollectionIT() {
    }

    @Test
    public void testGetCollection() throws Exception {
        testGetCollection(collection1Uri);
    }

    @Test
    public void testGetCollectionRemappedAll() throws Exception {
        testGetCollection(collection1UriRemappedAll);
    }

    @Test
    public void testGetCollectionRemappedDb() throws Exception {
        testGetCollection(collection1UriRemappedDb);
    }

    @Test
    public void testGetCollectionRemappedCollection() throws Exception {
        testGetCollection(collection1UriRemappedCollection);
    }

    @Test
    public void testGetCollectionCountAndPaging() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(docsCollectionUriCountAndPaging));

        HttpResponse httpResp = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        Assert.assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);

        Assert.assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        Assert.assertNotNull("content type not null", entity.getContentType());
        Assert.assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        Assert.assertNotNull("", content);

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
        }
        catch (Throwable t) {
            Assert.fail("parsing received json");
        }

        Assert.assertNotNull("check json not null", json);

        Assert.assertNotNull("check not null _link", json.get("_links"));
        Assert.assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        Assert.assertNotNull("check not null _returned property", json.get("_returned"));
        Assert.assertNotNull("check not null _size value", json.get("_size"));
        Assert.assertNotNull("check not null _total_pages", json.get("_total_pages"));

        Assert.assertEquals("check _returned value to be 2", 2, json.get("_returned").asInt());
        Assert.assertEquals("check _size value to be 10", 10, json.get("_size").asInt());
        Assert.assertEquals("check _total_pages value to be 5", 5, json.get("_total_pages").asInt());

        JsonObject links = (JsonObject) json.get("_links");

        Assert.assertNotNull("check not null self", links.get("self"));
        Assert.assertNotNull("check not null rh:db", links.get("rh:db"));
        Assert.assertNotNull("check not null rh:paging", links.get("rh:paging"));
        Assert.assertNotNull("check not null next", links.get("next"));
        Assert.assertNotNull("check not null first", links.get("first"));
        Assert.assertNotNull("check not null last", links.get("last"));
        Assert.assertNotNull("check not null previous", links.get("previous"));

        Response respSelf = adminExecutor.execute(Request.Get(docsCollectionUriCountAndPaging.resolve(links.get("self").asObject().get("href").asString())));
        HttpResponse httpRespSelf = respSelf.returnResponse();
        Assert.assertNotNull("check not null get self response", httpRespSelf);
        Assert.assertEquals("check get self response status code", HttpStatus.SC_OK, httpRespSelf.getStatusLine().getStatusCode());

        Response respRhdb = adminExecutor.execute(Request.Get(docsCollectionUriCountAndPaging.resolve(links.get("rh:db").asObject().get("href").asString())));
        HttpResponse httpRespRhdb = respRhdb.returnResponse();
        Assert.assertNotNull("check not null rh:doc self response", httpRespRhdb);
        Assert.assertEquals("check get rh:doc response status code", HttpStatus.SC_OK, httpRespRhdb.getStatusLine().getStatusCode());

        Response respNext = adminExecutor.execute(Request.Get(docsCollectionUriCountAndPaging.resolve(links.get("next").asObject().get("href").asString())));
        HttpResponse httpRespNext = respNext.returnResponse();
        Assert.assertNotNull("check not null get self response", httpRespNext);
        Assert.assertEquals("check get self response status code", HttpStatus.SC_OK, httpRespSelf.getStatusLine().getStatusCode());

        Response respPrevious = adminExecutor.execute(Request.Get(docsCollectionUriCountAndPaging.resolve(links.get("previous").asObject().get("href").asString())));
        HttpResponse httpRespPrevious = respPrevious.returnResponse();
        Assert.assertNotNull("check not null get previous response", httpRespPrevious);
        Assert.assertEquals("check get self previous status code", HttpStatus.SC_OK, httpRespSelf.getStatusLine().getStatusCode());

        Response respFirst = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("first").asObject().get("href").asString())));
        HttpResponse respRespFirst = respFirst.returnResponse();
        Assert.assertNotNull("check not null get first response", respRespFirst);
        Assert.assertEquals("check get self first status code", HttpStatus.SC_OK, respRespFirst.getStatusLine().getStatusCode());

        Response respLast = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("last").asObject().get("href").asString())));
        HttpResponse httpRespLast = respLast.returnResponse();
        Assert.assertNotNull("check not null get last response", httpRespLast);
        Assert.assertEquals("check get last response status code", HttpStatus.SC_OK, httpRespLast.getStatusLine().getStatusCode());
    }

    @Test
    public void testGetCollectionPaging() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(docsCollectionUriPaging));

        HttpResponse httpResp = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        Assert.assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);

        Assert.assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        Assert.assertNotNull("content type not null", entity.getContentType());
        Assert.assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        Assert.assertNotNull("", content);

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
        }
        catch (Throwable t) {
            Assert.fail("parsing received json");
        }

        Assert.assertNotNull("check json not null", json);

        Assert.assertNotNull("check not null _link", json.get("_links"));
        Assert.assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        Assert.assertNotNull("check not null _returned property", json.get("_returned"));
        Assert.assertNull("check null _size", json.get("_size"));
        Assert.assertNull("check null _total_pages", json.get("_total_pages"));

        Assert.assertEquals("check _returned value to be 2", 2, json.get("_returned").asInt());

        JsonObject links = (JsonObject) json.get("_links");

        Assert.assertNotNull("check not null self", links.get("self"));
        Assert.assertNotNull("check not null rh:db", links.get("rh:db"));
        Assert.assertNotNull("check not null rh:paging", links.get("rh:paging"));
        Assert.assertNotNull("check not null rh:countandpaging", links.get("rh:countandpaging"));
        Assert.assertNotNull("check not null rh:filter", links.get("rh:filter"));
        Assert.assertNotNull("check not null rh:sort", links.get("rh:filter"));
        Assert.assertNotNull("check not null next", links.get("next"));
        Assert.assertNotNull("check not null first", links.get("first"));
        Assert.assertNull("check null last", links.get("last"));
        Assert.assertNull("check null previous", links.get("previous"));

        Response respSelf = adminExecutor.execute(Request.Get(docsCollectionUriPaging.resolve(links.get("self").asObject().get("href").asString())));
        HttpResponse httpRespSelf = respSelf.returnResponse();
        Assert.assertNotNull(httpRespSelf);

        Response respDb = adminExecutor.execute(Request.Get(docsCollectionUriPaging.resolve(links.get("rh:db").asObject().get("href").asString())));
        HttpResponse httpRespDb = respDb.returnResponse();
        Assert.assertNotNull(httpRespDb);

        Response respNext = adminExecutor.execute(Request.Get(docsCollectionUriPaging.resolve(links.get("next").asObject().get("href").asString())));
        HttpResponse httpRespNext = respNext.returnResponse();
        Assert.assertNotNull(httpRespNext);
    }

    private void testGetCollection(URI uri) throws Exception {
        Response resp = adminExecutor.execute(Request.Get(uri));

        HttpResponse httpResp = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        Assert.assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);

        Assert.assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        Assert.assertNotNull("content type not null", entity.getContentType());
        Assert.assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        Assert.assertNotNull("", content);

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
        }
        catch (Throwable t) {
            Assert.fail("parsing received json");
        }

        Assert.assertNotNull("check json not null", json);
        Assert.assertNotNull("check not null _created_on property", json.get("_type"));
        Assert.assertNotNull("check not null _created_on property", json.get("_created_on"));
        Assert.assertNotNull("check not null _etag property", json.get("_etag"));
        Assert.assertNotNull("check not null _lastupdated_on property", json.get("_lastupdated_on"));
        Assert.assertNotNull("check not null _collection-props-cached property", json.get("_collection-props-cached"));
        Assert.assertNotNull("check not null _returned property", json.get("_returned"));
        Assert.assertNull("check null _size property", json.get("_size"));
        Assert.assertNull("check null _total_pages property", json.get("_total_pages"));

        Assert.assertNotNull("check not null _embedded", json.get("_embedded"));

        Assert.assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) json.get("_embedded");

        Assert.assertNotNull("check not null _embedded.rh:doc", embedded.get("rh:doc"));

        Assert.assertTrue("check _embedded.rh:doc to be a json array", (embedded.get("rh:doc") instanceof JsonArray));

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        Assert.assertNotNull("check not null _embedded.rh:doc[0]", rhdoc.get(0));

        Assert.assertTrue("check _embedded.rh:coll[0] to be a json object", (rhdoc.get(0) instanceof JsonObject));

        JsonObject rhdoc0 = (JsonObject) rhdoc.get(0);

        Assert.assertNotNull("check not null _embedded.rh:doc[0]._id", rhdoc0.get("_id"));

        Assert.assertNotNull("check not null _embedded.rh:doc[0]._links", rhdoc0.get("_links"));

        Assert.assertTrue("check _embedded.rh:doc[0]._links to be a json object", (rhdoc0.get("_links") instanceof JsonObject));

        JsonObject rhdoc0Links = (JsonObject) rhdoc0.get("_links");

        Assert.assertNotNull("check not null _embedded.rh:doc[0]._links.self", rhdoc0Links.get("self"));

        Assert.assertTrue("check _embedded.rh:doc[0]._links.self to be a json object", (rhdoc0Links.get("self") instanceof JsonObject));

        JsonObject rhdb0LinksSelf = (JsonObject) rhdoc0Links.get("self");

        Assert.assertNotNull("check not null _embedded.rh:doc[0]._links.self.href", rhdb0LinksSelf.get("href"));

        Assert.assertTrue("check _embedded.rh:doc[0]._links.self.href to be a string", (rhdb0LinksSelf.get("href").isString()));

        try {
            URI _uri = new URI(rhdb0LinksSelf.get("href").asString());
        }
        catch (URISyntaxException use) {
            Assert.fail("check _embedded.rh:doc[0]._links.self.href to be a valid URI");
        }

        Assert.assertNotNull("check not null _link", json.get("_links"));
        Assert.assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        JsonObject links = (JsonObject) json.get("_links");

        Assert.assertNotNull("check not null self", links.get("self"));
        if (!uri.equals(collection1UriRemappedCollection)) {
            Assert.assertNotNull("check not null rh:db", links.get("rh:db"));
        } else {
            Assert.assertNull("check null rh:db", links.get("rh:db"));
        }
        
        Assert.assertNotNull("check not null rh:paging", links.get("rh:paging"));
        Assert.assertNotNull("check not null curies", links.get("curies"));
    }

    @Test
    public void testGetCollectionSort() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(docsCollectionUriSort));

        HttpResponse httpResp = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        Assert.assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);

        Assert.assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        Assert.assertNotNull("content type not null", entity.getContentType());
        Assert.assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        Assert.assertNotNull("", content);

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
        }
        catch (Throwable t) {
            Assert.fail("parsing received json");
        }

        Assert.assertNotNull("check json not null", json);

        Assert.assertNotNull("check not null _embedded", json.get("_embedded"));

        Assert.assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) json.get("_embedded");

        Assert.assertNotNull("check not null _embedded.rh:doc", embedded.get("rh:doc"));

        Assert.assertTrue("check _embedded.rh:doc to be a json array", (embedded.get("rh:doc") instanceof JsonArray));

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        Assert.assertNotNull("check not null _embedded.rh:doc[0]", rhdoc.get(0));

        Assert.assertTrue("check _embedded.rh:coll[0] to be a json object", (rhdoc.get(0) instanceof JsonObject));

        JsonObject rhdoc0 = (JsonObject) rhdoc.get(0);

        Assert.assertNotNull("check not null _embedded.rh:doc[0]._id", rhdoc0.get("_id"));
        Assert.assertNotNull("check not null _embedded.rh:doc[0].name", rhdoc0.get("name"));
        Assert.assertEquals("check not null _embedded.rh:doc[1].name", "Morrissey", rhdoc0.get("name").asString());

        JsonObject rhdoc1 = (JsonObject) rhdoc.get(1);

        Assert.assertNotNull("check not null _embedded.rh:doc[1]._id", rhdoc1.get("_id"));
        Assert.assertNotNull("check not null _embedded.rh:doc[1].name", rhdoc1.get("name"));
        Assert.assertEquals("check not null _embedded.rh:doc[1].name", "Ian", rhdoc1.get("name").asString());
    }

    @Test
    public void testGetCollectionFilter() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(docsCollectionUriFilter));

        HttpResponse httpResp = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        Assert.assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);

        Assert.assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        Assert.assertNotNull("content type not null", entity.getContentType());
        Assert.assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        Assert.assertNotNull("", content);

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
        }
        catch (Throwable t) {
            Assert.fail("parsing received json");
        }

        Assert.assertNotNull("check json not null", json);

        Assert.assertNotNull("check _size not null", json.get("_size"));

        Assert.assertEquals("check _size value to be 2", 2, json.get("_size").asInt());

        Assert.assertNotNull("check _returned not null", json.get("_returned"));

        Assert.assertEquals("check _returned value to be 2", 2, json.get("_returned").asInt());

        Assert.assertNotNull("check not null _embedded", json.get("_embedded"));

        Assert.assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) json.get("_embedded");

        Assert.assertNotNull("check not null _embedded.rh:doc", embedded.get("rh:doc"));

        Assert.assertTrue("check _embedded.rh:doc to be a json array", (embedded.get("rh:doc") instanceof JsonArray));

        JsonArray rhdoc = (JsonArray) embedded.get("rh:doc");

        Assert.assertNotNull("check not null _embedded.rh:doc[0]", rhdoc.get(0));

        Assert.assertTrue("check _embedded.rh:coll[0] to be a json object", (rhdoc.get(0) instanceof JsonObject));

        JsonObject rhdoc0 = (JsonObject) rhdoc.get(0);

        Assert.assertNotNull("check not null _embedded.rh:doc[0]._id", rhdoc0.get("_id"));
        Assert.assertNotNull("check not null _embedded.rh:doc[0].name", rhdoc0.get("name"));
        Assert.assertEquals("check _embedded.rh:doc[1].name value to be Mark", "Mark", rhdoc0.get("name").asString());

        JsonObject rhdoc1 = (JsonObject) rhdoc.get(1);

        Assert.assertNotNull("check not null _embedded.rh:doc[1]._id", rhdoc1.get("_id"));
        Assert.assertNotNull("check not null _embedded.rh:doc[1].name", rhdoc1.get("name"));
        Assert.assertEquals("check _embedded.rh:doc[1].name value to be Nick", "Nick", rhdoc1.get("name").asString());
    }
}
