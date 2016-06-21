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
import io.undertow.util.Headers;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;
import org.bson.types.ObjectId;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.restheart.test.integration.HttpClientAbstactIT;
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
public class GetFileHandlerIT extends HttpClientAbstactIT {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public static final String FILENAME = "RESTHeart_documentation.pdf";
    public static final String BUCKET = "mybucket";
    public static Object ID = "myfile";

    public GetFileHandlerIT() {
    }

    @Test
    public void testGetFile() throws Exception {
        createBucket();
        ObjectId fileId = createFile();
        
        String url = dbTmpUri + "/" + BUCKET + ".files/" + fileId.toString() + "/binary";
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
    public void testPostFile() throws Exception {
        createBucket();
        ObjectId fileId = createFile();
    }

    @Test
    public void testEmptyBucket() throws Exception {
        createBucket();
        
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
    }
    
    @Test
    public void testBucketWithFile() throws Exception {
        createBucket();
        ObjectId fileId = createFile();
        
        // test that GET /db/bucket.files includes the file
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files";
        Response resp = adminExecutor.execute(Request.Get(bucketUrl));

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

        assertNotNull(json.get("_embedded").asObject().get("rh:file"));
        assertTrue(json.get("_embedded").asObject().get("rh:file").isArray());
    }
    
    private void createBucket() throws IOException {
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
    }

    private ObjectId createFile() throws UnknownHostException, IOException {
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files/";

        InputStream is = GetFileHandlerIT.class.getResourceAsStream("/" + FILENAME);

        HttpEntity entity = MultipartEntityBuilder
                .create()
                .addBinaryBody("file", is, ContentType.create("application/octet-stream"), FILENAME)
                .addTextBody("metadata", "{\"type\": \"documentation\"}")
                .build();

        Response resp = adminExecutor.execute(Request.Post(bucketUrl)
                .body(entity));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        
        StatusLine statusLine = httpResp.getStatusLine();
        
        assertNotNull(statusLine);
        assertEquals("check status code", HttpStatus.SC_CREATED, statusLine.getStatusCode());
        
        Header[] hs = httpResp.getHeaders("Location");
        
        if (hs == null || hs.length < 1) {
            return null;
        } else {
            String loc = hs[0].getValue();
            
            String id = loc.substring(loc.lastIndexOf("/") + 1 );
            
            return new ObjectId(id);
        }
    }
}
