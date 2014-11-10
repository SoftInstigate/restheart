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
import java.net.URI;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author uji
 */
public class PostCollectionIT extends AbstactIT {

    public PostCollectionIT() {
    }

    @Test
    public void testPostCollection() throws Exception {
        try {
            Response resp;

            // *** PUT tmpdb
            resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check put db", resp, HttpStatus.SC_CREATED);

            // *** PUT tmpcoll
            resp = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check put coll1", resp, HttpStatus.SC_CREATED);

            resp = adminExecutor.execute(Request.Post(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check post coll1", resp, HttpStatus.SC_CREATED);

            // *** POST tmpcoll
            resp = adminExecutor.execute(Request.Post(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            HttpResponse httpResp = check("check post coll1 again", resp, HttpStatus.SC_CREATED);

            Header[] headers = httpResp.getHeaders(Headers.LOCATION_STRING);

            Assert.assertNotNull("check loocation header", headers);
            Assert.assertTrue("check loocation header", headers.length > 0);

            Header locationH = headers[0];
            String location = locationH.getValue();

            URI createdDocUri = URI.create(location);

            resp = adminExecutor.execute(Request.Get(createdDocUri).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

            JsonObject content = JsonObject.readFrom(resp.returnContent().asString());
            Assert.assertNotNull("check created doc content", content.get("_id"));
            Assert.assertNotNull("check created doc content", content.get("_etag"));
            Assert.assertNotNull("check created doc content", content.get("a"));
            Assert.assertTrue("check created doc content", content.get("a").asInt() == 1);

            String _id = content.get("_id").asString();
            String _etag = content.get("_etag").asString();

            // try to post with _id without etag
            resp = adminExecutor.execute(Request.Post(collectionTmpUri).bodyString("{_id:\"" + _id + "\", a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check post created doc without etag", resp, HttpStatus.SC_CONFLICT);

            // try to post with wrong etag
            resp = adminExecutor.execute(Request.Post(collectionTmpUri).bodyString("{_id:\"" + _id + "\", a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE).addHeader(Headers.IF_MATCH_STRING, "pippoetag"));
            check("check put created doc with wrong etag", resp, HttpStatus.SC_PRECONDITION_FAILED);

            // try to post with correct etag
            resp = adminExecutor.execute(Request.Post(collectionTmpUri).bodyString("{_id:\"" + _id + "\", a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE).addHeader(Headers.IF_MATCH_STRING, _etag));
            check("check post created doc with correct etag", resp, HttpStatus.SC_OK);
        }
        finally {
            mongoClient.dropDatabase(dbTmpName);
        }
    }
}
