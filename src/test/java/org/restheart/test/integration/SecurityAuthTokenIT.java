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

import com.eclipsesource.json.JsonObject;
import org.restheart.utils.HttpStatus;
import java.net.URI;
import java.security.Principal;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import static org.junit.Assert.*;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.BasicUserPrincipal;
import org.apache.http.auth.Credentials;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.restheart.hal.Representation;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_HEADER;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.security.handlers.IAuthToken.AUTH_TOKEN_VALID_HEADER;
import static org.restheart.test.integration.AbstactIT.HTTP;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SecurityAuthTokenIT extends AbstactIT {

    public SecurityAuthTokenIT() {
    }

    @Test
    public void testAuthToken() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(rootUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);

        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check authorized", HttpStatus.SC_OK, statusLine.getStatusCode());

        Header[] _authToken = httpResp.getHeaders(AUTH_TOKEN_HEADER.toString());
        Header[] _authTokenValid = httpResp.getHeaders(AUTH_TOKEN_VALID_HEADER.toString());
        Header[] _authTokenLocation = httpResp.getHeaders(AUTH_TOKEN_LOCATION_HEADER.toString());

        assertNotNull("check not null auth token header", _authToken);
        assertNotNull("check not null auth token valid header", _authTokenValid);
        assertNotNull("check not null auth token location header", _authTokenLocation);

        assertTrue("check not empty array auth token header array ", _authToken.length == 1);
        assertTrue("check not empty array auth token valid header array", _authTokenValid.length == 1);
        assertTrue("check not empty array auth token location header array", _authTokenLocation.length == 1);
        
        assertTrue("check not empty array auth token header value not null or empty", _authToken[0] != null && _authToken[0].getValue() != null && !_authToken[0].getValue().isEmpty());
        assertTrue("check not empty array auth token valid value not null or empty",  _authTokenValid[0] != null && _authTokenValid[0].getValue() != null && !_authTokenValid[0].getValue().isEmpty());
        assertTrue("check not empty array auth token location  not null or empty", _authTokenLocation[0] != null && _authTokenLocation[0].getValue() != null && !_authTokenLocation[0].getValue().isEmpty());

        Response resp2 = unauthExecutor.auth(new Credentials() {
            @Override
            public Principal getUserPrincipal() {
                return new BasicUserPrincipal("admin");
            }

            @Override
            public String getPassword() {
                return _authToken[0].getValue();
            }
        }).execute(Request.Get(rootUri));

        HttpResponse httpResp2 = resp2.returnResponse();
        assertNotNull(httpResp2);

        StatusLine statusLine2 = httpResp2.getStatusLine();
        assertNotNull(statusLine2);

        assertEquals("check authorized via auth token", HttpStatus.SC_OK, statusLine2.getStatusCode());
    }

    @Test
    public void testAuthTokenResourceLocation() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(rootUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);

        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check authorized", HttpStatus.SC_OK, statusLine.getStatusCode());

        Header[] _authToken = httpResp.getHeaders(AUTH_TOKEN_HEADER.toString());
        Header[] _authTokenValid = httpResp.getHeaders(AUTH_TOKEN_VALID_HEADER.toString());
        Header[] _authTokenLocation = httpResp.getHeaders(AUTH_TOKEN_LOCATION_HEADER.toString());

        assertNotNull("check not null auth token header", _authToken);
        assertNotNull("check not null auth token valid header", _authTokenValid);
        assertNotNull("check not null auth token location header", _authTokenLocation);

        assertTrue("check not empty array auth token header array ", _authToken.length == 1);
        assertTrue("check not empty array auth token valid header", _authTokenValid.length == 1);
        assertTrue("check not empty array auth token location header", _authTokenLocation.length == 1);

        String locationURI = _authTokenLocation[0].getValue();

        URI authTokenResourceUri = rootUri.resolve(locationURI);
        
        final String host = MONGO_HOST;
        final int port = conf.getHttpPort();

        Response resp2 = unauthExecutor.authPreemptive(new HttpHost(host, port, HTTP)).auth(new Credentials() {
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

        assertEquals("check auth token resource URI", HttpStatus.SC_OK, statusLine2.getStatusCode());
        
        assertNotNull("content type not null", entity.getContentType());
        assertEquals("check content type", Representation.HAL_JSON_MEDIA_TYPE, entity.getContentType().getValue());

        String content = EntityUtils.toString(entity);

        assertNotNull("check content of auth token resource", content);

        JsonObject json = null;

        try {
            json = JsonObject.readFrom(content);
        } catch (Throwable t) {
            fail("parsing received json");
        }
        
        assertNotNull("check content - auth_token not null", json.get("auth_token"));
        assertNotNull("check content - auth_token_valid_until not null", json.get("auth_token_valid_until"));
        
        assertTrue("check content - auth_token not empty", !json.get("auth_token").asString().isEmpty());
        assertTrue("check content - auth_token_valid_until not empty", !json.get("auth_token_valid_until").asString().isEmpty());
        
        assertEquals(json.get("auth_token").asString(), _authToken[0].getValue());
        assertEquals(json.get("auth_token_valid_until").asString(), _authTokenValid2[0].getValue());
    }
    
    @Test
    public void testAuthTokenInvalidation() throws Exception {
        Response resp = adminExecutor.execute(Request.Get(rootUri));

        HttpResponse httpResp = resp.returnResponse();
        assertNotNull(httpResp);

        StatusLine statusLine = httpResp.getStatusLine();
        assertNotNull(statusLine);

        assertEquals("check authorized", HttpStatus.SC_OK, statusLine.getStatusCode());

        Header[] _authToken = httpResp.getHeaders(AUTH_TOKEN_HEADER.toString());
        Header[] _authTokenValid = httpResp.getHeaders(AUTH_TOKEN_VALID_HEADER.toString());
        Header[] _authTokenLocation = httpResp.getHeaders(AUTH_TOKEN_LOCATION_HEADER.toString());

        assertNotNull("check not null auth token header", _authToken);
        assertNotNull("check not null auth token valid header", _authTokenValid);
        assertNotNull("check not null auth token location header", _authTokenLocation);

        assertTrue("check not empty array auth token header array ", _authToken.length == 1);
        assertTrue("check not empty array auth token valid header", _authTokenValid.length == 1);
        assertTrue("check not empty array auth token location header", _authTokenLocation.length == 1);

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
        
        assertEquals("check auth token resource URI", HttpStatus.SC_NO_CONTENT, statusLine2.getStatusCode());
        
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
        
        assertEquals("check auth token resource URI", HttpStatus.SC_UNAUTHORIZED, statusLine3.getStatusCode());
    }
}