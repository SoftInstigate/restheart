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
import io.undertow.util.Headers;
import java.io.IOException;
import java.io.InputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.restheart.exchange.Exchange;
import org.restheart.test.integration.HttpClientAbstactIT;
import static org.restheart.utils.HttpStatus.SC_CREATED;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public abstract class FileHandlerAbstractIT extends HttpClientAbstactIT {

    /**
     *
     */
    public static final String FILENAME = "sample.pdf";

    /**
     *
     */
    public static final String BUCKET = "mybucket";

    /**
     *
     * @return
     * @throws IOException
     */
    protected String createBucket() throws IOException {
        // create db
        Response resp = adminExecutor
                .execute(Request.Put(dbTmpUri)
                        .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        StatusLine statusLine = httpResp.getStatusLine();

        assertNotNull(statusLine);
        assertEquals("check status code", SC_CREATED, statusLine.getStatusCode());

        // create bucket
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files/";
        resp = adminExecutor.execute(Request.Put(bucketUrl)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        statusLine = httpResp.getStatusLine();

        assertNotNull(statusLine);
        assertEquals("check status code", SC_CREATED, statusLine.getStatusCode());

        return bucketUrl;
    }

    /**
     *
     * @return
     */
    protected HttpEntity buildMultipartResource() {
        InputStream is = this.getClass().getResourceAsStream("/" + FILENAME);
        HttpEntity entity = MultipartEntityBuilder
                .create()
                .addBinaryBody("file", is, ContentType.create("application/octet-stream"), FILENAME)
                .addTextBody("metadata", "{\"type\": \"documentation\"}")
                .build();
        return entity;
    }

    /**
     *
     * @param entity
     * @throws IOException
     * @throws ParseException
     */
    protected void checkNotNullMetadata(HttpEntity entity) throws IOException, ParseException {
        String content = EntityUtils.toString(entity);
        JsonObject json = Json.parse(content).asObject();
        assertNotNull(json.get("_id"));
        assertNotNull(json.get("metadata"));
    }

    /**
     *
     * @param httpResp
     * @param representation
     * @return
     */
    protected HttpEntity checkContentType(HttpResponse httpResp, String representation) {
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", representation, entity.getContentType().getValue());
        return entity;
    }

}
