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
package org.restheart.handlers.files;

import com.eclipsesource.json.JsonObject;
import com.mongodb.DB;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import io.undertow.util.Headers;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.restheart.test.integration.AbstactIT;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.restheart.hal.Representation;
import org.restheart.utils.HttpStatus;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class GetFileHandlerIT extends AbstactIT {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public static final String FILENAME = "RESTHeart_documentation.pdf";
    public static final String BUCKET = "mybucket";
    public static Object ID = "myfile";

    public GetFileHandlerIT() {
    }

    @Test
    public void testGetFile() throws Exception {
        String url = dbTmpUri + "/" + BUCKET + ".files/" + ID + "/binary";
        Response resp = adminExecutor.execute(Request.Get(url));
        
        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        
        File tempFile = tempFolder.newFile(FILENAME);
        
        FileOutputStream fos = new FileOutputStream(tempFile);
        
        entity.writeTo(fos);
        assertTrue(tempFile.length() > 0);
    }

    @Test
    public void testGetBucket() throws Exception {
        // test that GET /db includes the rh:bucket array
        Response resp = adminExecutor.execute(Request.Get(dbTmpUri));

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

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull(json.get("_returned"));
        assertTrue(json.get("_returned").isNumber());
        assertTrue(json.getInt("_returned", 0) > 0);

        assertNotNull(json.get("_embedded"));
        assertTrue(json.get("_embedded").isObject());

        assertNotNull(json.get("_embedded").asObject().get("rh:bucket"));
        assertTrue(json.get("_embedded").asObject().get("rh:bucket").isArray());

        assertTrue(!json.get("_embedded").asObject().get("rh:bucket").asArray().isEmpty());

        // test that GET /db/bucket.files includes the file
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files";
        resp = adminExecutor.execute(Request.Get(bucketUrl));

        httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        entity = httpResp.getEntity();
        assertNotNull(entity);
        statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        content = EntityUtils.toString(entity);

        json = null;

        try {
            json = JsonObject.readFrom(content);
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull(json.get("_returned"));
        assertTrue(json.get("_returned").isNumber());
        assertTrue(json.getInt("_returned", 0) > 0);

        assertNotNull(json.get("_embedded"));
        assertTrue(json.get("_embedded").isObject());

        assertNotNull(json.get("_embedded").asObject().get("rh:file"));
        assertTrue(json.get("_embedded").asObject().get("rh:file").isArray());
    }

    @Before
    public void createFile() throws UnknownHostException, IOException {
        // create db
        Response resp = adminExecutor.execute(Request.Put(dbTmpUri)
                .addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        StatusLine statusLine = httpResp.getStatusLine();
        
        assertNotNull(statusLine);
        assertEquals("check status code", HttpStatus.SC_CREATED, statusLine.getStatusCode());
        
        // create bucket
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files/";
        resp = adminExecutor.execute(Request.Put(bucketUrl)
                .addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

        httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        statusLine = httpResp.getStatusLine();
        
        assertNotNull(statusLine);
        assertEquals("check status code", HttpStatus.SC_CREATED, statusLine.getStatusCode());

        InputStream is = GetFileHandlerIT.class.getResourceAsStream("/" + FILENAME);

        HttpEntity entity = MultipartEntityBuilder
                .create()
                .addBinaryBody("file", is, ContentType.create("application/octet-stream"), FILENAME)
                .addTextBody("properties", "{\"_id\": \"" + ID + "\"}")
                .build();

        resp = adminExecutor.execute(Request.Post(bucketUrl)
                .body(entity));

        httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        statusLine = httpResp.getStatusLine();
        
        assertNotNull(statusLine);
        assertEquals("check status code", HttpStatus.SC_CREATED, statusLine.getStatusCode());
    }
}
