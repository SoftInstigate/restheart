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
import static org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE_QPARAM_KEY;

import java.net.URI;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.message.BasicNameValuePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.exchange.ExchangeKeys.DOC_ID_TYPE;
import org.restheart.utils.HttpStatus;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import kong.unirest.Unirest;

import io.undertow.util.Headers;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DocIdTypeIT extends HttpClientAbstactIT {

    private final String DB = TEST_DB_PREFIX + "-id-db";
    private final String COLL = "coll";

    kong.unirest.HttpResponse<String> resp;

    /**
     *
     */
    public DocIdTypeIT() {
        super();
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPostCollectionInt() throws Exception {
        // *** PUT tmpdb
        Response resp1 = adminExecutor.execute(Request.Put(dbTmpUri)
                .bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put mytmpdb", resp1, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        Response resp2 = adminExecutor.execute(Request.Put(collectionTmpUri)
                .bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put mytmpdb.tmpcoll", resp2, HttpStatus.SC_CREATED);

        URI collectionTmpUriInt = buildURI("/" + dbTmpName + "/" + collectionTmpName,
                new NameValuePair[] {
                        new BasicNameValuePair(DOC_ID_TYPE_QPARAM_KEY,
                                DOC_ID_TYPE.NUMBER.name())
                });

        // *** POST tmpcoll
        Response resp3 = adminExecutor.execute(Request.Post(collectionTmpUriInt)
                .bodyString("{_id:100, a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        HttpResponse httpResp = check("check post coll1 again", resp3, HttpStatus.SC_CREATED);

        Header[] headers = httpResp.getHeaders(Headers.LOCATION_STRING);

        assertNotNull(headers, "check loocation header");
        assertTrue(headers.length > 0, "check loocation header");

        Header locationH = headers[0];
        String location = locationH.getValue();

        assertTrue(location.endsWith("/100?id_type=NUMBER"), "check location header value");

        URI createdDocUri = URI.create(location);

        Response resp4 = adminExecutor.execute(Request.Get(createdDocUri)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        JsonObject content = Json.parse(resp4.returnContent().asString()).asObject();
        assertTrue(content.get("_id").asInt() == 100, "check created doc content");
        assertNotNull(content.get("_etag"), "check created doc content");
        assertNotNull(content.get("a"), "check created doc content");
        assertTrue(content.get("a").asInt() == 1, "check created doc content");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPostCollectionString() throws Exception {
        // *** PUT tmpdb
        Response resp1 = adminExecutor.execute(Request.Put(dbTmpUri)
                .bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put mytmpdb", resp1, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        Response resp2 = adminExecutor.execute(Request.Put(collectionTmpUri)
                .bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put mytmpdb.tmpcoll", resp2, HttpStatus.SC_CREATED);

        URI collectionTmpUriInt = buildURI("/" + dbTmpName + "/" + collectionTmpName,
                new NameValuePair[] {
                        new BasicNameValuePair(DOC_ID_TYPE_QPARAM_KEY,
                                DOC_ID_TYPE.STRING.name())
                });

        // *** POST tmpcoll
        Response resp3 = adminExecutor.execute(Request.Post(collectionTmpUriInt)
                .bodyString("{_id:{'$oid':'54c965cbc2e64568e235b711'}, a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        HttpResponse httpResp = check("check post coll1 again", resp3, HttpStatus.SC_CREATED);

        Header[] headers = httpResp.getHeaders(Headers.LOCATION_STRING);

        assertNotNull(headers, "check loocation header");
        assertTrue(headers.length > 0, "check loocation header");

        Header locationH = headers[0];
        String location = locationH.getValue();

        // assertTrue(// location.endsWith("/54c965cbc2e64568e235b711?id_type=STRING"),
        // "check location header value");
        URI createdDocUri = URI.create(location);

        Response resp4 = adminExecutor.execute(Request.Get(createdDocUri)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        JsonObject content = Json.parse(resp4.returnContent().asString()).asObject();
        assertTrue(content.get("_id").asObject().get("$oid").asString()
                .equals("54c965cbc2e64568e235b711"), "check created doc content");
        assertNotNull(content.get("_etag"), "check created doc content");
        assertNotNull(content.get("a"), "check created doc content");
        assertTrue(content.get("a").asInt() == 1, "check created doc content");

        // *** filter - case 1 - with string id should not find it
        URI collectionTmpUriSearch = buildURI("/" + dbTmpName + "/" + collectionTmpName,
                new NameValuePair[] {
                        new BasicNameValuePair("filter", "{'_id':'54c965cbc2e64568e235b711'}")
                });

        Response resp5 = adminExecutor.execute(Request.Get(collectionTmpUriSearch)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        content = Json.parse(resp5.returnContent().asString()).asObject();
        assertTrue(content.get("_returned").asInt() == 0, "check created doc content");

        // *** filter - case 1 - with oid id should find it
        collectionTmpUriSearch = buildURI("/" + dbTmpName + "/" + collectionTmpName,
                new NameValuePair[] {
                        new BasicNameValuePair("filter",
                                "{'_id':{'$oid':'54c965cbc2e64568e235b711'}}")
                });

        Response resp6 = adminExecutor.execute(Request.Get(collectionTmpUriSearch)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));

        content = Json.parse(resp6.returnContent().asString()).asObject();
        assertTrue(content.get("_returned").asInt() == 1, "check created doc content");
    }

    /**
     *
     * @throws Exception
     */
    @BeforeEach
    public void createTestData() throws Exception {
        // create test db
        resp = Unirest.put(url(DB))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(), "create db " + DB);

        // create parent collection
        resp = Unirest.put(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(),
                "create collection ".concat(DB.concat("/").concat(COLL)));
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testComplexId() throws Exception {
        String body = "{'_id':{'a':1,'b':2}}";

        resp = Unirest.post(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .body(body)
                .asString();

        assertEquals(org.apache.http.HttpStatus.SC_CREATED, resp.getStatus(), "create doc with complex id ");

        resp = Unirest.get(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .queryString("filter", "{'_id':{'a':1,'b':2}}")
                .queryString("count", "1")
                .queryString("rep", "pj")
                .asString();

        JsonValue rbody = Json.parse(resp.getBody());

        assertTrue(
                rbody != null
                        && rbody.isObject()
                        && rbody.asObject().get("_size") != null
                        && rbody.asObject().get("_size").isNumber()
                        && rbody.asObject().get("_size").asInt() == 1,
                "check doc");
    }
}
