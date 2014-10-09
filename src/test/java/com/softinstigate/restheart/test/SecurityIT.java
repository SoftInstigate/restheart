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

import com.softinstigate.restheart.utils.HttpStatus;
import junit.framework.Assert;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Test;

/**
 *
 * @author uji
 */
public class SecurityIT extends AbstactIT
{
    public SecurityIT()
    {
    }
    
    @Test
    public void testAuthentication() throws Exception
    {
        Response resp = unauthExecutor.execute(Request.Get(rootUri));
        
        HttpResponse httpResp = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        
        StatusLine statusLine = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);
        
        Assert.assertEquals("check unauthorized", HttpStatus.SC_UNAUTHORIZED, statusLine.getStatusCode());
        
        resp = adminExecutor.execute(Request.Get(rootUri));
        
        httpResp = resp.returnResponse();
        statusLine = httpResp.getStatusLine();
        
        Assert.assertEquals("check authorized", HttpStatus.SC_OK, statusLine.getStatusCode());
    }
}
