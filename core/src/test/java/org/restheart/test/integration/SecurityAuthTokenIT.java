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
import java.security.Principal;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.BasicUserPrincipal;
import org.apache.http.auth.Credentials;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.restheart.exchange.Exchange;
import org.restheart.utils.HttpStatus;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;

import io.undertow.util.HttpString;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SecurityAuthTokenIT extends HttpClientAbstactIT {

    static final HttpString AUTH_TOKEN_HEADER = HttpString.tryFromString("Auth-Token");
    static final HttpString AUTH_TOKEN_VALID_HEADER = HttpString.tryFromString("Auth-Token-Valid-Until");
    static final HttpString AUTH_TOKEN_LOCATION_HEADER = HttpString.tryFromString("Auth-Token-Location");

    /**
     *
     */
    public SecurityAuthTokenIT() {
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testAuthToken() throws Exception {
        // Get token from /token endpoint
        URI tokenUri = rootUri.resolve("/token");
        Response resp = adminExecutor.execute(Request.Get(tokenUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);

        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check authorized");

        Header[] _authToken = httpResp.getHeaders(AUTH_TOKEN_HEADER.toString());
        Header[] _authTokenValid = httpResp.getHeaders(AUTH_TOKEN_VALID_HEADER.toString());
        Header[] _authTokenLocation = httpResp.getHeaders(AUTH_TOKEN_LOCATION_HEADER.toString());

        assertNotNull(_authToken, "check not null auth token header");
        assertNotNull(_authTokenValid, "check not null auth token valid header");
        assertNotNull(_authTokenLocation, "check not null auth token location header");

        assertTrue(_authToken.length == 1, "check not empty array auth token header array ");
        assertTrue(_authTokenValid.length == 1, "check not empty array auth token valid header array");
        assertTrue(_authTokenLocation.length == 1, "check not empty array auth token location header array");

        assertTrue(_authToken[0] != null && _authToken[0].getValue() != null && !_authToken[0].getValue().isEmpty(),
                "check not empty array auth token header value not null or empty");
        assertTrue(_authTokenValid[0] != null && _authTokenValid[0].getValue() != null
                && !_authTokenValid[0].getValue().isEmpty(),
                "check not empty array auth token valid value not null or empty");
        assertTrue(_authTokenLocation[0] != null && _authTokenLocation[0].getValue() != null
                && !_authTokenLocation[0].getValue().isEmpty(),
                "check not empty array auth token location  not null or empty");

        Response resp2 = unauthExecutor
                .authPreemptive(HTTP_HOST)
                .auth("admin", _authToken[0].getValue())
                .execute(Request.Get(rootUri));

        HttpResponse httpResp2 = resp2.returnResponse();
        assertNotNull(httpResp2);

        StatusLine statusLine2 = httpResp2.getStatusLine();
        assertNotNull(statusLine2);

        assertEquals(HttpStatus.SC_OK, statusLine2.getStatusCode(), "check authorized via auth token");
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testAuthTokenResourceLocation() throws Exception {
        // Get token from /token endpoint
        URI tokenUri = rootUri.resolve("/token");
        Response resp = adminExecutor.execute(Request.Get(tokenUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);

        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check authorized");

        Header[] _authToken = httpResp.getHeaders(AUTH_TOKEN_HEADER.toString());
        Header[] _authTokenValid = httpResp.getHeaders(AUTH_TOKEN_VALID_HEADER.toString());
        Header[] _authTokenLocation = httpResp.getHeaders(AUTH_TOKEN_LOCATION_HEADER.toString());

        assertNotNull(_authToken, "check not null auth token header");
        assertNotNull(_authTokenValid, "check not null auth token valid header");
        assertNotNull(_authTokenLocation, "check not null auth token location header");

        assertTrue(_authToken.length == 1, "check not empty array auth token header array ");
        assertTrue(_authTokenValid.length == 1, "check not empty array auth token valid header");
        assertTrue(_authTokenLocation.length == 1, "check not empty array auth token location header");

        String locationURI = _authTokenLocation[0].getValue();

        URI authTokenResourceUri = rootUri.resolve(locationURI);

        Response resp2 = unauthExecutor.authPreemptive(HTTP_HOST).auth(new Credentials() {
            @Override
            public Principal getUserPrincipal() {
                return new BasicUserPrincipal("admin");
            }

            @Override
            public String getPassword() {
                return _authToken[0].getValue();
            }
        }).execute(Request.Get(authTokenResourceUri));

        HttpResponse httpResp2 = resp2.returnResponse();
        assertNotNull(httpResp2);

        StatusLine statusLine2 = httpResp2.getStatusLine();
        assertNotNull(statusLine2);

        HttpEntity entity = httpResp2.getEntity();
        assertNotNull(entity);

        Header[] _authTokenValid2 = httpResp2.getHeaders(AUTH_TOKEN_VALID_HEADER.toString());

        assertEquals(HttpStatus.SC_OK, statusLine2.getStatusCode(), "check auth token resource URI");

        assertNotNull(entity.getContentType(), "content type not null");
        assertTrue(entity.getContentType().getValue().startsWith(Exchange.HAL_JSON_MEDIA_TYPE)
            || entity.getContentType().getValue().startsWith(Exchange.JSON_MEDIA_TYPE), "check content type");

        String content = EntityUtils.toString(entity);

        assertNotNull(content, "check content of auth token resource");

        JsonObject json = null;

        try {
            json = Json.parse(content).asObject();
        } catch (Throwable t) {
            fail("parsing received json");
        }

        if (json == null) {
            fail("parsing received json");
            json = new JsonObject(); // just to remove complier warning message (json might be null)
        }

        assertNotNull(json.get("access_token"), "check content - access_token not null");
        assertNotNull(json.get("expires_in"), "check content - expires_in not null");

        assertTrue(!json.get("access_token").asString().isEmpty(), "check content - access_token not empty");
        assertTrue(json.get("expires_in").asInt() > 0, "check content - expires_in is positive");

        assertEquals(json.get("access_token").asString(), _authToken[0].getValue());
        assertNotNull(json.get("username"), "check content - username not null");
        assertEquals(json.get("username").asString(), "admin");
    }

    /**
     * JWT tokens are stateless and cannot be truly invalidated.
     * Once a JWT is issued and signed, it remains valid until expiration.
     * The DELETE endpoint removes the token from the cache, but the JWT
     * itself can still be verified and used until it expires.
     * 
     * This test is disabled because JWT invalidation is not feasible
     * without maintaining a token blacklist (which defeats the purpose
     * of stateless tokens).
     * 
     * @throws Exception
     */
    @Test
    @org.junit.jupiter.api.Disabled("JWT tokens cannot be invalidated - they remain valid until expiration")
    public void testAuthTokenInvalidation() throws Exception {
        // Get token from /token endpoint
        URI tokenUri = rootUri.resolve("/token");
        Response resp = adminExecutor.execute(Request.Get(tokenUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);

        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals(HttpStatus.SC_OK, statusLine.getStatusCode(), "check authorized");

        Header[] _authToken = httpResp.getHeaders(AUTH_TOKEN_HEADER.toString());
        Header[] _authTokenValid = httpResp.getHeaders(AUTH_TOKEN_VALID_HEADER.toString());
        Header[] _authTokenLocation = httpResp.getHeaders(AUTH_TOKEN_LOCATION_HEADER.toString());

        assertNotNull(_authToken, "check not null auth token header");
        assertNotNull(_authTokenValid, "check not null auth token valid header");
        assertNotNull(_authTokenLocation, "check not null auth token location header");

        assertTrue(_authToken.length == 1, "check not empty array auth token header array ");
        assertTrue(_authTokenValid.length == 1, "check not empty array auth token valid header");
        assertTrue(_authTokenLocation.length == 1, "check not empty array auth token location header");

        String locationURI = _authTokenLocation[0].getValue();

        URI authTokenResourceUri = rootUri.resolve(locationURI);

        Response resp2 = unauthExecutor.auth(new Credentials() {
            @Override
            public Principal getUserPrincipal() {
                return new BasicUserPrincipal("admin");
            }

            @Override
            public String getPassword() {
                return _authToken[0].getValue();
            }
        }).execute(Request.Delete(authTokenResourceUri));

        HttpResponse httpResp2 = resp2.returnResponse();
        assertNotNull(httpResp2);

        StatusLine statusLine2 = httpResp2.getStatusLine();
        assertNotNull(statusLine2);

        assertEquals(HttpStatus.SC_NO_CONTENT, statusLine2.getStatusCode(), "check auth token resource URI");

        Response resp3 = unauthExecutor.auth(new Credentials() {
            @Override
            public Principal getUserPrincipal() {
                return new BasicUserPrincipal("admin");
            }

            @Override
            public String getPassword() {
                return _authToken[0].getValue();
            }
        }).execute(Request.Get(rootUri));

        HttpResponse httpResp3 = resp3.returnResponse();
        assertNotNull(httpResp3);

        StatusLine statusLine3 = httpResp3.getStatusLine();
        assertNotNull(statusLine3);

        assertEquals(HttpStatus.SC_UNAUTHORIZED, statusLine3.getStatusCode(), "check auth token resource URI");
    }
}
