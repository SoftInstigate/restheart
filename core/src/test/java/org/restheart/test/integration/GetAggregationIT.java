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
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import io.undertow.util.Headers;
import java.io.IOException;
import java.net.URI;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import static org.junit.Assert.*;
import org.junit.Test;
import static org.restheart.handlers.exchange.ExchangeKeys._AGGREGATIONS;
import org.restheart.representation.Resource;
import static org.restheart.test.integration.HttpClientAbstactIT.adminExecutor;
import org.restheart.utils.HttpStatus;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class GetAggregationIT extends HttpClientAbstactIT {

    /**
     *
     */
    public GetAggregationIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetAggregationPipeline() throws Exception {
        String uri = "avg_ages";

        String aggregationsMetadata = "{\"aggrs\": ["
                + "{"
                + "\"type\":\"aggregation\","
                + "\"uri\":\"" + uri + "\","
                + "\"stages\":"
                + "["
                + "{\"$match\": { \"name\": { \"$exists\": true}}},"
                + "{\"$group\":"
                + "{\"_id\": \"$name\", \"value\": {\"$avg\": \"$age\"} }}"
                + "]"
                + "}]}";

        createTmpCollection();
        createMetadataAndTestData(aggregationsMetadata);
        _testGetAggregation(uri);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetAggregationPipelineDotNotation() throws Exception {
        String uri = "avg_ages";

        String aggregationsMetadata = "{\"aggrs\": ["
                + "{"
                + "\"type\":\"aggregation\","
                + "\"uri\":\"" + uri + "\","
                + "\"stages\":"
                + "["
                + "{\"$match\": { \"obj.name\": { \"$exists\": true}}},"
                + "{\"$group\":"
                + "{\"_id\": \"$obj.name\", \"value\": {\"$avg\": \"$obj.age\"} }}"
                + "]"
                + "}]}";

        createTmpCollection();
        createMetadataAndTestData(aggregationsMetadata);
        _testGetAggregation(uri);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetMapReduce() throws Exception {
        String uri = "avg_ages";

        String aggregationsMetadata = "{\"aggrs\": ["
                + "{"
                + "\"type\":\"mapReduce\","
                + "\"uri\":\"" + uri + "\","
                + "\"map\": \"function() { emit(this.name, this.age); }\"" + ","
                + "\"reduce\":\"function(key, values) { return Array.avg(values); }\"" + ","
                + "\"query\":{\"name\":{\"$exists\":true}}"
                + "}]}";

        createTmpCollection();
        createMetadataAndTestData(aggregationsMetadata);
        _testGetAggregation(uri);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetMapReduceWithVariable() throws Exception {
        String uri = "avg_ages";

        String aggregationsMetadata = "{\"aggrs\": ["
                + "{"
                + "\"type\":\"mapReduce\"" + ","
                + "\"uri\": \"" + uri + "\","
                + "\"map\": \"function() { var minage = JSON.parse($vars).minage; if (this.age > minage ) { emit(this.name, this.age); }; }\","
                + "\"reduce\":\"function(key, values) { return Array.avg(values); }\"" + ","
                + "\"query\":{\"name\":{\"$var\":\"name\"}}"
                + "}]}";

        createTmpCollection();
        createMetadataAndTestData(aggregationsMetadata);

        Response resp;

        URI aggrUri = buildURI("/" + dbTmpName + "/" + collectionTmpName + "/"
                + _AGGREGATIONS + "/" + uri, new NameValuePair[]{
                    new BasicNameValuePair("avars", "{\"name\": \"a\", \"minage\": 20}")
                });

        resp = adminExecutor.execute(Request.Get(aggrUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Resource.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull("check not null json response", json);
        assertNotNull("check not null _embedded", json.get("_embedded"));
        assertTrue("check _embedded", json.get("_embedded").isObject());

        assertNotNull("", json.get("_embedded").asObject().get("rh:result"));
        assertTrue("check _embedded[\"rh:results\"]",
                json.get("_embedded").asObject().get("rh:result").isArray());

        JsonArray results
                = json.get("_embedded").asObject().get("rh:result").asArray();

        assertTrue("check we have 2 results", results.size() == 1);

        results.values().stream().map((v) -> {
            assertNotNull("check not null _id property",
                    v.asObject().get("_id"));
            return v;
        }).map((v) -> {
            assertTrue("check results _id property is string",
                    v.asObject().get("_id").isString());
            return v;
        }).map((v) -> {
            assertTrue("check results _id property is a",
                    v.asObject().get("_id").asString().equals("a"));
            return v;
        }).map((v) -> {
            assertNotNull("check not null value property",
                    v.asObject().get("value"));
            return v;
        }).forEach((v) -> {
            assertTrue("check results value property is number",
                    v.asObject().get("value").isNumber());
        });
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testUnboundVariable() throws Exception {
        String uri = "avg_ages";

        String aggregationsMetadata = "{\"aggrs\": ["
                + "{"
                + "\"type\":\"mapReduce\"" + ","
                + "\"uri\": \"" + uri + "\","
                + "\"map\": \"function() { emit(this.name, this.age) }\"" + ","
                + "\"reduce\":\"function(key, values) { return Array.avg(values) }\"" + ","
                + "\"query\":{\"name\":{\"$var\":\"name\"}}"
                + "}]}";

        createTmpCollection();
        createMetadataAndTestData(aggregationsMetadata);

        Response resp;

        URI aggrUri = buildURI("/" + dbTmpName
                + "/" + collectionTmpName + "/" + _AGGREGATIONS + "/" + uri);

        resp = adminExecutor.execute(Request.Get(aggrUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_BAD_REQUEST, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Resource.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());
    }

    private void createTmpCollection() throws Exception {
        Response resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put tmp db", resp, HttpStatus.SC_CREATED);

        resp = adminExecutor.execute(Request.Put(collectionTmpUri)
                .bodyString("{descr:\"temp coll\"}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put tmp coll", resp, HttpStatus.SC_CREATED);
    }

    private void createMetadataAndTestData(String aggregationsMetadata)
            throws Exception {
        // get the collection etag

        String etag = getEtag(collectionTmpUri);

        // post some data
        String[] data = new String[]{
            "{\"name\":\"a\",\"age\":10}",
            "{\"name\":\"a\",\"age\":20}",
            "{\"name\":\"a\",\"age\":30}",
            "{\"name\":\"b\",\"age\":40}",
            "{\"name\":\"b\",\"age\":50}",
            "{\"name\":\"b\",\"age\":60}",
            "{\"obj\":{\"name\":\"x\",\"age\":10}}",
            "{\"obj\":{\"name\":\"x\",\"age\":20}}",
            "{\"obj\":{\"name\":\"y\",\"age\":10}}",
            "{\"obj\":{\"name\":\"y\",\"age\":20}}"
        };

        for (String datum : data) {
            Response resp = adminExecutor
                    .execute(Request.Post(collectionTmpUri)
                            .bodyString(datum, halCT)
                            .addHeader(Headers.CONTENT_TYPE_STRING,
                                    Resource.HAL_JSON_MEDIA_TYPE));

            check("check aggregation create test data",
                    resp, HttpStatus.SC_CREATED);
        }

        Response resp = adminExecutor
                .execute(Request.Patch(collectionTmpUri)
                        .bodyString(aggregationsMetadata, halCT)
                        .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE)
                        .addHeader(Headers.IF_MATCH_STRING, etag));

        check("check update collection with aggregations metadata",
                resp, HttpStatus.SC_OK);
    }

    private void _testGetAggregation(String uri) throws Exception {
        Response resp;

        URI aggrUri = buildURI("/" + dbTmpName + "/" + collectionTmpName + "/"
                + _AGGREGATIONS + "/" + uri);

        resp = adminExecutor.execute(Request.Get(aggrUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Resource.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull("check not null json response", json);
        assertNotNull("check not null _embedded", json.get("_embedded"));
        assertTrue("check _embedded", json.get("_embedded").isObject());

        assertNotNull("", json.get("_embedded").asObject().get("rh:result"));

        assertTrue("check _embedded[\"rh:result\"]",
                json.get("_embedded").asObject().get("rh:result").isArray());

        JsonArray results
                = json.get("_embedded").asObject().get("rh:result").asArray();

        assertTrue("check we have 2 results", results.size() == 2);

        results.values().stream().map((v) -> {
            assertNotNull("check not null _id property",
                    v.asObject().get("_id"));
            return v;
        }).map((v) -> {
            assertTrue("check results _id property is string",
                    v.asObject().get("_id").isString());
            return v;
        }).map((v) -> {
            assertNotNull("check not null value property",
                    v.asObject().get("value"));
            return v;
        }).forEach((v) -> {
            assertTrue("check results value property is number",
                    v.asObject().get("value").isNumber());
        });
    }

    private String getEtag(URI uri) throws IOException {
        Response resp = adminExecutor.execute(Request.Get(uri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_OK, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Resource.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull("check not null json", json);

        assertNotNull("check not null _etag", json.get("_etag"));
        assertTrue("check _etag is object", json.get("_etag").isObject());

        assertNotNull("check not null _etag.$oid", json.get("_etag")
                .asObject().get("$oid"));

        assertNotNull("check _etag.$oid is string", json.get("_etag")
                .asObject().get("$oid").isString());

        return json.get("_etag")
                .asObject().get("$oid").asString();
    }
}
