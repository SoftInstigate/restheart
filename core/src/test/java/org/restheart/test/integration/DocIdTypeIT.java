/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.mashape.unirest.http.Unirest;
import io.undertow.util.Headers;
import java.net.URI;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.restheart.handlers.exchange.ExchangeKeys.DOC_ID_TYPE;
import static org.restheart.handlers.exchange.ExchangeKeys.DOC_ID_TYPE_QPARAM_KEY;
import org.restheart.representation.Resource;
import static org.restheart.test.integration.AbstactIT.url;
import static org.restheart.test.integration.HttpClientAbstactIT.adminExecutor;
import static org.restheart.test.integration.HttpClientAbstactIT.collectionTmpUri;
import org.restheart.utils.HttpStatus;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class DocIdTypeIT extends HttpClientAbstactIT {

    private final String DB = TEST_DB_PREFIX + "-id-db";
    private final String COLL = "coll";

    com.mashape.unirest.http.HttpResponse<String> resp;

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
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put mytmpdb", resp1, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        Response resp2 = adminExecutor.execute(Request.Put(collectionTmpUri)
                .bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put mytmpdb.tmpcoll", resp2, HttpStatus.SC_CREATED);

        URI collectionTmpUriInt = buildURI("/" + dbTmpName + "/" + collectionTmpName,
                new NameValuePair[]{
                    new BasicNameValuePair(DOC_ID_TYPE_QPARAM_KEY, DOC_ID_TYPE.NUMBER.name())
                });

        // *** POST tmpcoll
        Response resp3 = adminExecutor.execute(Request.Post(collectionTmpUriInt)
                .bodyString("{_id:100, a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        HttpResponse httpResp = check("check post coll1 again", resp3, HttpStatus.SC_CREATED);

        Header[] headers = httpResp.getHeaders(Headers.LOCATION_STRING);

        assertNotNull("check loocation header", headers);
        assertTrue("check loocation header", headers.length > 0);

        Header locationH = headers[0];
        String location = locationH.getValue();

        assertTrue("check location header value", location.endsWith("/100?id_type=NUMBER"));

        URI createdDocUri = URI.create(location);

        Response resp4 = adminExecutor.execute(Request.Get(createdDocUri)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        JsonObject content = Json.parse(resp4.returnContent().asString()).asObject();
        assertTrue("check created doc content", content.get("_id").asInt() == 100);
        assertNotNull("check created doc content", content.get("_etag"));
        assertNotNull("check created doc content", content.get("a"));
        assertTrue("check created doc content", content.get("a").asInt() == 1);
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
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put mytmpdb", resp1, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        Response resp2 = adminExecutor.execute(Request.Put(collectionTmpUri)
                .bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put mytmpdb.tmpcoll", resp2, HttpStatus.SC_CREATED);

        URI collectionTmpUriInt = buildURI("/" + dbTmpName + "/" + collectionTmpName,
                new NameValuePair[]{
                    new BasicNameValuePair(DOC_ID_TYPE_QPARAM_KEY, DOC_ID_TYPE.STRING.name())
                });

        // *** POST tmpcoll
        Response resp3 = adminExecutor.execute(Request.Post(collectionTmpUriInt)
                .bodyString("{_id:{'$oid':'54c965cbc2e64568e235b711'}, a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        HttpResponse httpResp = check("check post coll1 again", resp3, HttpStatus.SC_CREATED);

        Header[] headers = httpResp.getHeaders(Headers.LOCATION_STRING);

        assertNotNull("check loocation header", headers);
        assertTrue("check loocation header", headers.length > 0);

        Header locationH = headers[0];
        String location = locationH.getValue();

        //assertTrue("check location header value", location.endsWith("/54c965cbc2e64568e235b711?id_type=STRING"));
        URI createdDocUri = URI.create(location);

        Response resp4 = adminExecutor.execute(Request.Get(createdDocUri)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        JsonObject content = Json.parse(resp4.returnContent().asString()).asObject();
        assertTrue("check created doc content", content.get("_id").asObject().get("$oid").asString()
                .equals("54c965cbc2e64568e235b711"));
        assertNotNull("check created doc content", content.get("_etag"));
        assertNotNull("check created doc content", content.get("a"));
        assertTrue("check created doc content", content.get("a").asInt() == 1);

        // *** filter - case 1 - with string id should not find it
        URI collectionTmpUriSearch = buildURI("/" + dbTmpName + "/" + collectionTmpName,
                new NameValuePair[]{
                    new BasicNameValuePair("filter", "{'_id':'54c965cbc2e64568e235b711'}")
                });

        Response resp5 = adminExecutor.execute(Request.Get(collectionTmpUriSearch)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        content = Json.parse(resp5.returnContent().asString()).asObject();
        assertTrue("check created doc content", content.get("_returned").asInt() == 0);

        // *** filter - case 1 - with oid id should find it
        collectionTmpUriSearch = buildURI("/" + dbTmpName + "/" + collectionTmpName,
                new NameValuePair[]{
                    new BasicNameValuePair("filter", "{'_id':{'$oid':'54c965cbc2e64568e235b711'}}")
                });

        Response resp6 = adminExecutor.execute(Request.Get(collectionTmpUriSearch)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        content = Json.parse(resp6.returnContent().asString()).asObject();
        assertTrue("check created doc content", content.get("_returned").asInt() == 1);
    }

    /**
     *
     * @throws Exception
     */
    @Before
    public void createTestData() throws Exception {
        // create test db
        resp = Unirest.put(url(DB))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("create db " + DB,
                org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        // create parent collection
        resp = Unirest.put(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .asString();

        Assert.assertEquals("create collection ".concat(DB.concat("/").concat(COLL)),
                org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());
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

        Assert.assertEquals("create doc with complex id ",
                org.apache.http.HttpStatus.SC_CREATED, resp.getStatus());

        resp = Unirest.get(url(DB, COLL))
                .basicAuth(ADMIN_ID, ADMIN_PWD)
                .header("content-type", "application/json")
                .queryString("filter", "{'_id':{'a':1,'b':2}}")
                .queryString("count", "1")
                .queryString("rep", "pj")
                .asString();

        JsonValue rbody = Json.parse(resp.getBody());

        Assert.assertTrue("check doc",
                rbody != null
                && rbody.isObject()
                && rbody.asObject().get("_size") != null
                && rbody.asObject().get("_size").isNumber()
                && rbody.asObject().get("_size").asInt() == 1);
    }
}
