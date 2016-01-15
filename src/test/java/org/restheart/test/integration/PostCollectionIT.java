/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) 2014 - 2015 SoftInstigate Srl
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
package org.restheart.test.integration;

import com.eclipsesource.json.JsonObject;
import org.restheart.hal.Representation;
import static org.restheart.test.integration.AbstactIT.adminExecutor;
import org.restheart.utils.HttpStatus;
import io.undertow.util.Headers;
import java.net.URI;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PostCollectionIT extends AbstactIT {

    public PostCollectionIT() {
    }

    @Test
    public void testPostCollection() throws Exception {
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

        assertNotNull("check loocation header", headers);
        assertTrue("check loocation header", headers.length > 0);

        Header locationH = headers[0];
        String location = locationH.getValue();

        URI createdDocUri = URI.create(location);

        resp = adminExecutor.execute(Request.Get(createdDocUri).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

        JsonObject content = JsonObject.readFrom(resp.returnContent().asString());
        assertNotNull("check created doc content", content.get("_id"));
        assertNotNull("check created doc content", content.get("_etag"));
        assertNotNull("check created doc content", content.get("a"));
        assertTrue("check created doc content", content.get("a").asInt() == 1);

        String _id = content.get("_id").asObject().get("$oid").asString();
        String _etag = content.get("_etag").asObject().get("$oid").asString();

        // try to post with _id without etag  forcing checkEtag
        resp = adminExecutor.execute(Request.Post(addCheckEtag(collectionTmpUri)).bodyString("{_id:{\"$oid\":\"" + _id + "\"}, a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
        check("check post created doc without etag forcing checkEtag", resp, HttpStatus.SC_CONFLICT);
        
        // try to post with wrong etag
        resp = adminExecutor.execute(Request.Post(collectionTmpUri).bodyString("{_id:{\"$oid\":\"" + _id + "\"}, a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE).addHeader(Headers.IF_MATCH_STRING, "pippoetag"));
        check("check put created doc with wrong etag", resp, HttpStatus.SC_PRECONDITION_FAILED);

        // try to post with correct etag
        resp = adminExecutor.execute(Request.Post(collectionTmpUri).bodyString("{_id:{\"$oid\":\"" + _id + "\"}, a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE).addHeader(Headers.IF_MATCH_STRING, _etag));
        check("check post created doc with correct etag", resp, HttpStatus.SC_OK);
        
        // try to post with _id without etag
        resp = adminExecutor.execute(Request.Post(collectionTmpUri).bodyString("{_id:{\"$oid\":\"" + _id + "\"}, a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
        check("check post created doc without etag", resp, HttpStatus.SC_OK);
    }
}
