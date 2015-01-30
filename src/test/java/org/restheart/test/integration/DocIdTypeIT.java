/*
 * RESTHeart - the data REST API server
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
import io.undertow.util.Headers;
import org.restheart.hal.Representation;
import org.restheart.utils.HttpStatus;
import java.net.URI;
import org.apache.http.Header;
import static org.junit.Assert.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Test;
import org.restheart.handlers.RequestContext.DOC_ID_TYPE;
import static org.restheart.handlers.RequestContext.DOC_ID_TYPE_KEY;
import static org.restheart.test.integration.AbstactIT.adminExecutor;
import static org.restheart.test.integration.AbstactIT.collectionTmpUri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class DocIdTypeIT extends AbstactIT {

    private static final Logger LOG = LoggerFactory.getLogger(DocIdTypeIT.class);

    public DocIdTypeIT() {
    }

    @Test
    public void testPostCollectionInt() throws Exception {
        try {
            Response resp;

            // *** PUT tmpdb
            resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check put db", resp, HttpStatus.SC_CREATED);

            // *** PUT tmpcoll
            resp = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check put coll1", resp, HttpStatus.SC_CREATED);

            URI collectionTmpUriInt = new URIBuilder()
                    .setScheme(HTTP)
                    .setHost(HOST)
                    .setPort(conf.getHttpPort())
                    .setPath("/" + dbTmpName + "/" + collectionTmpName)
                    .setParameter(DOC_ID_TYPE_KEY, DOC_ID_TYPE.INT.name())
                    .build();

            // *** POST tmpcoll
            resp = adminExecutor.execute(Request.Post(collectionTmpUriInt).bodyString("{_id:100, a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            HttpResponse httpResp = check("check post coll1 again", resp, HttpStatus.SC_CREATED);

            Header[] headers = httpResp.getHeaders(Headers.LOCATION_STRING);

            assertNotNull("check loocation header", headers);
            assertTrue("check loocation header", headers.length > 0);

            Header locationH = headers[0];
            String location = locationH.getValue();

            //assertTrue("check location header value", location.endsWith("/100?doc_id_type=INT"));

            URI createdDocUri = URI.create(location);

            resp = adminExecutor.execute(Request.Get(createdDocUri).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

            JsonObject content = JsonObject.readFrom(resp.returnContent().asString());
            assertTrue("check created doc content", content.get("_id").asInt() == 100);
            assertNotNull("check created doc content", content.get("_etag"));
            assertNotNull("check created doc content", content.get("a"));
            assertTrue("check created doc content", content.get("a").asInt() == 1);
        } finally {
            mongoClient.dropDatabase(dbTmpName);
        }
    }

    @Test
    public void testPostCollectionString() throws Exception {
        try {
            Response resp;

            // *** PUT tmpdb
            resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check put db", resp, HttpStatus.SC_CREATED);

            // *** PUT tmpcoll
            resp = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            check("check put coll1", resp, HttpStatus.SC_CREATED);

            URI collectionTmpUriInt = new URIBuilder()
                    .setScheme(HTTP)
                    .setHost(HOST)
                    .setPort(conf.getHttpPort())
                    .setPath("/" + dbTmpName + "/" + collectionTmpName)
                    .setParameter(DOC_ID_TYPE_KEY, DOC_ID_TYPE.STRING.name())
                    .build();

            // *** POST tmpcoll
            resp = adminExecutor.execute(Request.Post(collectionTmpUriInt).bodyString("{_id:'54c965cbc2e64568e235b711', a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));
            HttpResponse httpResp = check("check post coll1 again", resp, HttpStatus.SC_CREATED);

            Header[] headers = httpResp.getHeaders(Headers.LOCATION_STRING);

            assertNotNull("check loocation header", headers);
            assertTrue("check loocation header", headers.length > 0);

            Header locationH = headers[0];
            String location = locationH.getValue();

            //assertTrue("check location header value", location.endsWith("/54c965cbc2e64568e235b711?doc_id_type=STRING"));

            URI createdDocUri = URI.create(location);

            resp = adminExecutor.execute(Request.Get(createdDocUri).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

            JsonObject content = JsonObject.readFrom(resp.returnContent().asString());
            assertTrue("check created doc content", content.get("_id").asString().equals("54c965cbc2e64568e235b711"));
            assertNotNull("check created doc content", content.get("_etag"));
            assertNotNull("check created doc content", content.get("a"));
            assertTrue("check created doc content", content.get("a").asInt() == 1);

            // *** filter - case 1 - without detect_oids=false should not find it
            URI collectionTmpUriSearch = new URIBuilder()
                    .setScheme(HTTP)
                    .setHost(HOST)
                    .setPort(conf.getHttpPort())
                    .setPath("/" + dbTmpName + "/" + collectionTmpName)
                    .setParameter("filter", "{'_id':'54c965cbc2e64568e235b711'}")
                    .build();

            resp = adminExecutor.execute(Request.Get(collectionTmpUriSearch).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

            content = JsonObject.readFrom(resp.returnContent().asString());
            assertTrue("check created doc content", content.get("_returned").asInt() == 0);

            // *** filter - case 1 - with detect_oids=false should find it
            collectionTmpUriSearch = new URIBuilder()
                    .setScheme(HTTP)
                    .setHost(HOST)
                    .setPort(conf.getHttpPort())
                    .setPath("/" + dbTmpName + "/" + collectionTmpName)
                    .setParameter("filter", "{'_id':'54c965cbc2e64568e235b711'}")
                    .setParameter("detect_oids", "false")
                    .build();

            resp = adminExecutor.execute(Request.Get(collectionTmpUriSearch).addHeader(Headers.CONTENT_TYPE_STRING, Representation.HAL_JSON_MEDIA_TYPE));

            content = JsonObject.readFrom(resp.returnContent().asString());
            assertTrue("check created doc content", content.get("_returned").asInt() == 1);

        } finally {
            mongoClient.dropDatabase(dbTmpName);
        }
    }
}
