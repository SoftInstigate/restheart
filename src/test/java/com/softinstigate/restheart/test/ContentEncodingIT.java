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

import static com.softinstigate.restheart.test.AbstactIT.conf;
import com.softinstigate.restheart.utils.HttpStatus;
import io.undertow.util.Headers;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import junit.framework.Assert;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author uji
 */
public class ContentEncodingIT extends AbstactIT
{
    protected static Executor notDecompressingExecutor = null;
    
    public ContentEncodingIT()
    {
    }
    
    @Before
    public void setUp() throws Exception
    {
        super.setUp();
        notDecompressingExecutor = Executor.newInstance(HttpClients.custom().disableContentCompression().build()).auth(new HttpHost(conf.getHttpHost()), "admin", "changeit");
    }
            

    @Test
    public void testGzipAcceptEncoding() throws Exception
    {
        Response resp = notDecompressingExecutor.execute(Request.Get(rootUri).addHeader(Headers.ACCEPT_ENCODING_STRING, Headers.GZIP.toString()));

        HttpResponse httpResp = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        Assert.assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);

        String content = EntityUtils.toString(entity);

        Header h = httpResp.getFirstHeader("Content-Encoding");

        Assert.assertNotNull("check accept encoding header not null", h);
        Assert.assertEquals("check accept encoding header value", Headers.GZIP.toString(), h.getValue());

        Assert.assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());

        try
        {
            GZIPInputStream gzipis = new GZIPInputStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.ISO_8859_1)));

            while (gzipis.read() > 0)
            {

            }
        }
        catch (Exception ex)
        {
            Assert.fail("check decompressing content");
        }
    }
}
