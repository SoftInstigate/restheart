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

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.UUID;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.restheart.representation.Resource.HAL_JSON_MEDIA_TYPE;
import static org.restheart.utils.HttpStatus.SC_CREATED;
import static org.restheart.utils.HttpStatus.SC_OK;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class PutFileHandlerIT extends FileHandlerAbstractIT {

    /**
     *
     */
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     *
     * @throws Exception
     */
    @Before
    public void init() throws Exception {
        Thread.sleep(1000); // Sleep 1 second to avoid NoHttpResponseException
        createBucket();
    }

    private HttpResponse createFilePut(String id) throws UnknownHostException, IOException {
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files/" + id;

        HttpEntity entity = buildMultipartResource();

        Response resp = adminExecutor.execute(Request.Put(bucketUrl)
                .body(entity));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertTrue("check status code", Arrays.asList(SC_CREATED, SC_OK).contains(statusLine.getStatusCode()));

        return httpResp;
    }

    /**
     *
     * @throws IOException
     */
    @Test
    public void testPutNonExistingFile() throws IOException {
        String id = "nonexistingfile" + UUID.randomUUID().toString();

        final HttpResponse httpResponse = createFilePut(id);
        assertEquals(SC_CREATED, httpResponse.getStatusLine().getStatusCode());

        // test that GET /db/bucket.files includes the file
        final String fileUrl = dbTmpUri + "/" + BUCKET + ".files/" + id;
        Response resp = adminExecutor.execute(Request.Get(fileUrl));

        HttpResponse httpResp = this.check("Response is 200 OK", resp, SC_OK);
        HttpEntity entity = checkContentType(httpResp, HAL_JSON_MEDIA_TYPE);
        checkNotNullMetadata(entity);
    }

    /**
     *
     * @throws IOException
     */
    @Test
    public void testPutAndOverwriteExistingFile() throws IOException {
        String id = "nonexistingfile" + UUID.randomUUID().toString();

        HttpResponse httpResponse = createFilePut(id);
        assertEquals(SC_CREATED, httpResponse.getStatusLine().getStatusCode());
        //now run the put again to see that it has been overwritten
        httpResponse = createFilePut(id);
        assertEquals(SC_OK, httpResponse.getStatusLine().getStatusCode());

        // test that GET /db/bucket.files includes the file
        final String fileUrl = dbTmpUri + "/" + BUCKET + ".files/" + id;
        Response resp = adminExecutor.execute(Request.Get(fileUrl));

        HttpResponse httpResp = this.check("Response is 200 OK", resp, SC_OK);
        HttpEntity entity = checkContentType(httpResp, HAL_JSON_MEDIA_TYPE);
        checkNotNullMetadata(entity);
    }
}
