/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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
import static org.restheart.exchange.ExchangeKeys._AGGREGATIONS;

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
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;

import io.undertow.util.Headers;

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
                + _AGGREGATIONS + "/" + uri,
                new NameValuePair[] {
                        new BasicNameValuePair("avars", "{\"name\": \"a\", \"minage\": 20}")
                });

        resp = adminExecutor.execute(Request.Get(aggrUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check status code");
        assertNotNull(entity.getContentType(), "content type not null");
        assertEquals(Exchange.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue(), "check content type");

        String content = EntityUtils.toString(entity);

        assertNotNull(content, "");

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull(json, "check not null json response");
        assertNotNull(json.get("_embedded"), "check not null _embedded");
        assertTrue(json.get("_embedded").isObject(), "check _embedded");

        assertNotNull(json.get("_embedded").asObject().get("rh:result"), "");
        assertTrue(json.get("_embedded").asObject().get("rh:result").isArray(),
                "check _embedded[\"rh:results\"]");

        JsonArray results = json.get("_embedded").asObject().get("rh:result").asArray();

        assertTrue(results.size() == 1, "check we have 2 results");

        results.values().stream().map((v) -> {
            assertNotNull(v.asObject().get("_id"), "check not null _id property");
            return v;
        }).map((v) -> {
            assertTrue(v.asObject().get("_id").isString(), "check results _id property is string");
            return v;
        }).map((v) -> {
            assertTrue(v.asObject().get("_id").asString().equals("a"), "check results _id property is a");
            return v;
        }).map((v) -> {
            assertNotNull(
                    v.asObject().get("value"), "check not null value property");
            return v;
        }).forEach((v) -> {
            assertTrue(v.asObject().get("value").isNumber(), "check results value property is number");
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

        assertEquals(HttpStatus.SC_BAD_REQUEST, statusLine.getStatusCode(), "check status code");
        assertNotNull(entity.getContentType(), "content type not null");
        assertEquals(Exchange.JSON_MEDIA_TYPE, entity.getContentType().getValue(), "check content type");
    }

    private void createTmpCollection() throws Exception {
        Response resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp db", resp, HttpStatus.SC_CREATED);

        resp = adminExecutor.execute(Request.Put(collectionTmpUri)
                .bodyString("{descr:\"temp coll\"}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put tmp coll", resp, HttpStatus.SC_CREATED);
    }

    private void createMetadataAndTestData(String aggregationsMetadata)
            throws Exception {
        // get the collection etag

        String etag = getEtag(collectionTmpUri);

        // post some data
        String[] data = new String[] {
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
                                    Exchange.HAL_JSON_MEDIA_TYPE));

            check("check aggregation create test data",
                    resp, HttpStatus.SC_CREATED);
        }

        Response resp = adminExecutor
                .execute(Request.Patch(collectionTmpUri)
                        .bodyString(aggregationsMetadata, halCT)
                        .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE)
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

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check status code");
        assertNotNull(entity.getContentType(), "content type not null");
        assertEquals(Exchange.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue(), "check content type");

        String content = EntityUtils.toString(entity);

        assertNotNull(content, "");

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull(json, "check not null json response");
        assertNotNull(json.get("_embedded"), "check not null _embedded");
        assertTrue(json.get("_embedded").isObject(), "check _embedded");

        assertNotNull(json.get("_embedded").asObject().get("rh:result"), "");

        assertTrue(json.get("_embedded").asObject().get("rh:result").isArray(),
                "check _embedded[\"rh:result\"]");

        JsonArray results = json.get("_embedded").asObject().get("rh:result").asArray();

        assertTrue(results.size() == 2, "check we have 2 results");

        results.values().stream().map((v) -> {
            assertNotNull(
                    v.asObject().get("_id"), "check not null _id property");
            return v;
        }).map((v) -> {
            assertTrue(v.asObject().get("_id").isString(), "check results _id property is string");
            return v;
        }).map((v) -> {
            assertNotNull(
                    v.asObject().get("value"), "check not null value property");
            return v;
        }).forEach((v) -> {
            assertTrue(v.asObject().get("value").isNumber(), "check results value property is number");
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

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check status code");
        assertNotNull(entity.getContentType(), "content type not null");
        assertEquals(Exchange.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue(), "check content type");

        String content = EntityUtils.toString(entity);

        assertNotNull(content, "");

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("parsing received json");
        }

        assertNotNull(json, "check not null json");

        assertNotNull(json.get("_etag"), "check not null _etag");
        assertTrue(json.get("_etag").isObject(), "check _etag is object");

        assertNotNull(json.get("_etag").asObject().get("$oid"), "check not null _etag.$oid");

        assertNotNull(json.get("_etag").asObject().get("$oid").isString(), "check _etag.$oid is string");

        return json.get("_etag").asObject().get("$oid").asString();
    }
}
