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
public class GetRootIT extends HttpClientAbstactIT {

    /**
     *
     */
    public GetRootIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetRoot() throws Exception {
        testGetRoot(rootUri);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetRootRemappedAll() throws Exception {
        testGetRoot(rootUriRemapped);
    }

    private void testGetRoot(URI uri) throws Exception {
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
        assertNotNull("check not null _total_pages property", json.get("_total_pages"));
        assertNotNull("check not null _embedded", json.get("_embedded"));

        assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) json.get("_embedded");

        assertNotNull("check not null _embedded.rh:db", embedded.get("rh:db"));

        assertTrue("check _embedded to be a json array", (embedded.get("rh:db") instanceof JsonArray));

        JsonArray rhdb = (JsonArray) embedded.get("rh:db");

        assertNotNull("check not null _embedded.rh:db[0]", rhdb.get(0));

        assertTrue("check _embedded.rh:db[0] to be a json object", (rhdb.get(0) instanceof JsonObject));

        JsonObject rhdb0 = (JsonObject) rhdb.get(0);

        assertNotNull("check not null _embedded.rh:db[0]._id", rhdb0.get("_id"));

        assertNotNull("check not null _embedded.rh:db[0]._links", rhdb0.get("_links"));

        assertTrue("check _embedded.rh:db[0]._links  to be a json object", (rhdb0.get("_links") instanceof JsonObject));

        JsonObject rhdb0Links = (JsonObject) rhdb0.get("_links");

        assertNotNull("check not null _embedded.rh:db[0]._links.self", rhdb0Links.get("self"));

        assertTrue("check _embedded.rh:db[0]._links.self  to be a json object", (rhdb0Links.get("self") instanceof JsonObject));

        JsonObject rhdb0LinksSelf = (JsonObject) rhdb0Links.get("self");

        assertNotNull("check not null _embedded.rh:db[0]._links.self.href", rhdb0LinksSelf.get("href"));

        assertTrue("check _embedded.rh:db[0]._links.self.href to be a string", (rhdb0LinksSelf.get("href").isString()));

        try {
            new URI(rhdb0LinksSelf.get("href").asString());
        } catch (URISyntaxException use) {
            fail("check _embedded.rh:db[0]._links.self.href to be a valid URI");
        }

        assertNotNull("check not null _link", json.get("_links"));
        assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        JsonObject links = (JsonObject) json.get("_links");

        assertNotNull("check not null self", links.get("self"));
        assertNotNull("check not null rh:paging", links.get("rh:paging"));
    }
}
