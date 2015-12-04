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
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class PatchDocumentIT extends AbstactIT {

    public PatchDocumentIT() {
    }

    @Test
    public void testPatchDocument() throws Exception {
        Response resp;

        // *** PUT tmpdb
        resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
        check("check put db", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        resp = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
        check("check put coll1", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpdoc
        resp = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
        check("check put tmp doc", resp, HttpStatus.SC_CREATED);

        // try to patch without body
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
        check("check patch tmp doc without etag", resp, HttpStatus.SC_NOT_ACCEPTABLE);

        // try to patch without etag
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
        check("check patch tmp doc without etag", resp, HttpStatus.SC_CONFLICT);

        // try to patch with wrong etag
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE).addHeader(Headers.IF_MATCH_STRING, "pippoetag"));
        check("check patch tmp doc with wrong etag", resp, HttpStatus.SC_PRECONDITION_FAILED);

        resp = adminExecutor.execute(Request.Get(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

        JsonObject content = JsonObject.readFrom(resp.returnContent().asString());

        String etag = content.get("_etag").asObject().get("$oid").asString();

        // try to patch with correct etag
        resp = adminExecutor.execute(Request.Patch(documentTmpUri).bodyString("{b:2}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE).addHeader(Headers.IF_MATCH_STRING, etag));
        check("check patch tmp doc with correct etag", resp, HttpStatus.SC_OK);

        resp = adminExecutor.execute(Request.Get(documentTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

        content = JsonObject.readFrom(resp.returnContent().asString());
        assertNotNull("check patched content", content.get("a"));
        assertNotNull("check patched content", content.get("b"));
        assertTrue("check patched content", content.get("a").asInt() == 1 && content.get("b").asInt() == 2);
    }
}
