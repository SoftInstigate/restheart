/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
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

import com.mashape.unirest.http.Unirest;
import io.undertow.util.Headers;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Test;
import org.restheart.representation.Resource;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@Ignore
public class SecurityIT extends HttpClientAbstactIT {
    public static String JWT_AUTH_HEADER_PREFIX = "Bearer ";
    public static final String SILENT_HEADER_KEY = "No-Auth-Challenge";
    public static final String SILENT_QUERY_PARAM_KEY = "noauthchallenge";

    public SecurityIT() {
    }

    @Test
    public void testAuthentication() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(rootUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);

        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check authorized", HttpStatus.SC_OK, statusLine.getStatusCode());
    }

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

        assertTrue("check get root unauthorized silent", httpResp.getHeaders(Headers.WWW_AUTHENTICATE_STRING).length == 0);
    }

    @Test
    public void testPostUnauthenticated() throws Exception {
        // *** POST coll1
        Response resp = unauthExecutor.execute(Request.Post(collection1Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check post coll1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** GET coll2
        resp = unauthExecutor.execute(Request.Post(collection2Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check post coll2b unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);
    }

    @Test
    public void testPutUnauthenticated() throws Exception {
        // *** PUT root
        Response resp = unauthExecutor.execute(Request.Put(rootUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put root unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PUT db
        resp = unauthExecutor.execute(Request.Put(dbUri).bodyString("{a:1}", halCT));
        check("check put db unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PUT coll1
        resp = unauthExecutor.execute(Request.Put(collection1Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put coll1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PUT doc1
        resp = unauthExecutor.execute(Request.Put(document1Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put doc1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PUT coll2
        resp = unauthExecutor.execute(Request.Put(collection2Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put coll2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PUT doc2
        resp = unauthExecutor.execute(Request.Put(document2Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put doc2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);
    }

    @Test
    public void testPatchUnauthenticated() throws Exception {
        // *** PATCH root
        Response resp = unauthExecutor.execute(Request.Patch(rootUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check patch root unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PATCH db
        resp = unauthExecutor.execute(Request.Patch(dbUri).bodyString("{a:1}", halCT));
        check("check patch db unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PATCH coll1
        resp = unauthExecutor.execute(Request.Patch(collection1Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check patch coll1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PATCH doc1
        resp = unauthExecutor.execute(Request.Patch(document1Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check patch doc1 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PATCH coll2
        resp = unauthExecutor.execute(Request.Patch(collection2Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check patch coll2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);

        // *** PATCH doc2
        resp = unauthExecutor.execute(Request.Patch(document2Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check patch doc2 unauthorized", resp, HttpStatus.SC_UNAUTHORIZED);
    }

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

    @Test
    public void testPostAsAdmin() throws Exception {
        // *** POST coll1
        Response resp = adminExecutor.execute(Request.Post(collection1Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check post coll1 as admin", resp, HttpStatus.SC_CREATED);

        // *** POST coll2
        resp = adminExecutor.execute(Request.Post(collection2Uri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check post coll2b asadmin", resp, HttpStatus.SC_CREATED);
    }

    @Test
    public void testPutAsAdmin() throws Exception {
        // *** PUT root
        Response resp = adminExecutor.execute(Request.Put(rootUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put root as admin", resp, HttpStatus.SC_METHOD_NOT_ALLOWED);

        // *** PUT tmpdb
        resp = adminExecutor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put db as admin", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        resp = adminExecutor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put coll1 as admin", resp, HttpStatus.SC_CREATED);

        // *** PUT doc1
        resp = adminExecutor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put doc1 as admin", resp, HttpStatus.SC_CREATED);
    }

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

    @Test
    public void testPutAsPowerUser() throws Exception {
        // *** PUT root
        Response resp = user1Executor.execute(Request.Put(rootUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put root as user1", resp, HttpStatus.SC_FORBIDDEN);

        // *** PUT db
        resp = user1Executor.execute(Request.Put(dbUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put db as user1", resp, HttpStatus.SC_FORBIDDEN);

        // *** PUT tmpdb
        resp = user1Executor.execute(Request.Put(dbTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put db as user1", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll
        resp = user1Executor.execute(Request.Put(collectionTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put coll1 as user1", resp, HttpStatus.SC_CREATED);

        // *** PUT doc1
        resp = user1Executor.execute(Request.Put(documentTmpUri).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put doc1 as user1", resp, HttpStatus.SC_CREATED);
    }

    @Test
    public void testPathPefixAndRegexPredicates() throws Exception {
        // *** create dbs
        Response resp = user2Executor.execute(Request.Put(dbTmpUri2).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check create " + dbTmpName2 + " as user2", resp, HttpStatus.SC_CREATED);

        resp = user2Executor.execute(Request.Put(dbTmpUri3).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check create " + dbTmpName3 + " as user2", resp, HttpStatus.SC_CREATED);

        // *** create user collection
        resp = user2Executor.execute(Request.Put(collectionTmpUserUri2).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check path predicate creating user collection " + collectionTmpUserUri2 + " as user2", resp, HttpStatus.SC_CREATED);

        // *** create user collection
        resp = user2Executor.execute(Request.Put(collectionTmpUserUri3).bodyString("{a:1}", halCT).addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check regex predicate creating user collection " + collectionTmpUserUri3 + " as user2", resp, HttpStatus.SC_CREATED);
    }

    @Test
    @Ignore
    public void testJwtAuthentication() throws Exception {
        com.mashape.unirest.http.HttpResponse<String> resp;
        
        // JWT tokens signedsigned with HS256 and key secret
        
        /**
         * {
         * "iss": "myIssuer", "iat": 1519201622, "exp": 2529044822, "aud":
         * "myAudience", "sub": "theAdmin", "roles": "admins" }
         */
        String JWT_ADMIN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJteUlzc3VlciIsImlhdCI6MTUxOTIwMTYyMiwiZXhwIjoyNTI5MDQ0ODIyLCJhdWQiOiJteUF1ZGllbmNlIiwic3ViIjoidGhlQWRtaW4iLCJyb2xlcyI6ImFkbWlucyJ9.oQS55i_cg-cX-fqGCu-G8S-dF8KNOQo77WIy2LrJvKU";

        /**
         * {
         * "iss": "myIssuer", "iat": 1519201622, "exp": 2526366422, "aud":
         * "myAudience", "sub": "theAdmin" }
         */
        String JWT_NO_ROLES = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJteUlzc3VlciIsImlhdCI6MTUxOTIwMTYyMiwiZXhwIjoyNTI2MzY2NDIyLCJhdWQiOiJteUF1ZGllbmNlIiwic3ViIjoidGhlQWRtaW4ifQ.1K35oCjo9Jx1KD_6XC38didmbgjh4TWgq8F7B-5gVTk";

        /**
         * {
         * "iss": "myIssuer", "iat": 1519201622, "exp": 2526366422, "aud":
         * "wrongAudience", "sub": "theAdmin", "roles": "admins" }
         */
        String JWT_WRONG_AUDIENCE = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJteUlzc3VlciIsImlhdCI6MTUxOTIwMTYyMiwiZXhwIjoyNTI2MzY2NDIyLCJhdWQiOiJ3cm9uZ0F1ZGllbmNlIiwic3ViIjoidGhlQWRtaW4iLCJyb2xlcyI6ImFkbWlucyJ9.hXjTQl3X6lWSJklZxG1EnlZifxVrqLRHa_YzkdZ5gBw";

        String JWT_EXPIRED = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJteUlzc3VlciIsImlhdCI6MTUxNjUyMzIyMiwiZXhwIjoxNTE2NTIzMjIyLCJhdWQiOiJteUF1ZGllbmNlIiwic3ViIjoidGhlQWRtaW4iLCJyb2xlcyI6ImFkbWlucyJ9.jzPLp9apzo3gn0U6kWIuvJBpxTUwkmYTBYstfboJkL8";

        resp = Unirest.get(rootUri.toString())
                .header(HttpHeaders.AUTHORIZATION, JWT_AUTH_HEADER_PREFIX + JWT_ADMIN)
                .asString();

        Assert.assertEquals("get / with valid JWT", HttpStatus.SC_OK, resp.getStatus());

        resp = Unirest.get(rootUri.toString())
                .header(HttpHeaders.AUTHORIZATION, JWT_AUTH_HEADER_PREFIX + JWT_WRONG_AUDIENCE)
                .asString();

        Assert.assertEquals("get / with wrong JWT (wrong audience)", HttpStatus.SC_UNAUTHORIZED, resp.getStatus());

        resp = Unirest.get(rootUri.toString())
                .header(HttpHeaders.AUTHORIZATION, JWT_AUTH_HEADER_PREFIX + JWT_EXPIRED)
                .asString();

        Assert.assertEquals("get / with wrong JWT (expired)", HttpStatus.SC_UNAUTHORIZED, resp.getStatus());
        
        resp = Unirest.get(rootUri.toString())
                .header(HttpHeaders.AUTHORIZATION, JWT_AUTH_HEADER_PREFIX + JWT_NO_ROLES)
                .asString();

        Assert.assertEquals("get / with valid JWT but no roles", HttpStatus.SC_FORBIDDEN, resp.getStatus());
    }
}
