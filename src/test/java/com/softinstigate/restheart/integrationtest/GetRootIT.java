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
public class GetRootIT extends AbstactIT {
    public GetRootIT() {
    }

    @Test
    public void testGetRoot() throws Exception {
        testGetRoot(rootUri);
    }

    @Test
    public void testGetRootRemappedAll() throws Exception {
        testGetRoot(rootUriRemapped);
    }

    private void testGetRoot(URI uri) throws Exception {
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
        Assert.assertNotNull("check not null _total_pages property", json.get("_total_pages"));
        Assert.assertNotNull("check not null _embedded", json.get("_embedded"));

        Assert.assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));

        JsonObject embedded = (JsonObject) json.get("_embedded");

        Assert.assertNotNull("check not null _embedded.rh:db", embedded.get("rh:db"));

        Assert.assertTrue("check _embedded to be a json array", (embedded.get("rh:db") instanceof JsonArray));

        JsonArray rhdb = (JsonArray) embedded.get("rh:db");

        Assert.assertNotNull("check not null _embedded.rh:db[0]", rhdb.get(0));

        Assert.assertTrue("check _embedded.rh:db[0] to be a json object", (rhdb.get(0) instanceof JsonObject));

        JsonObject rhdb0 = (JsonObject) rhdb.get(0);

        Assert.assertNotNull("check not null _embedded.rh:db[0]._id", rhdb0.get("_id"));

        Assert.assertNotNull("check not null _embedded.rh:db[0]._links", rhdb0.get("_links"));

        Assert.assertTrue("check _embedded.rh:db[0]._links  to be a json object", (rhdb0.get("_links") instanceof JsonObject));

        JsonObject rhdb0Links = (JsonObject) rhdb0.get("_links");

        Assert.assertNotNull("check not null _embedded.rh:db[0]._links.self", rhdb0Links.get("self"));

        Assert.assertTrue("check _embedded.rh:db[0]._links.self  to be a json object", (rhdb0Links.get("self") instanceof JsonObject));

        JsonObject rhdb0LinksSelf = (JsonObject) rhdb0Links.get("self");

        Assert.assertNotNull("check not null _embedded.rh:db[0]._links.self.href", rhdb0LinksSelf.get("href"));

        Assert.assertTrue("check _embedded.rh:db[0]._links.self.href to be a string", (rhdb0LinksSelf.get("href").isString()));

        try {
            URI _uri = new URI(rhdb0LinksSelf.get("href").asString());
        }
        catch (URISyntaxException use) {
            Assert.fail("check _embedded.rh:db[0]._links.self.href to be a valid URI");
        }

        Assert.assertNotNull("check not null _link", json.get("_links"));
        Assert.assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));

        JsonObject links = (JsonObject) json.get("_links");

        Assert.assertNotNull("check not null self", links.get("self"));
        Assert.assertNotNull("check not null rh:paging", links.get("rh:paging"));
        Assert.assertNotNull("check not null curies", links.get("curies"));
    }
}
