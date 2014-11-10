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
import com.softinstigate.restheart.utils.HttpStatus;
import java.net.URI;
import junit.framework.Assert;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

/**
 *
 * @author uji
 */
public class GetDocumentIT extends AbstactIT {

    public GetDocumentIT() {
    }

    @Test
    public void testGetDocumentRemappedAll() throws Exception {
        testGetDocument(document1UriRemappedAll);
    }

    @Test
    public void testGetDocumentRemappedDb() throws Exception {
        testGetDocument(document1UriRemappedDb);
    }

    @Test
    public void testGetDocumentRemappedCollection() throws Exception {
        testGetDocument(document1UriRemappedCollection);
    }

    @Test
    public void testGetDocumentRemappedDoc() throws Exception {
        testGetDocument(document1UriRemappedDocument);
    }

    private void testGetDocument(URI uri) throws Exception {
        Response resp = adminExecutor.execute(Request.Get(uri));

        HttpResponse httpResp = resp.returnResponse();
        Assert.assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        Assert.assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        Assert.assertNotNull(statusLine);

        Assert.assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        Assert.assertNotNull("content type not null", entity.getContentType());
        Assert.assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        Assert.assertNotNull("", content);

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
        }
        catch (Throwable t) {
            Assert.fail("parsing received json");
        }

        Assert.assertNotNull("check json not null", json);
        Assert.assertNotNull("check not null @etag property", json.get("_etag"));
        Assert.assertNotNull("check not null @lastupdated_on property", json.get("_lastupdated_on"));
        Assert.assertNotNull("check not null @created_on property", json.get("_created_on"));
        Assert.assertNotNull("check not null @_id property", json.get("_id"));
        Assert.assertEquals("check @_id value", document1Id, json.get("_id").asString());
        Assert.assertNotNull("check not null a", json.get("a"));
        Assert.assertEquals("check a value", 1, json.get("a").asInt());
        Assert.assertNotNull("check not null mtm links", json.get("_links").asObject().get("mtm"));
        Assert.assertNotNull("check not null mto links", json.get("_links").asObject().get("mto"));
        Assert.assertNotNull("check not null otm links", json.get("_links").asObject().get("otm"));
        Assert.assertNotNull("check not null oto links", json.get("_links").asObject().get("oto"));

        Assert.assertTrue("check mtm link", json.get("_links").asObject().get("mtm").asObject().get("href").asString().endsWith("?filter={'mtm':{'$in':['doc2']}}"));
        Assert.assertTrue("check mto link", json.get("_links").asObject().get("mto").asObject().get("href").asString().endsWith("/doc2"));
        Assert.assertTrue("check otm link", json.get("_links").asObject().get("otm").asObject().get("href").asString().endsWith("?filter={'otm':{'$in':['doc2']}}"));
        Assert.assertTrue("check oto link", json.get("_links").asObject().get("oto").asObject().get("href").asString().endsWith("/doc2"));

        String mtm = json.get("_links").asObject().get("mtm").asObject().get("href").asString();
        String mto = json.get("_links").asObject().get("mto").asObject().get("href").asString();
        String otm = json.get("_links").asObject().get("otm").asObject().get("href").asString();
        String oto = json.get("_links").asObject().get("oto").asObject().get("href").asString();

        URIBuilder ub = new URIBuilder();

        String[] mtms = mtm.split("\\?");
        String[] mtos = mtm.split("\\?");
        String[] otms = mtm.split("\\?");
        String[] otos = mtm.split("\\?");

        URI _mtm = ub
                .setScheme(uri.getScheme())
                .setHost(uri.getHost())
                .setPort(uri.getPort())
                .setPath(mtms[0])
                .setCustomQuery(mtms[1])
                .build();

        URI _mto = ub
                .setScheme(uri.getScheme())
                .setHost(uri.getHost())
                .setPort(uri.getPort())
                .setPath(mtos[0])
                .setCustomQuery(mtos[1])
                .build();

        URI _otm = ub
                .setScheme(uri.getScheme())
                .setHost(uri.getHost())
                .setPort(uri.getPort())
                .setPath(otms[0])
                .setCustomQuery(otms[1])
                .build();

        URI _oto = ub
                .setScheme(uri.getScheme())
                .setHost(uri.getHost())
                .setPort(uri.getPort())
                .setPath(otos[0])
                .setCustomQuery(otos[1])
                .build();

        Response respMtm = adminExecutor.execute(Request.Get(_mtm));
        HttpResponse httpRespMtm = respMtm.returnResponse();
        Assert.assertNotNull("check not null get mtm response", httpRespMtm);
        Assert.assertEquals("check get mtm response status code", HttpStatus.SC_OK, httpRespMtm.getStatusLine().getStatusCode());

        Response respMto = adminExecutor.execute(Request.Get(_mto));
        HttpResponse httpRespMto = respMto.returnResponse();
        Assert.assertNotNull("check not null get mto response", httpRespMto);
        Assert.assertEquals("check get mto response status code", HttpStatus.SC_OK, httpRespMto.getStatusLine().getStatusCode());

        Response respOtm = adminExecutor.execute(Request.Get(_otm));
        HttpResponse httpRespOtm = respOtm.returnResponse();
        Assert.assertNotNull("check not null get otm response", httpRespOtm);
        Assert.assertEquals("check get otm response status code", HttpStatus.SC_OK, httpRespOtm.getStatusLine().getStatusCode());

        Response respOto = adminExecutor.execute(Request.Get(_oto));
        HttpResponse httpRespOto = respOto.returnResponse();
        Assert.assertNotNull("check not null get oto response", httpRespOto);
        Assert.assertEquals("check get oto response status code", HttpStatus.SC_OK, httpRespOto.getStatusLine().getStatusCode());
    }
}
