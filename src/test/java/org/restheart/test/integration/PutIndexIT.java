/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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

import com.eclipsesource.json.JsonObject;
import org.restheart.hal.Representation;
import static org.restheart.test.integration.AbstactIT.adminExecutor;
import org.restheart.utils.HttpStatus;
import io.undertow.util.Headers;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PutIndexIT extends AbstactIT {

    public PutIndexIT() {
    }

    @Test
    public void testPutDocument() throws Exception {
        try {
            Response resp;

            // *** PUT tmpdb
            resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check put db", resp, HttpStatus.SC_CREATED);

            // *** PUT tmpcoll
            resp = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check put coll1", resp, HttpStatus.SC_CREATED);

            // *** PUT wrong index
            //resp = adminExecutor.execute(Request.Put(indexTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            //check("check put wrong index", resp, HttpStatus.SC_NOT_ACCEPTABLE);
            // try to put without etag
            resp = adminExecutor.execute(Request.Put(indexTmpUri).bodyString("{ keys: {a:1,b:2}, ops: { name: \"ciao\"} }", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check put correct index", resp, HttpStatus.SC_CREATED);

            resp = adminExecutor.execute(Request.Get(indexesTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

            HttpResponse httpResp = resp.returnResponse();
            junit.framework.Assert.assertNotNull(httpResp);
            HttpEntity entity = httpResp.getEntity();
            junit.framework.Assert.assertNotNull(entity);
            StatusLine statusLine = httpResp.getStatusLine();
            junit.framework.Assert.assertNotNull(statusLine);

            junit.framework.Assert.assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
            junit.framework.Assert.assertNotNull("content type not null", entity.getContentType());
            junit.framework.Assert.assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

            String content = EntityUtils.toString(entity);

            junit.framework.Assert.assertNotNull("", content);

            JsonObject json = null;

            try {
                json = JsonObject.readFrom(content);
            } catch (Throwable t) {
                junit.framework.Assert.fail("parsing received json");
            }

            junit.framework.Assert.assertNotNull("check json not null", json);
            junit.framework.Assert.assertNotNull("check not null _returned property", json.get("_returned"));
            junit.framework.Assert.assertNotNull("check not null _size property", json.get("_size"));
            junit.framework.Assert.assertEquals("check _size value to be 5", 5, json.get("_size").asInt());
            junit.framework.Assert.assertEquals("check _returned value to be 5", 5, json.get("_returned").asInt());
        } finally {
            mongoClient.dropDatabase(dbTmpName);
        }
    }
}
