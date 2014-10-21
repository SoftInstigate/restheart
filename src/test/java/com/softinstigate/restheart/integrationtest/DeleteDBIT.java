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
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author uji
 */
public class DeleteDBIT extends AbstactIT
{
    
    public DeleteDBIT()
    {
    }
    
    @Test
    public void testDeleteDB() throws Exception
    {
        try
        {
            Response resp;

            // *** PUT tmpdb
            resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check put db", resp, HttpStatus.SC_CREATED);

            // try to delete without etag
            resp = adminExecutor.execute(Request.Delete(dbTmpUri));
            check("check delete tmp doc without etag", resp, HttpStatus.SC_CONFLICT);
            
            // try to delete with wrong etag
            resp = adminExecutor.execute(Request.Delete(dbTmpUri).addHeader(Headers.IF_MATCH_STRING, "pippoetag"));
            check("check delete tmp doc with wrong etag", resp, HttpStatus.SC_PRECONDITION_FAILED);
            
            resp = adminExecutor.execute(Request.Get(dbTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            //check("getting etag of tmp doc", resp, HttpStatus.SC_OK);
            
            JsonObject content = JsonObject.readFrom(resp.returnContent().asString());
            
            String etag = content.get("_etag").asString();
            
            // try to delete with correct etag
            resp = adminExecutor.execute(Request.Delete(dbTmpUri).addHeader(Headers.IF_MATCH_STRING, etag));
            check("check delete tmp doc with correct etag", resp, HttpStatus.SC_GONE);

            resp = adminExecutor.execute(Request.Get(dbTmpUri).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check get deleted tmp doc", resp, HttpStatus.SC_NOT_FOUND);
        }
        finally
        {
            mongoClient.dropDatabase(dbTmpName);
        }
    }
}
