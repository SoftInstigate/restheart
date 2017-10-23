package org.restheart.handlers.files;

import com.eclipsesource.json.JsonObject;
import com.mashape.unirest.http.Unirest;
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
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.restheart.hal.Representation;
import org.restheart.test.integration.HttpClientAbstactIT;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class GetFileHandlerIT extends HttpClientAbstactIT {

    public static final String FILENAME = "RESTHeart_documentation.pdf";
    public static final String BUCKET = "mybucket";
    public static Object ID = "myfile";
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public GetFileHandlerIT() {
    }
    
    @Before
    public void init() throws Exception {
        createBucket();
    }

    @Test
    public void testGetFile() throws Exception {
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
    public void testGetNotExistingFile() throws Exception {
        String bucketUlr = dbTmpUri.toString().concat("/").concat(BUCKET.concat(".files")); 
        
        com.mashape.unirest.http.HttpResponse<String> resp = Unirest
                .get(bucketUlr)
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();
        
        Assert.assertEquals("bucket exists " + BUCKET,
                org.apache.http.HttpStatus.SC_OK, resp.getStatus());
        
        
        // get not existing file metadata
        
        resp = Unirest
                .get(bucketUlr.concat("/notexistingid"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();
        
        Assert.assertEquals("get not existing file metadata",
                org.apache.http.HttpStatus.SC_NOT_FOUND, resp.getStatus());
        
        // get not existing file binary
        
        resp = Unirest
                .get(bucketUlr.concat("/notexistingid/binary"))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();
        
        Assert.assertEquals("get not existing file binary",
                org.apache.http.HttpStatus.SC_NOT_FOUND, resp.getStatus());
    }

    @Test
    public void testEmptyBucket() throws Exception {
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

    @Test
    public void testPutFile() throws Exception {
        String id = "test";

        createFilePut(id);

        // test that GET /db/bucket.files includes the file
        String fileUrl = dbTmpUri + "/" + BUCKET + ".files/" + id;
        Response resp = adminExecutor.execute(Request.Get(fileUrl));

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

        assertNotNull(json.get("_id"));
        assertNotNull(json.get("metadata"));
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
            String id = loc.substring(loc.lastIndexOf('/') + 1);
            return new ObjectId(id);
        }
    }

    private void createFilePut(String id) throws UnknownHostException, IOException {
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files/" + id;

        InputStream is = GetFileHandlerIT.class.getResourceAsStream("/" + FILENAME);

        HttpEntity entity = MultipartEntityBuilder
                .create()
                .addBinaryBody("file", is, ContentType.create("application/octet-stream"), FILENAME)
                .addTextBody("metadata", "{\"type\": \"documentation\"}")
                .build();

        Response resp = adminExecutor.execute(Request.Put(bucketUrl)
                .body(entity));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);

        StatusLine statusLine = httpResp.getStatusLine();

        assertNotNull(statusLine);
        assertEquals("check status code", HttpStatus.SC_CREATED, statusLine.getStatusCode());
    }
}
