package org.restheart.handlers.files;

import com.eclipsesource.json.JsonObject;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.restheart.hal.Representation;
import org.restheart.test.integration.HttpClientAbstactIT;
import org.restheart.utils.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.UUID;

import io.undertow.util.Headers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.restheart.utils.HttpStatus.SC_CREATED;
import static org.restheart.utils.HttpStatus.SC_OK;

/**
 * TODO: fillme
 */
public class PutFileHandlerIT extends HttpClientAbstactIT {
    public static final String FILENAME = "sample.pdf";
    public static final String BUCKET = "mybucket";

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void init() throws Exception {
        createBucket();
    }

    private void createBucket() throws IOException {
        // create db
        Response resp = adminExecutor.execute(Request.Put(dbTmpUri)
                                                  .addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        StatusLine statusLine = httpResp.getStatusLine();

        assertNotNull(statusLine);
        assertEquals("check status code", SC_CREATED, statusLine.getStatusCode());

        // create bucket
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files/";
        resp = adminExecutor.execute(Request.Put(bucketUrl)
                                         .addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

        httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        statusLine = httpResp.getStatusLine();

        assertNotNull(statusLine);
        assertEquals("check status code", SC_CREATED, statusLine.getStatusCode());
    }

    private HttpResponse createFilePut(String id) throws UnknownHostException, IOException {
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files/" + id;

        InputStream is = PutFileHandlerIT.class.getResourceAsStream("/" + FILENAME);

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
        assertTrue("check status code", Arrays.asList(SC_CREATED, SC_OK).contains(statusLine.getStatusCode()));

        return httpResp;
    }

    @Test
    public void testPutNonExistingFile() throws Exception {
        String id = "nonexistingfile" + UUID.randomUUID().toString();

        final HttpResponse httpResponse = createFilePut(id);
        assertEquals(SC_CREATED, httpResponse.getStatusLine().getStatusCode());

        // test that GET /db/bucket.files includes the file
        final String fileUrl = dbTmpUri + "/" + BUCKET + ".files/" + id;
        Response resp = adminExecutor.execute(Request.Get(fileUrl));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", SC_OK, statusLine.getStatusCode());
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

    @Test
    public void testPutAndOverwriteExistingFile() throws Exception {
        String id = "nonexistingfile" + UUID.randomUUID().toString();

        HttpResponse httpResponse = createFilePut(id);
        assertEquals(SC_CREATED, httpResponse.getStatusLine().getStatusCode());
        //now run the put again to see that it has been overwritten
        httpResponse = createFilePut(id);
        assertEquals(SC_OK, httpResponse.getStatusLine().getStatusCode());

        // test that GET /db/bucket.files includes the file
        final String fileUrl = dbTmpUri + "/" + BUCKET + ".files/" + id;
        Response resp = adminExecutor.execute(Request.Get(fileUrl));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", SC_OK, statusLine.getStatusCode());
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
}