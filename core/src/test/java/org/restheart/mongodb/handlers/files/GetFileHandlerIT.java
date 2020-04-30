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
package org.restheart.mongodb.handlers.files;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;
import static org.restheart.utils.HttpStatus.SC_CREATED;
import static org.restheart.utils.HttpStatus.SC_NOT_FOUND;
import static org.restheart.utils.HttpStatus.SC_OK;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class GetFileHandlerIT extends FileHandlerAbstractIT {

    /**
     *
     */
    public static Object ID = "myfile";

    /**
     *
     */
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     *
     */
    public GetFileHandlerIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Before
    public void init() throws Exception {
        createBucket();
    }

    /**
     *
     * @throws IOException
     */
    @Test
    public void testGetFile() throws IOException {
        ObjectId fileId = createFile();

        String url = dbTmpUri + "/" + BUCKET + ".files/" + fileId.toString() + "/binary";

        HttpResponse httpResp = this.check("Response is 200 OK", adminExecutor.execute(Request.Get(url)), SC_OK);
        HttpEntity entity = checkContentType(httpResp, Exchange.APPLICATION_PDF_TYPE);

        File tempFile = tempFolder.newFile(FILENAME);
        FileOutputStream fos = new FileOutputStream(tempFile);

        entity.writeTo(fos);
        assertTrue(tempFile.length() > 0);
    }

    /**
     *
     * @throws IOException
     * @throws UnirestException
     */
    @Test
    public void testGetNotExistingFile() throws IOException, UnirestException {
        final String url = dbTmpUri.toString().concat("/").concat(BUCKET.concat(".files"));

        this.check("Response is 200 OK",
                adminExecutor.execute(Request.Get(url)), SC_OK);

        this.check("Response is 404 Not Found",
                adminExecutor.execute(Request.Get(url.concat("/notexistingid"))), SC_NOT_FOUND);

        this.check("Response is 404 Not Found",
                adminExecutor.execute(Request.Get(url.concat("/notexistingid/binary"))), SC_NOT_FOUND);
    }

    /**
     *
     * @throws IOException
     */
    @Test
    public void testEmptyBucket() throws IOException {
        // test that GET /db includes the rh:bucket array
        HttpResponse httpResp = this.check("Response is 200 OK", adminExecutor.execute(Request.Get(dbTmpUri)), SC_OK);
        HttpEntity entity = checkContentType(httpResp, Exchange.HAL_JSON_MEDIA_TYPE);

        String content = EntityUtils.toString(entity);
        JsonObject json = Json.parse(content).asObject();
        checkReturnedAndEmbedded(json);

        assertNotNull(json.get("_embedded").asObject().get("rh:bucket"));
        assertTrue(json.get("_embedded").asObject().get("rh:bucket").isArray());

        assertTrue(!json.get("_embedded").asObject().get("rh:bucket").asArray().isEmpty());
    }

    /**
     *
     * @throws IOException
     */
    @Test
    public void testBucketWithFile() throws IOException {
        ObjectId fileId = createFile();

        // test that GET /db/bucket.files includes the file
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files";
        HttpResponse httpResp = this.check("Response is 200 OK", adminExecutor.execute(Request.Get(bucketUrl)), SC_OK);
        HttpEntity entity = checkContentType(httpResp, Exchange.HAL_JSON_MEDIA_TYPE);

        String content = EntityUtils.toString(entity);
        JsonObject json = Json.parse(content).asObject();
        checkReturnedAndEmbedded(json);

        assertNotNull(json.get("_embedded").asObject().get("rh:file"));
        assertTrue(json.get("_embedded").asObject().get("rh:file").isArray());
    }

    /**
     *
     * @throws IOException
     */
    @Test
    public void testPutFile() throws IOException {
        String id = "test";

        createFilePut(id);

        // test that GET /db/bucket.files includes the file
        String fileUrl = dbTmpUri + "/" + BUCKET + ".files/" + id;
        Response resp = adminExecutor.execute(Request.Get(fileUrl));

        HttpResponse httpResp = this.check("Response is 200 OK", resp, SC_OK);
        HttpEntity entity = checkContentType(httpResp, Exchange.HAL_JSON_MEDIA_TYPE);

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
        HttpResponse httpResp = this.check("Response is 201 CREATED", resp, SC_CREATED);

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
        this.check("Response is 201 CREATED", resp, HttpStatus.SC_CREATED);
    }

    private void checkReturnedAndEmbedded(JsonObject json) {
        assertNotNull(json.get("_returned"));
        assertTrue(json.get("_returned").isNumber());
        assertTrue(json.getInt("_returned", 0) > 0);
        assertNotNull(json.get("_embedded"));
        assertTrue(json.get("_embedded").isObject());
    }
}
