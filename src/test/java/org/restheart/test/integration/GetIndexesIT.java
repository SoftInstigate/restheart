/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
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
package org.restheart.test.integration;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import org.restheart.hal.Representation;
import org.restheart.utils.HttpStatus;
import java.net.URI;
import static org.junit.Assert.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetIndexesIT extends HttpClientAbstactIT {

    public GetIndexesIT() {
    }

    @Test
    public void testGetIndexes() throws Exception {
        testGetIndexes(indexesUri);
    }

    @Test
    public void testGetIndexesRemappedAll() throws Exception {
        testGetIndexes(indexesUriRemappedAll);
    }

    @Test
    public void testGetIndexesRemappedDb() throws Exception {
        testGetIndexes(indexesUriRemappedDb);
    }

    private void testGetIndexes(URI uri) throws Exception {
        Response resp = adminExecutor.execute(Request.Get(uri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
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
        assertNotNull("check not null curies", links.get("curies"));

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
}
