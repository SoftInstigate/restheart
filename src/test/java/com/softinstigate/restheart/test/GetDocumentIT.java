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
package com.softinstigate.restheart.test;

import com.softinstigate.restheart.json.hal.HALDocumentSender;
import com.softinstigate.restheart.json.hal.Representation;
import com.softinstigate.restheart.utils.HttpStatus;
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
public class GetDocumentIT extends AbstactIT
{
    
    public GetDocumentIT()
    {
    }
    
    @Test
    public void testGetDocument() throws Exception
    {
        Response resp = adminExecutor.execute(Request.Get(document1Uri));
        
        HttpResponse    httpResp    = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        HttpEntity      entity      = httpResp.getEntity();
        Assert.assertNotNull(entity);
        StatusLine      statusLine  = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);
        
        Assert.assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        Assert.assertNotNull("content type not null", entity.getContentType());
        Assert.assertEquals("check content type", HALDocumentSender.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());
        
        String content = EntityUtils.toString(entity);
        
        Assert.assertNotNull("", content);
        
        Representation rep = Representation.parse(content);
        
        
        Assert.assertNotNull("check hal representation not null", rep);
        Assert.assertNotNull("check not null properties", rep.getProperties());
        Assert.assertNotNull("check not null @etag property", rep.getProperties().asObject().get("@etag"));
        Assert.assertNotNull("check not null @lastupdated_on property", rep.getProperties().asObject().get("@lastupdated_on"));
        Assert.assertNotNull("check not null @created_on property", rep.getProperties().asObject().get("@created_on"));
        Assert.assertNotNull("check not null @_id property", rep.getProperties().asObject().get("@_id"));
        Assert.assertEquals("check @_id value", document1Id, rep.getProperties().asObject().get("@_id"));
        Assert.assertNotNull("check not null a", rep.getProperties().asObject().get("a"));
        Assert.assertEquals("check a value", 1, rep.getProperties().asObject().get("a"));
        Assert.assertNotNull("check not null mtm links", rep.getLinks().asArray().get(0));
        Assert.assertNotNull("check not null mto links", rep.getLinks().asArray().get(1));
        Assert.assertNotNull("check not null otm links", rep.getLinks().asArray().get(2));
        Assert.assertNotNull("check not null oto links", rep.getLinks().asArray().get(3));
        
        
        Assert.assertEquals("check not null mtm links", collection2Uri.toString() + "?filter=<'mtm':<'$in':['doc2']>>\"", rep.getLinks().asArray().get(0).asObject().get("href"));
        Assert.assertEquals("check not null mto links", document2Uri.toString(), rep.getLinks().asArray().get(1).asObject().get("href"));
        Assert.assertEquals("check not null otm links", collection2Uri.toString() + "?filter=<'otm':<'$in':['doc2']>>\"", rep.getLinks().asArray().get(2).asObject().get("href"));
        Assert.assertEquals("check not null oto links", document2Uri.toString(), rep.getLinks().asArray().get(3).asObject().get("href"));
        
    }
}
