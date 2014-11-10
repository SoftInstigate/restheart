/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.integrationtest;

import com.eclipsesource.json.JsonObject;
import com.softinstigate.restheart.hal.Representation;
import static com.softinstigate.restheart.integrationtest.AbstactIT.adminExecutor;
import com.softinstigate.restheart.utils.HttpStatus;
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
 * @author uji
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
            }
            catch (Throwable t) {
                junit.framework.Assert.fail("parsing received json");
            }

            junit.framework.Assert.assertNotNull("check json not null", json);
            junit.framework.Assert.assertNotNull("check not null _returned property", json.get("_returned"));
            junit.framework.Assert.assertNotNull("check not null _size property", json.get("_size"));
            junit.framework.Assert.assertEquals("check _size value to be 5", 5, json.get("_size").asInt());
            junit.framework.Assert.assertEquals("check _returned value to be 5", 5, json.get("_returned").asInt());
        }
        finally {
            mongoClient.dropDatabase(dbTmpName);
        }
    }
}
