/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetDocumentIT extends HttpClientAbstactIT {

    /**
     *
     */
    public GetDocumentIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDocumentRemappedAll() throws Exception {
        testGetDocument(document1UriRemappedAll);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDocumentRemappedDb() throws Exception {
        testGetDocument(document1UriRemappedDb);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetDocumentRemappedCollection() throws Exception {
        testGetDocument(document1UriRemappedCollection);
    }

    /**
     *
     * @throws Exception
     */
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

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check status code");
        assertNotNull(entity.getContentType(), "content type not null");
        assertTrue(entity.getContentType().getValue().startsWith(Exchange.HAL_JSON_MEDIA_TYPE), "check content type");

        String content = EntityUtils.toString(entity);

        assertNotNull(content, "");

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull(json, "check json not null");
        assertNotNull(json.get("_etag"), "check not null _etag property");

        assertNotNull(json.get("_id"), "check not null _id property");
        assertEquals(document1Id, json.get("_id").asString(), "check _id value");
        assertNotNull(json.get("a"), "check not null a");
        assertEquals(1, json.get("a").asInt(), "check a value");
        assertNotNull(json.get("_links").asObject().get("mtm"), "check not null mtm links");
        assertNotNull(json.get("_links").asObject().get("mto"), "check not null mto links");
        assertNotNull(json.get("_links").asObject().get("otm"), "check not null otm links");
        assertNotNull(json.get("_links").asObject().get("oto"), "check not null oto links");

        assertTrue(json.get("_links").asObject().get("mtm").asObject().get("href").asString()
                .endsWith("?filter={'_id':{'$in':['doc2']}}"), "check mtm link");
        assertTrue(json.get("_links").asObject().get("mto").asObject().get("href").asString().endsWith("/doc2"),
                "check mto link");
        assertTrue(json.get("_links").asObject().get("otm").asObject().get("href").asString()
                .endsWith("?filter={'_id':{'$in':['doc2']}}"), "check otm link");
        assertTrue(json.get("_links").asObject().get("oto").asObject().get("href").asString().endsWith("/doc2"),
                "check oto link");

        String mtm = json.get("_links").asObject().get("mtm").asObject().get("href").asString();
        // String mto =
        // json.get("_links").asObject().get("mto").asObject().get("href").asString();
        // String otm =
        // json.get("_links").asObject().get("otm").asObject().get("href").asString();
        // String oto =
        // json.get("_links").asObject().get("oto").asObject().get("href").asString();

        String[] mtms = mtm.split("\\?");
        String[] mtos = mtm.split("\\?");
        String[] otms = mtm.split("\\?");
        String[] otos = mtm.split("\\?");

        String base = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();

        URI _mtm = URI.create(base + mtms[0] + "?" + encodeQueryForUri(mtms[1]));
        URI _mto = URI.create(base + mtos[0] + "?" + encodeQueryForUri(mtos[1]));
        URI _otm = URI.create(base + otms[0] + "?" + encodeQueryForUri(otms[1]));
        URI _oto = URI.create(base + otos[0] + "?" + encodeQueryForUri(otos[1]));

        Response respMtm = adminExecutor.execute(Request.Get(_mtm));
        HttpResponse httpRespMtm = respMtm.returnResponse();
        assertNotNull(httpRespMtm, "check not null get mtm response");
        assertEquals(HttpStatus.SC_OK,
                httpRespMtm.getStatusLine().getStatusCode(), "check get mtm response status code");

        Response respMto = adminExecutor.execute(Request.Get(_mto));
        HttpResponse httpRespMto = respMto.returnResponse();
        assertNotNull(httpRespMto, "check not null get mto response");
        assertEquals(HttpStatus.SC_OK,
                httpRespMto.getStatusLine().getStatusCode(), "check get mto response status code");

        Response respOtm = adminExecutor.execute(Request.Get(_otm));
        HttpResponse httpRespOtm = respOtm.returnResponse();
        assertNotNull(httpRespOtm, "check not null get otm response");
        assertEquals(HttpStatus.SC_OK,
                httpRespOtm.getStatusLine().getStatusCode(), "check get otm response status code");

        Response respOto = adminExecutor.execute(Request.Get(_oto));
        HttpResponse httpRespOto = respOto.returnResponse();
        assertNotNull(httpRespOto, "check not null get oto response");
        assertEquals(HttpStatus.SC_OK,
                httpRespOto.getStatusLine().getStatusCode(), "check get oto response status code");
    }

    // Undertow 2.4+ enforces RFC 3986: '[', ']', '{', '}' are illegal unencoded in query strings.
    // URI.create() also requires '{' and '}' to be encoded to build a valid URI instance.
    private static String encodeQueryForUri(String query) {
        return query.replace("{", "%7B").replace("}", "%7D")
                    .replace("[", "%5B").replace("]", "%5D");
    }
}
