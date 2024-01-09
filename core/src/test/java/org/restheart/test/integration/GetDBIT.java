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
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

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

        assertNotNull(json.get("_links"), "check not null _link");
        assertTrue((json.get("_links") instanceof JsonObject), "check _link to be a json object");

        assertNotNull(json.get("_returned"), "check not null _returned property");
        assertNotNull(json.get("_size"), "check not null _size value");
        assertNotNull(json.get("_total_pages"), "check not null _total_pages");

        assertEquals(1, json.get("_returned").asInt(), "check _returned value to be 1");
        assertEquals(3, json.get("_size").asInt(), "check _size value to be 3");
        assertEquals(3, json.get("_total_pages").asInt(), "check _total_pages value to be 2");

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull(links.get("self"), "check not null self");
        assertNotNull(links.get("rh:root"), "check not null rh:root");
        assertNotNull(links.get("rh:paging"), "check not null rh:paging");
        assertNotNull(links.get("next"), "check not null next");
        assertNotNull(links.get("first"), "check not null first");
        assertNotNull(links.get("last"), "check not null last");
        assertNull(links.get("previous"), "check null previous");

        Response respSelf = adminExecutor
                .execute(Request.Get(dbUriPaging.resolve(links.get("self").asObject().get("href").asString())));
        HttpResponse httpRespSelf = respSelf.returnResponse();
        assertNotNull(httpRespSelf);

        Response respRoot = adminExecutor
                .execute(Request.Get(dbUriPaging.resolve(links.get("rh:root").asObject().get("href").asString())));
        HttpResponse httpRespRoot = respRoot.returnResponse();
        assertNotNull(httpRespRoot);

        Response respNext = adminExecutor
                .execute(Request.Get(dbUriPaging.resolve(links.get("next").asObject().get("href").asString())));
        HttpResponse httpRespNext = respNext.returnResponse();
        assertNotNull(httpRespNext);

        Response respFirst = adminExecutor
                .execute(Request.Get(dbUriPaging.resolve(links.get("first").asObject().get("href").asString())));
        HttpResponse respRespFirst = respFirst.returnResponse();
        assertNotNull(respRespFirst);

        Response respLast = adminExecutor
                .execute(Request.Get(dbUriPaging.resolve(links.get("last").asObject().get("href").asString())));
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
        assertNotNull(json.get("_type"), "check not null _type property");
        assertNotNull(json.get("_etag"), "check not null _etag property");
        assertNotNull(json.get("_returned"), "check not null _returned property");
        assertNotNull(json.get("_size"), "check not null _size property");
        assertNotNull(json.get("_total_pages"), "check not null _total_pages property");

        assertNotNull(json.get("_embedded"), "check not null _embedded");

        assertTrue((json.get("_embedded") instanceof JsonObject), "check _embedded to be a json object");

        JsonObject embedded = (JsonObject) json.get("_embedded");

        assertNotNull(embedded.get("rh:coll"), "check not null _embedded.rh:coll");

        assertTrue((embedded.get("rh:coll") instanceof JsonArray), "check _embedded.rh:coll to be a json array");

        JsonArray rhcoll = (JsonArray) embedded.get("rh:coll");

        assertNotNull(rhcoll.get(0), "check not null _embedded.rh:coll[0]");

        assertTrue((rhcoll.get(0) instanceof JsonObject), "check _embedded.rh:coll[0] to be a json object");

        JsonObject rhcoll0 = (JsonObject) rhcoll.get(0);

        assertNotNull(rhcoll0.get("_id"), "check not null _embedded.rh:coll[0]._id");

        assertNotNull(rhcoll0.get("_links"), "check not null _embedded.rh:coll[0]._links");

        assertTrue((rhcoll0.get("_links") instanceof JsonObject),
                "check _embedded.rh:coll[0]._links to be a json object");

        JsonObject rhcoll0Links = (JsonObject) rhcoll0.get("_links");

        assertNotNull(rhcoll0Links.get("self"), "check not null _embedded.rh:coll[0]._links.self");

        assertTrue((rhcoll0Links.get("self") instanceof JsonObject),
                "check _embedded.rh:coll[0]._links.self  to be a json object");

        JsonObject rhdb0LinksSelf = (JsonObject) rhcoll0Links.get("self");

        assertNotNull(rhdb0LinksSelf.get("href"), "check not null _embedded.rh:coll[0]._links.self.href");

        assertTrue((rhdb0LinksSelf.get("href").isString()),
                "check _embedded.rh:coll[0]._links.self.href to be a string");

        try {
            new URI(rhdb0LinksSelf.get("href").asString());
        } catch (URISyntaxException use) {
            fail("check _embedded.rh:coll[0]._links.self.href to be a valid URI");
        }

        assertNotNull(json.get("_links"), "check not null _link");
        assertTrue((json.get("_links") instanceof JsonObject), "check _link to be a json object");

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull(links.get("self"), "check not null self");
        assertNotNull(links.get("rh:root"), "check not null rh:root");
        assertNotNull(links.get("rh:paging"), "check not null rh:paging");
    }
}
