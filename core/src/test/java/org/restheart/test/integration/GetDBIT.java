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
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import static org.junit.Assert.*;
import org.junit.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetDBIT extends HttpClientAbstactIT {

    /**
     *
     */
    public GetDBIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDB() throws Exception {
        testGetDb(dbUri);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDBRemappedAll() throws Exception {
        testGetDb(dbUriRemappedAll);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDBRemappedDb() throws Exception {
        testGetDb(dbUriRemappedDb);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDBPaging() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(dbUriPaging));

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

        assertNotNull("check not null _link", json.get("_links"));
        assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        assertNotNull("check not null _returned property", json.get("_returned"));
        assertNotNull("check not null _size value", json.get("_size"));
        assertNotNull("check not null _total_pages", json.get("_total_pages"));

        assertEquals("check _returned value to be 1", 1, json.get("_returned").asInt());
        assertEquals("check _size value to be 3", 3, json.get("_size").asInt());
        assertEquals("check _total_pages value to be 2", 3, json.get("_total_pages").asInt());

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull("check not null self", links.get("self"));
        assertNotNull("check not null rh:root", links.get("rh:root"));
        assertNotNull("check not null rh:paging", links.get("rh:paging"));
        assertNotNull("check not null next", links.get("next"));
        assertNotNull("check not null first", links.get("first"));
        assertNotNull("check not null last", links.get("last"));
        assertNull("check null previous", links.get("previous"));

        Response respSelf = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("self").asObject().get("href").asString())));
        HttpResponse httpRespSelf = respSelf.returnResponse();
        assertNotNull(httpRespSelf);

        Response respRoot = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("rh:root").asObject().get("href").asString())));
        HttpResponse httpRespRoot = respRoot.returnResponse();
        assertNotNull(httpRespRoot);

        Response respNext = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("next").asObject().get("href").asString())));
        HttpResponse httpRespNext = respNext.returnResponse();
        assertNotNull(httpRespNext);

        Response respFirst = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("first").asObject().get("href").asString())));
        HttpResponse respRespFirst = respFirst.returnResponse();
        assertNotNull(respRespFirst);

        Response respLast = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("last").asObject().get("href").asString())));
        HttpResponse httpRespLast = respLast.returnResponse();
        assertNotNull(httpRespLast);
    }

    private void testGetDb(URI uri) throws Exception {
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
        assertNotNull("check not null _type property", json.get("_type"));
        assertNotNull("check not null _etag property", json.get("_etag"));
        assertNotNull("check not null _returned property", json.get("_returned"));
        assertNotNull("check not null _size property", json.get("_size"));
        assertNotNull("check not null _total_pages property", json.get("_total_pages"));

        assertNotNull("check not null _embedded", json.get("_embedded"));

        assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) json.get("_embedded");

        assertNotNull("check not null _embedded.rh:coll", embedded.get("rh:coll"));

        assertTrue("check _embedded.rh:coll to be a json array", (embedded.get("rh:coll") instanceof JsonArray));

        JsonArray rhcoll = (JsonArray) embedded.get("rh:coll");

        assertNotNull("check not null _embedded.rh:coll[0]", rhcoll.get(0));

        assertTrue("check _embedded.rh:coll[0] to be a json object", (rhcoll.get(0) instanceof JsonObject));

        JsonObject rhcoll0 = (JsonObject) rhcoll.get(0);

        assertNotNull("check not null _embedded.rh:coll[0]._id", rhcoll0.get("_id"));

        assertNotNull("check not null _embedded.rh:coll[0]._links", rhcoll0.get("_links"));

        assertTrue("check _embedded.rh:coll[0]._links to be a json object", (rhcoll0.get("_links") instanceof JsonObject));

        JsonObject rhcoll0Links = (JsonObject) rhcoll0.get("_links");

        assertNotNull("check not null _embedded.rh:coll[0]._links.self", rhcoll0Links.get("self"));

        assertTrue("check _embedded.rh:coll[0]._links.self  to be a json object", (rhcoll0Links.get("self") instanceof JsonObject));

        JsonObject rhdb0LinksSelf = (JsonObject) rhcoll0Links.get("self");

        assertNotNull("check not null _embedded.rh:coll[0]._links.self.href", rhdb0LinksSelf.get("href"));

        assertTrue("check _embedded.rh:coll[0]._links.self.href to be a string", (rhdb0LinksSelf.get("href").isString()));

        try {
            new URI(rhdb0LinksSelf.get("href").asString());
        } catch (URISyntaxException use) {
            fail("check _embedded.rh:coll[0]._links.self.href to be a valid URI");
        }

        assertNotNull("check not null _link", json.get("_links"));
        assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull("check not null self", links.get("self"));
        assertNotNull("check not null rh:root", links.get("rh:root"));
        assertNotNull("check not null rh:paging", links.get("rh:paging"));
    }
}
