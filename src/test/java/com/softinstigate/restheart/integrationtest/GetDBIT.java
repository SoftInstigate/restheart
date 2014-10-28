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

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.softinstigate.restheart.hal.Representation;
import com.softinstigate.restheart.utils.HttpStatus;
import java.net.URI;
import java.net.URISyntaxException;
import junit.framework.Assert;
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
public class GetDBIT extends AbstactIT
{
    
    public GetDBIT()
    {
    }
    
    @Test
    public void testGetDB() throws Exception
    {
        testGetDb(dbUri);
    }
    
    @Test
    public void testGetDBRemappedAll() throws Exception
    {
        testGetDb(dbUriRemappedAll);
    }
    
    @Test
    public void testGetDBRemappedDb() throws Exception
    {
        testGetDb(dbUriRemappedDb);
    }
    
    @Test
    public void testGetDBPaging() throws Exception
    {
        Response resp = adminExecutor.execute(Request.Get(dbUriPaging));
        
        HttpResponse    httpResp    = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        HttpEntity      entity      = httpResp.getEntity();
        Assert.assertNotNull(entity);
        StatusLine      statusLine  = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);
        
        Assert.assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        Assert.assertNotNull("content type not null", entity.getContentType());
        Assert.assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());
        
        String content = EntityUtils.toString(entity);
        
        Assert.assertNotNull("", content);
        
        JsonObject json = null;
        
        try
        {
            json = JsonObject.readFrom(content);
        }
        catch(Throwable t)
        {
            Assert.fail("parsing received json");
        }
        
        Assert.assertNotNull("check json not null", json);
        
        Assert.assertNotNull("check not null _link", json.get("_links"));
        Assert.assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));
        
        Assert.assertNotNull("check not null _returned property", json.get("_returned"));
        Assert.assertNotNull("check not null _size value", json.get("_size"));
        Assert.assertNotNull("check not null _total_pages", json.get("_total_pages"));
        
        Assert.assertEquals("check _returned value to be 1", 1, json.get("_returned").asInt());
        Assert.assertEquals("check _size value to be 2", 3, json.get("_size").asInt());
        Assert.assertEquals("check _total_pages value to be 2",3, json.get("_total_pages").asInt());
        
        JsonObject links = (JsonObject) json.get("_links");
        
        Assert.assertNotNull("check not null self", links.get("self"));
        Assert.assertNotNull("check not null rh:root", links.get("rh:root"));
        Assert.assertNotNull("check not null rh:paging", links.get("rh:paging"));
        Assert.assertNotNull("check not null next", links.get("next"));
        Assert.assertNotNull("check not null first", links.get("first"));
        Assert.assertNotNull("check not null last", links.get("last"));
        Assert.assertNull("check null previous", links.get("previous"));
        
        Response respSelf = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("self").asObject().get("href").asString())));
        HttpResponse    httpRespSelf    = respSelf.returnResponse();
        Assert.assertNotNull(httpRespSelf);
        
        Response respRoot = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("rh:root").asObject().get("href").asString())));
        HttpResponse    httpRespRoot    = respRoot.returnResponse();
        Assert.assertNotNull(httpRespRoot);
        
        Response respNext = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("next").asObject().get("href").asString())));
        HttpResponse    httpRespNext    = respNext.returnResponse();
        Assert.assertNotNull(httpRespNext);
        
        Response respFirst = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("first").asObject().get("href").asString())));
        HttpResponse    respRespFirst    = respFirst.returnResponse();
        Assert.assertNotNull(respRespFirst);
        
        Response respLast = adminExecutor.execute(Request.Get(dbUriPaging.resolve(links.get("last").asObject().get("href").asString())));
        HttpResponse    httpRespLast    = respLast.returnResponse();
        Assert.assertNotNull(httpRespLast);
    }
    
    private void testGetDb(URI uri) throws Exception
    {
        Response resp = adminExecutor.execute(Request.Get(uri));
        
        HttpResponse    httpResp    = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        HttpEntity      entity      = httpResp.getEntity();
        Assert.assertNotNull(entity);
        StatusLine      statusLine  = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);
        
        Assert.assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        Assert.assertNotNull("content type not null", entity.getContentType());
        Assert.assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());
        
        String content = EntityUtils.toString(entity);
        
        Assert.assertNotNull("", content);
        
        JsonObject json = null;
        
        try
        {
            json = JsonObject.readFrom(content);
        }
        catch(Throwable t)
        {
            Assert.fail("parsing received json");
        }
        
        Assert.assertNotNull("check json not null", json);
        Assert.assertNotNull("check not null _created_on property", json.get("_created_on"));
        Assert.assertNotNull("check not null _etag property", json.get("_etag"));
        Assert.assertNotNull("check not null _lastupdated_on property", json.get("_lastupdated_on"));
        Assert.assertNotNull("check not null _db-props-cached property", json.get("_db-props-cached"));
        Assert.assertNotNull("check not null _returned property", json.get("_returned"));
        Assert.assertNotNull("check not null _size property", json.get("_size"));
        Assert.assertNotNull("check not null _total_pages property", json.get("_total_pages"));
        
        Assert.assertNotNull("check not null _embedded", json.get("_embedded"));
        
        Assert.assertTrue("check _embedded to be a json object", (json.get("_embedded") instanceof JsonObject));
        
        JsonObject embedded = (JsonObject) json.get("_embedded");
        
        Assert.assertNotNull("check not null _embedded.rh:coll", embedded.get("rh:coll"));
        
        Assert.assertTrue("check _embedded.rh:coll to be a json array", (embedded.get("rh:coll") instanceof JsonArray));
        
        JsonArray rhcoll = (JsonArray) embedded.get("rh:coll");
        
        Assert.assertNotNull("check not null _embedded.rh:coll[0]", rhcoll.get(0));
        
        Assert.assertTrue("check _embedded.rh:coll[0] to be a json object", (rhcoll.get(0) instanceof JsonObject));
        
        JsonObject rhcoll0 = (JsonObject) rhcoll.get(0);
        
        Assert.assertNotNull("check not null _embedded.rh:coll[0]._id", rhcoll0.get("_id"));
        
        Assert.assertNotNull("check not null _embedded.rh:coll[0]._links", rhcoll0.get("_links"));
        
        Assert.assertTrue("check _embedded.rh:coll[0]._links to be a json object", (rhcoll0.get("_links") instanceof JsonObject));
        
        JsonObject rhcoll0Links = (JsonObject) rhcoll0.get("_links");
        
        Assert.assertNotNull("check not null _embedded.rh:coll[0]._links.self", rhcoll0Links.get("self"));
        
        Assert.assertTrue("check _embedded.rh:coll[0]._links.self  to be a json object", (rhcoll0Links.get("self") instanceof JsonObject));
        
        JsonObject rhdb0LinksSelf = (JsonObject) rhcoll0Links.get("self");
        
        Assert.assertNotNull("check not null _embedded.rh:coll[0]._links.self.href", rhdb0LinksSelf.get("href"));
        
        Assert.assertTrue("check _embedded.rh:coll[0]._links.self.href to be a string", (rhdb0LinksSelf.get("href").isString()));
        
        try
        {
            URI _uri = new URI(rhdb0LinksSelf.get("href").asString());
        }
        catch(URISyntaxException use)
        {
            Assert.fail("check _embedded.rh:coll[0]._links.self.href to be a valid URI");
        }
        
        Assert.assertNotNull("check not null _link", json.get("_links"));
        Assert.assertTrue("check _link to be a json object", (json.get("_links") instanceof JsonObject));
        
        JsonObject links = (JsonObject) json.get("_links");
        
        Assert.assertNotNull("check not null self", links.get("self"));
        Assert.assertNotNull("check not null rh:root", links.get("rh:root"));
        Assert.assertNotNull("check not null rh:paging", links.get("rh:paging"));
        Assert.assertNotNull("check not null curies", links.get("curies"));
    }
}
