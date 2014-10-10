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
public class GetRootIT extends AbstactIT
{
    public GetRootIT()
    {
    }
    
    @Test
    public void testGetRoot() throws Exception
    {
        Response resp = adminExecutor.execute(Request.Get(rootUri));
        
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
        Assert.assertNotNull("check not null @returned property", rep.getProperties().asObject().get("@returned"));
        Assert.assertNotNull("check not null @size property", rep.getProperties().asObject().get("@size"));
        Assert.assertNotNull("check not null @total_pages property", rep.getProperties().asObject().get("@total_pages"));
        Assert.assertNotNull("check not null resources", rep.getResources());
    }
}