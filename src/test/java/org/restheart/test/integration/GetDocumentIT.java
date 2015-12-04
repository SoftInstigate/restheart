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
import org.restheart.utils.HttpStatus;
import java.net.URI;
import static org.junit.Assert.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.bson.types.ObjectId;
import org.junit.Test;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
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
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull("check json not null", json);
        assertNotNull("check not null _etag property", json.get("_etag"));
        assertNotNull("check not null _lastupdated_on property", json.get("_lastupdated_on"));
        
        assertNotNull("check not null _id property", json.get("_id"));
        assertEquals("check _id value", document1Id, json.get("_id").asString());
        assertNotNull("check not null a", json.get("a"));
        assertEquals("check a value", 1, json.get("a").asInt());
        assertNotNull("check not null mtm links", json.get("_links").asObject().get("mtm"));
        assertNotNull("check not null mto links", json.get("_links").asObject().get("mto"));
        assertNotNull("check not null otm links", json.get("_links").asObject().get("otm"));
        assertNotNull("check not null oto links", json.get("_links").asObject().get("oto"));

        assertTrue("check mtm link", json.get("_links").asObject().get("mtm").asObject().get("href").asString().endsWith("?filter={'_id':{'$in':['doc2']}}"));
        assertTrue("check mto link", json.get("_links").asObject().get("mto").asObject().get("href").asString().endsWith("/doc2"));
        assertTrue("check otm link", json.get("_links").asObject().get("otm").asObject().get("href").asString().endsWith("?filter={'_id':{'$in':['doc2']}}"));
        assertTrue("check oto link", json.get("_links").asObject().get("oto").asObject().get("href").asString().endsWith("/doc2"));

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
        assertNotNull("check not null get mtm response", httpRespMtm);
        assertEquals("check get mtm response status code", HttpStatus.SC_OK, httpRespMtm.getStatusLine().getStatusCode());

        Response respMto = adminExecutor.execute(Request.Get(_mto));
        HttpResponse httpRespMto = respMto.returnResponse();
        assertNotNull("check not null get mto response", httpRespMto);
        assertEquals("check get mto response status code", HttpStatus.SC_OK, httpRespMto.getStatusLine().getStatusCode());

        Response respOtm = adminExecutor.execute(Request.Get(_otm));
        HttpResponse httpRespOtm = respOtm.returnResponse();
        assertNotNull("check not null get otm response", httpRespOtm);
        assertEquals("check get otm response status code", HttpStatus.SC_OK, httpRespOtm.getStatusLine().getStatusCode());

        Response respOto = adminExecutor.execute(Request.Get(_oto));
        HttpResponse httpRespOto = respOto.returnResponse();
        assertNotNull("check not null get oto response", httpRespOto);
        assertEquals("check get oto response status code", HttpStatus.SC_OK, httpRespOto.getStatusLine().getStatusCode());
    }
}
