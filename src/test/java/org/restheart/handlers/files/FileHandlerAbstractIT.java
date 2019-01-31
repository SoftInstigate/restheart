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
import org.restheart.representation.Resource;
import org.restheart.test.integration.HttpClientAbstactIT;
import static org.restheart.utils.HttpStatus.SC_CREATED;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public abstract class FileHandlerAbstractIT extends HttpClientAbstactIT {

    public static final String FILENAME = "sample.pdf";
    public static final String BUCKET = "mybucket";

    protected String createBucket() throws IOException {
        // create db
        Response resp = adminExecutor
                .execute(Request.Put(dbTmpUri)
                        .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        StatusLine statusLine = httpResp.getStatusLine();

        assertNotNull(statusLine);
        assertEquals("check status code", SC_CREATED, statusLine.getStatusCode());

        // create bucket
        String bucketUrl = dbTmpUri + "/" + BUCKET + ".files/";
        resp = adminExecutor.execute(Request.Put(bucketUrl)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        statusLine = httpResp.getStatusLine();

        assertNotNull(statusLine);
        assertEquals("check status code", SC_CREATED, statusLine.getStatusCode());

        return bucketUrl;
    }

    protected HttpEntity buildMultipartResource() {
        InputStream is = this.getClass().getResourceAsStream("/" + FILENAME);
        HttpEntity entity = MultipartEntityBuilder
                .create()
                .addBinaryBody("file", is, ContentType.create("application/octet-stream"), FILENAME)
                .addTextBody("metadata", "{\"type\": \"documentation\"}")
                .build();
        return entity;
    }

    protected void checkNotNullMetadata(HttpEntity entity) throws IOException, ParseException {
        String content = EntityUtils.toString(entity);
        JsonObject json = Json.parse(content).asObject();
        assertNotNull(json.get("_id"));
        assertNotNull(json.get("metadata"));
    }

    protected HttpEntity checkContentType(HttpResponse httpResp, String representation) {
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", representation, entity.getContentType().getValue());
        return entity;
    }

}
