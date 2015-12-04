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

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import io.undertow.util.Headers;
import java.io.IOException;
import org.restheart.hal.Representation;
import org.restheart.utils.HttpStatus;
import java.net.URI;
import static org.junit.Assert.*;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.restheart.handlers.RequestContext;
import static org.restheart.test.integration.AbstactIT.adminExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class GetAggreationIT extends AbstactIT {
    public GetAggreationIT() {
    }

    @Test
    public void testGetAggregationPipeline() throws Exception {
        String uri = "avg_ages";

        String aggregationsMetadata = "{\"aggregations\": ["
                + "{"
                + "\"type\":\"aggregation\","
                + "\"uri\":\"" + uri + "\","
                + "\"stages\":"
                + "["
                + "{\"_$match\": { \"name\": { \"_$exists\": true}}},"
                + "{\"_$group\":"
                + "{\"_id\": \"$name\", \"value\": {\"_$avg\": \"$age\"} }}"
                + "]"
                + "}]}";

        createTmpCollection();
        createMetadataAndTestData(aggregationsMetadata);
        _testGetAggregation(uri);
    }

    @Test
    public void testGetMapReduce() throws Exception {
        String uri = "avg_ages";

        String aggregationsMetadata = "{\"aggregations\": ["
                + "{"
                + "\"type\":\"mapReduce\","
                + "\"uri\":\"" + uri + "\","
                + "\"map\": \"function() { emit(this.name, this.age) }\"" + ","
                + "\"reduce\":\"function(key, values) { return Array.avg(values) }\"" + ","
                + "\"query\":{\"name\":{\"_$exists\":true}}"
                + "}]}";

        createTmpCollection();
        createMetadataAndTestData(aggregationsMetadata);
        _testGetAggregation(uri);
    }

    @Test
    public void testGetMapReduceWithVariable() throws Exception {
        String uri = "avg_ages";

        String aggregationsMetadata = "{\"aggregations\": ["
                + "{"
                + "\"type\":\"mapReduce\"" + ","
                + "\"uri\": \"" + uri + "\","
                + "\"map\": \"function() { var minage = JSON.parse($vars).minage; if (this.age > minage ) { emit(this.name, this.age); }; }\","
                + "\"reduce\":\"function(key, values) { return Array.avg(values) }\"" + ","
                + "\"query\":{\"name\":{\"_$var\":\"name\"}}"
                + "}]}";

        createTmpCollection();
        createMetadataAndTestData(aggregationsMetadata);

        Response resp;

        URI aggrUri = buildURI("/" + dbTmpName + "/" + collectionTmpName + "/" + RequestContext._AGGREGATIONS + "/" + uri, new NameValuePair[]{
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
        assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
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

    @Test
    public void testUnboundVariable() throws Exception {
        String uri = "avg_ages";

        String aggregationsMetadata = "{\"aggregations\": ["
                + "{"
                + "\"type\":\"mapReduce\"" + ","
                + "\"uri\": \"" + uri + "\","
                + "\"map\": \"function() { emit(this.name, this.age) }\"" + ","
                + "\"reduce\":\"function(key, values) { return Array.avg(values) }\"" + ","
                + "\"query\":{\"name\":{\"_$var\":\"name\"}}"
                + "}]}";

        createTmpCollection();
        createMetadataAndTestData(aggregationsMetadata);

        Response resp;

        URI aggrUri = buildURI("/" + dbTmpName + "/" + collectionTmpName + "/" + RequestContext._AGGREGATIONS + "/" + uri);

        resp = adminExecutor.execute(Request.Get(aggrUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);
        HttpEntity entity = httpResp.getEntity();
        assertNotNull(entity);
        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check status code", HttpStatus.SC_BAD_REQUEST, statusLine.getStatusCode());
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());
    }

    private void createTmpCollection() throws Exception {
        Response resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
        check("check put tmp db", resp, HttpStatus.SC_CREATED);

        resp = adminExecutor.execute(Request.Put(collectionTmpUri)
                .bodyString("{descr:\"temp coll\"}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
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
            "{\"name\":\"b\",\"age\":60}"
        };

        for (String datum : data) {
            Response resp = adminExecutor
                    .execute(Request.Post(collectionTmpUri)
                            .bodyString(datum, halCT)
                            .addHeader(Headers.CONTENT_TYPE_STRING,
                                    Representation.HAL_JSON_MEDIA_TYPE));

            check("check aggregation create test data",
                    resp, HttpStatus.SC_CREATED);
        }

        Response resp = adminExecutor
                .execute(Request.Patch(collectionTmpUri)
                        .bodyString(aggregationsMetadata, halCT)
                        .addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE)
                        .addHeader(Headers.IF_MATCH_STRING, etag));

        check("check update collection with aggregations metadata",
                resp, HttpStatus.SC_OK);
    }

    private void _testGetAggregation(String uri) throws Exception {
        Response resp;

        URI aggrUri = buildURI("/" + dbTmpName + "/" + collectionTmpName + "/" + RequestContext._AGGREGATIONS + "/" + uri);

        resp = adminExecutor.execute(Request.Get(aggrUri));

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
        }).map((v) -> {
            assertTrue("check results value property is number",
                    v.asObject().get("value").isNumber());
            return v;
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
        assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("", content);

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
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
