/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2024 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.exchange;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import io.undertow.util.PathTemplate;
import io.undertow.util.PathTemplateMatch;
import io.undertow.util.PathTemplateMatcher;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class MongoRequestTest {
    /**
     *
     */
    @BeforeAll
    public static void setUpClass() {
    }

    /**
     *
     */
    @AfterAll
    public static void tearDownClass() {
    }

    /**
     *
     *
     * public TestRule watcher = new TestWatcher() {
     *
     * @Override
     *           protected void starting(Description description) {
     *           LOG.info("executing test {}", description.toString());
     *           }
     *           };
     */

    /**
     *
     */
    public BsonRequestTest() {
    }

    /**
     *
     */
    @BeforeEach
    public void setUp() {
    }

    /**
     *
     */
    @AfterEach
    public void tearDown() {
    }

    /**
     *
     */
    @Test
    public void testSelectRequestType() {
        String[] pathTokens = "/".split("/");
        assertEquals(ExchangeKeys.TYPE.ROOT, MongoRequest.selectRequestType(pathTokens));

        pathTokens = "/db".split("/");
        assertEquals(ExchangeKeys.TYPE.DB, MongoRequest.selectRequestType(pathTokens));

        pathTokens = "/db/collection".split("/");
        assertEquals(ExchangeKeys.TYPE.COLLECTION, MongoRequest.selectRequestType(pathTokens));

        pathTokens = "/db/collection/document".split("/");
        assertEquals(ExchangeKeys.TYPE.DOCUMENT, MongoRequest.selectRequestType(pathTokens));

        pathTokens = "/db/collection/_indexes".split("/");
        assertEquals(ExchangeKeys.TYPE.COLLECTION_INDEXES, MongoRequest.selectRequestType(pathTokens));

        pathTokens = "/db/collection/_indexes/123".split("/");
        assertEquals(ExchangeKeys.TYPE.INDEX, MongoRequest.selectRequestType(pathTokens));

        pathTokens = "/db/collection/_aggrs/test".split("/");
        assertEquals(ExchangeKeys.TYPE.AGGREGATION, MongoRequest.selectRequestType(pathTokens));
    }

    /**
     *
     */
    @Test
    public void test_COLLECTION_FILES_selectRequestType() {
        String[] pathTokens = "/db/mybucket.files".split("/");
        assertEquals(ExchangeKeys.TYPE.FILES_BUCKET, MongoRequest.selectRequestType(pathTokens));
    }

    /**
     *
     */
    @Test
    public void test_FILE_selectRequestType() {
        String[] pathTokens = "/db/mybucket.files/123".split("/");
        assertEquals(ExchangeKeys.TYPE.FILE, MongoRequest.selectRequestType(pathTokens));

        pathTokens = "/db/mybucket.files/123/binary".split("/");
        assertEquals(ExchangeKeys.TYPE.FILE_BINARY, MongoRequest.selectRequestType(pathTokens));

        pathTokens = "/db/mybucket.files/123/456".split("/");
        assertEquals(ExchangeKeys.TYPE.FILE, MongoRequest.selectRequestType(pathTokens));
    }

    /**
     *
     */
    @Test
    public void testGetMappedRequestUri() {
        HttpServerExchange ex = mock(HttpServerExchange.class);
        when(ex.getRequestPath()).thenReturn("/");
        when(ex.getRequestMethod()).thenReturn(HttpString.EMPTY);

        String whatUri = "/db/mycollection";
        String whereUri = "/";

        MongoRequest request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/mycollection", request.getMongoResourceUri());

        whatUri = "*";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/", request.getMongoResourceUri());

        whatUri = "*";
        whereUri = "/data";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/", request.getMongoResourceUri());

        whatUri = "/data";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/data", request.getMongoResourceUri());

        whatUri = "/db/coll";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll", request.getMongoResourceUri());

        whatUri = "/db/coll/doc";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll/doc", request.getMongoResourceUri());

        whatUri = "/db/coll/";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll", request.getMongoResourceUri());

        whatUri = "/db/coll////";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll", request.getMongoResourceUri());
    }

    /**
     *
     */
    @Test
    public void testGetMappedRequestUri2() {
        HttpServerExchange ex = mock(HttpServerExchange.class);
        when(ex.getRequestPath()).thenReturn("/x");
        when(ex.getRequestMethod()).thenReturn(HttpString.EMPTY);

        String whatUri = "/db/mycollection";
        String whereUri = "/";

        MongoRequest request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/mycollection/x", request.getMongoResourceUri());

        whatUri = "*";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/x", request.getMongoResourceUri());

        whatUri = "db";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/x", request.getMongoResourceUri());

        whatUri = "db/coll";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll/x", request.getMongoResourceUri());
    }

    /**
     *
     */
    @Test
    public void testGetMappedRequestUriWithPathTemplate() {
        // where=/{*}
        assertEquals("/", request("/", "/{*}", "{*}").getMongoResourceUri());
        assertEquals("/a", request("/a", "/{*}", "{*}").getMongoResourceUri());
        assertEquals("/a/b", request("/a/b", "/{*}", "{*}").getMongoResourceUri());
        assertEquals("/a/b/c", request("/a/b/c", "/{*}", "{*}").getMongoResourceUri());
        assertEquals("/a/b/*", request("/a/b/*", "/{*}", "{*}").getMongoResourceUri());

        assertEquals("/", request("/api/", "/api/{*}", "{*}").getMongoResourceUri());
        assertEquals("/a", request("/api/a", "/api/{*}", "{*}").getMongoResourceUri());
        assertEquals("/a/b", request("/api/a/b", "/api/{*}", "{*}").getMongoResourceUri());
        assertEquals("/a/b/c", request("/api/a/b/c", "/api/{*}", "{*}").getMongoResourceUri());
        assertEquals("/a/b/*", request("/api/a/b/*", "/api/{*}", "{*}").getMongoResourceUri());

        assertEquals("/x/y/z", request("/x/y/z", "/{a}/{b}/{c}", "/{a}/{b}/{c}").getMongoResourceUri());
        assertEquals("/a/b/c", request("/a/b/c", "/{a}/{b}/{*}", "/{a}/{b}/{*}").getMongoResourceUri());
        assertEquals("/a/b/*", request("/a/b/*", "/{a}/{b}/{*}", "/{a}/{b}/{*}").getMongoResourceUri());

        assertEquals("/1-2-x", request("/api/1/2", "/api/{a}/{b}", "/{a}-{b}-x").getMongoResourceUri());

        assertEquals("/1-2", request("/1/2", "/{b}/{*}", "/{b}-{*}").getMongoResourceUri());

        // *=don't work well with path templates, matching paths always map to root resource
        assertEquals("/", request("/api/1/2", "/api/{*}", "*").getMongoResourceUri());

        // Case with single document mapping
        assertEquals("/db/mycollection/doc1", request("/api/coll/doc1", "/api/coll/{id}", "/db/mycollection/{id}").getMongoResourceUri());

        // Case with single document mapping
        assertEquals("/db/mycollection/doc1", request("/api/coll/doc1", "/api/coll/{id}", "/db/mycollection/{id}").getMongoResourceUri());

        // Case with an additional path segment that matches the wildcard
        assertEquals("/db/mycollection/subpath", request("/api/coll/subpath", "/api/coll/{*}", "/db/mycollection/{*}").getMongoResourceUri());

        // Case with empty path and wildcard for root mapping
        assertEquals("/", request("/", "/{*}", "{*}").getMongoResourceUri());

        // Case with variable for a specific document
        assertEquals("/db/mycollection/doc1", request("/api/coll/doc1", "/api/coll/{id}", "/db/mycollection/{id}").getMongoResourceUri());

        // Case with base path without variables
        assertEquals("/db/mycollection", request("/api/coll", "/api/coll", "/db/mycollection").getMongoResourceUri());

        // Case with special characters in identifier
        assertEquals("/db/mycollection/abc%20def", request("/api/coll/abc%20def", "/api/coll/{name}", "/db/mycollection/{name}").getMongoResourceUri());

        // Edge case with identical paths for both request and template
        assertEquals("/api/coll", request("/api/coll", "/api/coll", "/api/coll").getMongoResourceUri());

    }

    private MongoRequest request(String path, String where, String what) {
        var ex = mock(HttpServerExchange.class);
        when(ex.getRequestPath()).thenReturn(path);
        when(ex.getRequestMethod()).thenReturn(HttpString.EMPTY);

        var ptm = new PathTemplateMatcher<String>();
        ptm.add(PathTemplate.create(where), "");

        var match = ptm.match(path);
        when(ex.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)).thenReturn(match);

        return MongoRequest.init(ex, where, what);
    }
}
