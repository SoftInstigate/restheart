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
public class GetIndexesIT extends AbstactIT {
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
        Assert.assertNotNull("check not null _returned property", json.get("_returned"));
        Assert.assertNotNull("check not null _size property", json.get("_size"));
        Assert.assertEquals("check _size value to be 8", 8, json.get("_size").asInt());
        Assert.assertEquals("check _returned value to be 8", 8, json.get("_returned").asInt());

        Assert.assertNotNull("check not null _link", json.get("_links"));
        Assert.assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        JsonObject links = (JsonObject) json.get("_links");

        Assert.assertNotNull("check not null self", links.get("self"));
        Assert.assertNotNull("check not null rh:coll", links.get("rh:coll"));
        Assert.assertNotNull("check not null curies", links.get("curies"));

        Assert.assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) json.get("_embedded");

        Assert.assertNotNull("check not null _embedded.rh:index", embedded.get("rh:index"));

        Assert.assertTrue("check _embedded.rh:index to be a json array", (embedded.get("rh:index") instanceof JsonArray));

        JsonArray rhindex = (JsonArray) embedded.get("rh:index");

        Assert.assertNotNull("check not null _embedded.rh:index[0]", rhindex.get(0));

        Assert.assertTrue("check _embedded.rh:index[0] to be a json object", (rhindex.get(0) instanceof JsonObject));

        JsonObject rhindex0 = (JsonObject) rhindex.get(0);

        Assert.assertNotNull("check not null _embedded.rh:index[0]._id", rhindex0.get("_id"));
        Assert.assertNotNull("check not null _embedded.rh:index[0].key", rhindex0.get("key"));
    }
}
