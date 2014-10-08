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

import com.fasterxml.jackson.core.JsonParseException;
import com.softinstigate.restheart.Bootstrapper;
import com.softinstigate.restheart.Configuration;
import com.softinstigate.restheart.json.hal.HALDocumentSender;
import com.softinstigate.restheart.utils.HttpStatus;
import com.theoryinpractise.halbuilder.api.ContentRepresentation;
import com.theoryinpractise.halbuilder.api.RepresentationFactory;
import com.theoryinpractise.halbuilder.json.JsonRepresentationFactory;
import io.undertow.util.Headers;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;
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
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author uji
 */
public class GetRootIT
{
    private static final String confFilePath = "restheart.yml";
    private static Configuration conf = null;
    private static Executor executor = null;
    private static Executor notDecompressingExecutor = null;
    
    public GetRootIT()
    {
    }
    
    @BeforeClass
    public static void setUpClass()
    {
    }
    
    @AfterClass
    public static void tearDownClass()
    {
    }
    
    @Before
    public void setUp()
    {
        conf = new Configuration(confFilePath);
        
        executor = Executor.newInstance().auth(new HttpHost(conf.getHttpHost()), "a", "a");
        notDecompressingExecutor = Executor.newInstance(HttpClients.custom().disableContentCompression().build()).auth(new HttpHost(conf.getHttpHost()), "a", "a");
    }
    
    @After
    public void tearDown()
    {
    }
    
    @Test
    public void testAuthentication() throws Exception
    {
        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/")
                .build();
        
        Response resp = Request.Get(uri).execute();
        
        HttpResponse    httpResp    = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        StatusLine      statusLine  = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);
        
        Assert.assertEquals("check unauthorized", HttpStatus.SC_UNAUTHORIZED, statusLine.getStatusCode());
        
        resp = executor.execute(Request.Get(uri));
        
        httpResp    = resp.returnResponse();
        statusLine  = httpResp.getStatusLine();
        
        Assert.assertEquals("check authorized", HttpStatus.SC_OK, statusLine.getStatusCode());
        
    }
    
    @Test
    public void testGetRoot() throws Exception
    {
        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/")
                .build();
        
        Response resp = executor.execute(Request.Get(uri));
        
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
        
        JsonRepresentationFactory rf = new JsonRepresentationFactory();
        
        ContentRepresentation rep = null;
        
        try
        {
            rep = rf.readRepresentation(RepresentationFactory.HAL_JSON, new InputStreamReader(entity.getContent()));
        }
        catch(JsonParseException jpe)
        {
            Assert.fail("check returned hal format");
        }
        
        Assert.assertNotNull("check hal representation not null", rep);
        Assert.assertNotNull("check not null properties", rep.getProperties());
        Assert.assertNotNull("check not null @returned property", rep.getProperties().get("@returned"));
        Assert.assertNotNull("check not null @size property", rep.getProperties().get("@size"));
        Assert.assertNotNull("check not null @total_pages property", rep.getProperties().get("@total_pages"));
        Assert.assertNotNull("check not null resources", rep.getResources());
    }
    
    @Test
    public void testGzipEncodingRoot() throws Exception
    {
        URI uri = new URIBuilder()
                .setScheme("http")
                .setHost(conf.getHttpHost())
                .setPort(conf.getHttpPort())
                .setPath("/")
                .build();

        Response resp = notDecompressingExecutor.execute(Request.Get(uri).addHeader(Headers.ACCEPT_ENCODING_STRING, Headers.GZIP.toString()));
        
        HttpResponse httpResp = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        Assert.assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);
        
        String content = EntityUtils.toString(entity);
        
        Header h = httpResp.getFirstHeader("Content-Encoding");
        
        Assert.assertNotNull("check content encoding header not null", h);
        Assert.assertEquals("check content encoding header value", Headers.GZIP.toString(), h.getValue());
        
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
