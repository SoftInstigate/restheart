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

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

import io.undertow.util.Headers;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SecurityIT extends HttpClientAbstactIT {

    /**
     *
     */
    public static String JWT_AUTH_HEADER_PREFIX = "Bearer ";

    /**
     *
     */
    public static final String SILENT_HEADER_KEY = "No-Auth-Challenge";

    /**
     *
     */
    public static final String SILENT_QUERY_PARAM_KEY = "noauthchallenge";

    /**
     *
     */
    public SecurityIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testAuthentication() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(rootUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);

        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check authorized");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetUnauthenticated() throws Exception {
        // *** GET root
        Response resp = unauthExecutor.execute(Request.Get(rootUri));
        check("check get root unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** GET db
        resp = unauthExecutor.execute(Request.Get(dbUri));
        check("check get db unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** GET coll1
        resp = unauthExecutor.execute(Request.Get(collection1Uri));
        check("check get coll1 unauthorized", resp, HttpStatus.SC_OK);

        // *** GET doc1
        resp = unauthExecutor.execute(Request.Get(document1Uri));
        check("check get doc1 unauthorized", resp, HttpStatus.SC_OK);

        // *** GET coll2
        resp = unauthExecutor.execute(Request.Get(collection2Uri));
        check("check get coll2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** GET doc2
        resp = unauthExecutor.execute(Request.Get(document2Uri));
        check("check get doc2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** GET root with silent authorization (no auth challenge must be sent)
        resp = unauthExecutor.execute(Request.Get(rootUri).addHeader(SILENT_HEADER_KEY, ""));
        check("check get root unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        resp = unauthExecutor.execute(Request.Get(rootUri).addHeader(SILENT_HEADER_KEY, ""));
        HttpResponse httpResp = resp.returnResponse();

        assertTrue(httpResp.getHeaders(Headers.WWW_AUTHENTICATE_STRING).length == 0,
                "check get root unauthorized silent");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPostUnauthenticated() throws Exception {
        // *** POST coll1
        Response resp = unauthExecutor.execute(Request.Post(collection1Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check post coll1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** GET coll2
        resp = unauthExecutor.execute(Request.Post(collection2Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check post coll2b unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPutUnauthenticated() throws Exception {
        // *** PUT root
        Response resp = unauthExecutor.execute(Request.Put(rootUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put root unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PUT db
        resp = unauthExecutor.execute(Request.Put(dbUri).bodyString("{a:1}", halCT));
        check("check put db unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PUT coll1
        resp = unauthExecutor.execute(Request.Put(collection1Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put coll1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PUT doc1
        resp = unauthExecutor.execute(Request.Put(document1Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put doc1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PUT coll2
        resp = unauthExecutor.execute(Request.Put(collection2Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put coll2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PUT doc2
        resp = unauthExecutor.execute(Request.Put(document2Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put doc2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPatchUnauthenticated() throws Exception {
        // *** PATCH root
        Response resp = unauthExecutor.execute(Request.Patch(rootUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check patch root unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PATCH db
        resp = unauthExecutor.execute(Request.Patch(dbUri).bodyString("{a:1}", halCT));
        check("check patch db unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PATCH coll1
        resp = unauthExecutor.execute(Request.Patch(collection1Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check patch coll1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PATCH doc1
        resp = unauthExecutor.execute(Request.Patch(document1Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check patch doc1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PATCH coll2
        resp = unauthExecutor.execute(Request.Patch(collection2Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check patch coll2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PATCH doc2
        resp = unauthExecutor.execute(Request.Patch(document2Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check patch doc2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testDeleteUnauthenticated() throws Exception {
        // *** DELETE root
        Response resp = unauthExecutor.execute(Request.Delete(rootUri));
        check("check delete root unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** DELETE db
        resp = unauthExecutor.execute(Request.Delete(dbUri));
        check("check delete db unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** DELETE coll1
        resp = unauthExecutor.execute(Request.Delete(collection1Uri));
        check("check delete coll1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** DELETE doc1
        resp = unauthExecutor.execute(Request.Delete(document1Uri));
        check("check delete doc1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** DELETE coll2
        resp = unauthExecutor.execute(Request.Delete(collection2Uri));
        check("check delete coll2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** DELETE doc2
        resp = unauthExecutor.execute(Request.Delete(document2Uri));
        check("check delete doc2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetAsAdmin() throws Exception {
        // *** GET root
        Response resp = adminExecutor.execute(Request.Get(rootUri));
        check("check get root as admin", resp, HttpStatus.SC_OK);

        // *** GET db
        resp = adminExecutor.execute(Request.Get(dbUri));
        check("check get db as admin", resp, HttpStatus.SC_OK);

        // *** GET coll1
        resp = adminExecutor.execute(Request.Get(collection1Uri));
        check("check get coll1 as admin", resp, HttpStatus.SC_OK);

        // *** GET doc1
        resp = adminExecutor.execute(Request.Get(document1Uri));
        check("check get doc1 as admin", resp, HttpStatus.SC_OK);

        // *** GET coll2
        resp = adminExecutor.execute(Request.Get(collection2Uri));
        check("check get coll2 as admin", resp, HttpStatus.SC_OK);

        // *** GET doc2
        resp = adminExecutor.execute(Request.Get(document2Uri));
        check("check get doc2 as admin", resp, HttpStatus.SC_OK);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPostAsAdmin() throws Exception {
        // *** POST coll1
        Response resp = adminExecutor.execute(Request.Post(collection1Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check post coll1 as admin", resp, HttpStatus.SC_CREATED);

        // *** POST coll2
        resp = adminExecutor.execute(Request.Post(collection2Uri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check post coll2b asadmin", resp, HttpStatus.SC_CREATED);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPutAsAdmin() throws Exception {
        // *** PUT root
        Response resp = adminExecutor.execute(Request.Put(rootUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put root as admin", resp, HttpStatus.SC_METHOD_NOT_ALLOWED);

        // *** PUT tmpdb
        resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put db as admin", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        resp = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put coll1 as admin", resp, HttpStatus.SC_CREATED);

        // *** PUT doc1
        resp = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put doc1 as admin", resp, HttpStatus.SC_CREATED);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testGetAsPowerUser() throws Exception {
        // *** GET root
        Response resp = user1Executor.execute(Request.Get(rootUri));
        check("check get root as user1", resp, HttpStatus.SC_FORBIDDEN);

        // *** GET db
        resp = user1Executor.execute(Request.Get(dbUri));
        check("check get db as user1", resp, HttpStatus.SC_OK);

        // *** GET coll1
        resp = user1Executor.execute(Request.Get(collection1Uri));
        check("check get coll1 as user1", resp, HttpStatus.SC_OK);

        // *** GET doc1
        resp = user1Executor.execute(Request.Get(document1Uri));
        check("check get doc1 as user1", resp, HttpStatus.SC_OK);

        // *** GET coll2
        resp = user1Executor.execute(Request.Get(collection2Uri));
        check("check get coll2 as user1", resp, HttpStatus.SC_OK);

        // *** GET doc2
        resp = user1Executor.execute(Request.Get(document2Uri));
        check("check get doc2 as user1", resp, HttpStatus.SC_OK);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPutAsPowerUser() throws Exception {
        // *** PUT root
        Response resp = user1Executor.execute(Request.Put(rootUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put root as user1", resp, HttpStatus.SC_FORBIDDEN);

        // *** PUT db
        resp = user1Executor.execute(Request.Put(dbUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put db as user1", resp, HttpStatus.SC_FORBIDDEN);

        // *** PUT tmpdb
        resp = user1Executor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put db as user1", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        resp = user1Executor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put coll1 as user1", resp, HttpStatus.SC_CREATED);

        // *** PUT doc1
        resp = user1Executor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check put doc1 as user1", resp, HttpStatus.SC_CREATED);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPathPefixAndRegexPredicates() throws Exception {
        // *** create dbs
        Response resp = user2Executor.execute(Request.Put(dbTmpUri2).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check create " + dbTmpName2 + " as user2", resp, HttpStatus.SC_CREATED);

        resp = user2Executor.execute(Request.Put(dbTmpUri3).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check create " + dbTmpName3 + " as user2", resp, HttpStatus.SC_CREATED);

        // *** create user collection
        resp = user2Executor.execute(Request.Put(collectionTmpUserUri2).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check path predicate creating user collection " + collectionTmpUserUri2 + " as user2", resp,
                HttpStatus.SC_CREATED);

        // *** create user collection
        resp = user2Executor.execute(Request.Put(collectionTmpUserUri3).bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Exchange.HAL_JSON_MEDIA_TYPE));
        check("check regex predicate creating user collection " + collectionTmpUserUri3 + " as user2", resp,
                HttpStatus.SC_CREATED);
    }
}
