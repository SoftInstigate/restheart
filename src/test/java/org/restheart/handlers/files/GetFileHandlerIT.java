package org.restheart.handlers.files;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.bson.types.ObjectId;
import org.junit.Assert;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.restheart.hal.Representation.APPLICATION_PDF_TYPE;
import static org.restheart.hal.Representation.HAL_JSON_MEDIA_TYPE;
import org.restheart.utils.HttpStatus;
import static org.restheart.utils.HttpStatus.SC_CREATED;
import static org.restheart.utils.HttpStatus.SC_OK;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class GetFileHandlerIT extends FileHandlerAbstractIT {

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
    public void testGetFile() throws IOException {
        ObjectId fileId = createFile();

        String url = dbTmpUri + "/" + BUCKET + ".files/" + fileId.toString() + "/binary";

        HttpResponse httpResp = this.check("Response is 200 OK", adminExecutor.execute(Request.Get(url)), SC_OK);
        HttpEntity entity = checkContentType(httpResp, APPLICATION_PDF_TYPE);

        File tempFile = tempFolder.newFile(FILENAME);
        FileOutputStream fos = new FileOutputStream(tempFile);

        entity.writeTo(fos);
        assertTrue(tempFile.length() > 0);
    }

    @Test
    public void testGetNotExistingFile() throws IOException, UnirestException {
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
    public void testEmptyBucket() throws IOException {
        // test that GET /db includes the rh:bucket array
        HttpResponse httpResp = this.check("Response is 200 OK", adminExecutor.execute(Request.Get(dbTmpUri)), SC_OK);
        HttpEntity entity = checkContentType(httpResp, HAL_JSON_MEDIA_TYPE);

        String content = EntityUtils.toString(entity);
        JsonObject json = Json.parse(content).asObject();
        checkReturnedAndEmbedded(json);

        assertNotNull(json.get("_embedded").asObject().get("rh:bucket"));
        assertTrue(json.get("_embedded").asObject().get("rh:bucket").isArray());

        assertTrue(!json.get("_embedded").asObject().get("rh:bucket").asArray().isEmpty());
    }

    @Test
    public void testBucketWithFile() throws IOException {
        ObjectId fileId = createFile();

        // test that GET /db/bucket.files includes the file
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files";
        HttpResponse httpResp = this.check("Response is 200 OK", adminExecutor.execute(Request.Get(bucketUrl)), SC_OK);
        HttpEntity entity = checkContentType(httpResp, HAL_JSON_MEDIA_TYPE);

        String content = EntityUtils.toString(entity);
        JsonObject json = Json.parse(content).asObject();
        checkReturnedAndEmbedded(json);

        assertNotNull(json.get("_embedded").asObject().get("rh:file"));
        assertTrue(json.get("_embedded").asObject().get("rh:file").isArray());
    }

    @Test
    public void testPutFile() throws IOException {
        String id = "test";

        createFilePut(id);

        // test that GET /db/bucket.files includes the file
        String fileUrl = dbTmpUri + "/" + BUCKET + ".files/" + id;
        Response resp = adminExecutor.execute(Request.Get(fileUrl));

        HttpResponse httpResp = this.check("Response is 200 OK", resp, SC_OK);
        HttpEntity entity = checkContentType(httpResp, HAL_JSON_MEDIA_TYPE);

        String content = EntityUtils.toString(entity);

        JsonObject json = Json.parse(content).asObject();
        assertNotNull(json.get("_id"));
        assertNotNull(json.get("metadata"));
    }

    private ObjectId createFile() throws UnknownHostException, IOException {
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files/";

        HttpEntity entity = buildMultipartResource();
        Response resp = adminExecutor.execute(Request.Post(bucketUrl)
                .body(entity));
        HttpResponse httpResp = this.check("Response is 200 OK", resp, SC_CREATED);

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
        HttpEntity entity = buildMultipartResource();
        Response resp = adminExecutor.execute(Request.Put(bucketUrl)
                .body(entity));
        this.check("Response is 200 OK", resp, HttpStatus.SC_CREATED);
    }

    private void checkReturnedAndEmbedded(JsonObject json) {
        assertNotNull(json.get("_returned"));
        assertTrue(json.get("_returned").isNumber());
        assertTrue(json.getInt("_returned", 0) > 0);
        assertNotNull(json.get("_embedded"));
        assertTrue(json.get("_embedded").isObject());
    }
}
