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

import io.undertow.util.Headers;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.Before;
import org.junit.Test;
import org.restheart.representation.Resource;
import static org.restheart.test.integration.HttpClientAbstactIT.adminExecutor;
import static org.restheart.test.integration.HttpClientAbstactIT.buildURI;
import static org.restheart.test.integration.HttpClientAbstactIT.collectionTmpUri;
import static org.restheart.test.integration.HttpClientAbstactIT.dbTmpUri;
import static org.restheart.test.integration.HttpClientAbstactIT.halCT;
import org.restheart.utils.HttpStatus;

/**
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonPathConditionsCheckerIT extends HttpClientAbstactIT {

    private static URI userURI;

    /**
     *
     * @throws URISyntaxException
     */
    public JsonPathConditionsCheckerIT() throws URISyntaxException {
        super();
        userURI = buildURI("/" + dbTmpName + "/" + collectionTmpName + "/a@si.com");
    }

    /**
     *
     * @throws Exception
     */
    @Before
    public void createDBandCollection() throws Exception {
        Response resp;

        // *** PUT tmpdb
        resp = adminExecutor.execute(Request.Put(dbTmpUri)
                .bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put mytmpdb", resp, HttpStatus.SC_CREATED);

        // *** PUT tmpcoll 
        final String METADATA = getResourceFile("metadata/jsonpath-users.json");

        resp = adminExecutor.execute(Request.Put(collectionTmpUri)
                .bodyString(METADATA, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));
        check("check put mytmpdb.tmpcoll", resp, HttpStatus.SC_CREATED);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPostData() throws Exception {
        Response resp;

        // *** test create invalid data
        resp = adminExecutor.execute(Request.Post(collectionTmpUri)
                .bodyString("{a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check post invalid data", resp, HttpStatus.SC_BAD_REQUEST);

        // *** test create valid data
        final String VALID_USER = getResourceFile("data/jsonpath-testuser.json");

        resp = adminExecutor.execute(Request.Post(collectionTmpUri)
                .bodyString(VALID_USER, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check post valid data", resp, HttpStatus.SC_CREATED);

        // *** test update invalid data
        resp = adminExecutor.execute(Request.Post(collectionTmpUri)
                .bodyString("{_id: \"a@si.com\", a:1}", halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check update post invalid data", resp, HttpStatus.SC_BAD_REQUEST);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPostDataDotNotation() throws Exception {
        Response resp;

        // *** test post valid data with dot notation
        final String VALID_USER_DN = getResourceFile("data/jsonpath-testuser-dotnot.json");

        resp = adminExecutor.execute(Request.Post(collectionTmpUri)
                .bodyString(VALID_USER_DN, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check post valid data with dot notation", resp, HttpStatus.SC_CREATED);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPostIncompleteDataDotNotation() throws Exception {
        Response resp;

        // *** test post valid data with dot notation
        final String VALID_USER_DN = getResourceFile("data/jsonpath-testuser-dotnot-incomplete.json");

        resp = adminExecutor.execute(Request.Post(collectionTmpUri)
                .bodyString(VALID_USER_DN, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check post valid data with dot notation", resp, HttpStatus.SC_BAD_REQUEST);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPutDataDotNotation() throws Exception {
        Response resp;

        // *** test post valid data with dot notation
        final String VALID_USER_DN = getResourceFile("data/jsonpath-testuser-dotnot.json");

        resp = adminExecutor.execute(Request.Put(userURI)
                .bodyString(VALID_USER_DN, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check post valid data with dot notation", resp, HttpStatus.SC_CREATED);
    }

    /**
     *
     * @throws Exception
     */
    @Test
    public void testPatchData() throws Exception {
        Response resp;

        // *** test create valid data
        final String VALID_USER = getResourceFile("data/jsonpath-testuser.json");

        resp = adminExecutor.execute(Request.Post(collectionTmpUri)
                .bodyString(VALID_USER, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check post valid data", resp, HttpStatus.SC_CREATED);

        // *** test patch valid data with dot notation
        final String VALID_USER_DN = getResourceFile("data/jsonpath-testuser-dotnot.json");

        resp = adminExecutor.execute(Request.Patch(userURI)
                .bodyString(VALID_USER_DN, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check patch valid data with dot notation", resp, HttpStatus.SC_OK);

        // *** test patch invalid key
        final String INVALID_KEY = "{\"notexist\": 1}";

        resp = adminExecutor.execute(Request.Patch(userURI)
                .bodyString(INVALID_KEY, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check patch invalid key with dot notation", resp, HttpStatus.SC_BAD_REQUEST);

        // *** test patch invalid key
        final String INVALID_KEY_DN = "{\"details.notexists\": 1}";

        resp = adminExecutor.execute(Request.Patch(userURI)
                .bodyString(INVALID_KEY_DN, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check patch invalid key with dot notation", resp, HttpStatus.SC_BAD_REQUEST);

        // *** test patch wrong type object data
        final String INVALID_OBJ = "{\"details.city\": 1}";

        resp = adminExecutor.execute(Request.Patch(userURI)
                .bodyString(INVALID_OBJ, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check patch wrong type object data with dot notation", resp, HttpStatus.SC_BAD_REQUEST);

        // *** test patch invalid array data
        final String INVALID_ARRAY = "{\"roles.0\": 1}";

        resp = adminExecutor.execute(Request.Patch(userURI)
                .bodyString(INVALID_ARRAY, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check patch invalid array data with dot notation", resp, HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * see bug https://softinstigate.atlassian.net/browse/RH-160
     *
     * @throws Exception
     */
    @Test
    public void testPatchIncompleteObject() throws Exception {
        Response resp;

        // *** test create valid data
        final String VALID_USER = getResourceFile("data/jsonpath-testuser.json");

        resp = adminExecutor.execute(Request.Post(collectionTmpUri)
                .bodyString(VALID_USER, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check post valid data", resp, HttpStatus.SC_CREATED);

        // *** test patch valid data with dot notation
        // an incomplete details object. address and country are nullable but mandatory
        final String INCOMPLETE_OBJ = "{\"details\": {\"city\": \"a city\"}}";

        resp = adminExecutor.execute(Request.Patch(userURI)
                .bodyString(INCOMPLETE_OBJ, halCT)
                .addHeader(Headers.CONTENT_TYPE_STRING, Resource.HAL_JSON_MEDIA_TYPE));

        check("check patch valid data with dot notation", resp, HttpStatus.SC_BAD_REQUEST);
    }
}
