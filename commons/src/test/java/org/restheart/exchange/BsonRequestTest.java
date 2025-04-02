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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.undertow.util.PathTemplateMatch;
import io.undertow.util.PathTemplateMatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class BsonRequestTest {

    private static final Logger LOG = LoggerFactory.getLogger(BsonRequestTest.class);

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
        assertEquals("/db/mycollection", request.getUnmappedRequestUri());

        whatUri = "*";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/", request.getUnmappedRequestUri());

        whatUri = "*";
        whereUri = "/data";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/", request.getUnmappedRequestUri());

        whatUri = "/data";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/data", request.getUnmappedRequestUri());

        whatUri = "/db/coll";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll", request.getUnmappedRequestUri());

        whatUri = "/db/coll/doc";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll/doc", request.getUnmappedRequestUri());

        whatUri = "/db/coll/";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll", request.getUnmappedRequestUri());

        whatUri = "/db/coll////";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll", request.getUnmappedRequestUri());
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
        assertEquals("/db/mycollection/x", request.getUnmappedRequestUri());

        whatUri = "*";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/x", request.getUnmappedRequestUri());

        whatUri = "db";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/x", request.getUnmappedRequestUri());

        whatUri = "db/coll";
        whereUri = "/";

        request = MongoRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll/x", request.getUnmappedRequestUri());
    }

    /**
     *
     */
    @Test
    public void testPathTemplatedMappedRequest() {
        var requestPath = "/acme/coll";

        // Here mimic MongoService that attach PathTemplateMatch to the exchange
        PathTemplateMatcher<MongoMount> templateMongoMounts = new PathTemplateMatcher<>();
        String whatUri = "/restheart/{tenant}_{coll}";
        String whereUri = "/{tenant}/{coll}";
        var mongoMount = new MongoMount(whatUri,whereUri);
        templateMongoMounts.add(mongoMount.uri, mongoMount);

        var tmm = templateMongoMounts.match(requestPath);

        HttpServerExchange ex = mock(HttpServerExchange.class);
        when(ex.getRequestPath()).thenReturn(requestPath);
        when(ex.getRequestMethod()).thenReturn(HttpString.EMPTY);
        when(ex.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)).thenReturn(tmm);

        MongoRequest request = MongoRequest.init(ex, whereUri, whatUri);

        assertEquals("/restheart/acme_coll", request.getUnmappedRequestUri());
        assertEquals("restheart", request.getDBName());
        assertEquals("acme_coll", request.getCollectionName());
    }

    /**
     *
     */
    @Test
    public void testPathTemplatedMappedRequestWithWildcard() {
        var requestPath = "/acme/coll/docid";

        // Here mimic MongoService that attach PathTemplateMatch to the exchange
        PathTemplateMatcher<MongoMount> templateMongoMounts = new PathTemplateMatcher<>();
        String whatUri = "/restheart/{tenant}_{coll}";
        String whereUri = "/{tenant}/{coll}/*";
        var mongoMount = new MongoMount(whatUri,whereUri);
        templateMongoMounts.add(mongoMount.uri, mongoMount);

        var tmm = templateMongoMounts.match(requestPath);

        HttpServerExchange ex = mock(HttpServerExchange.class);
        when(ex.getRequestPath()).thenReturn(requestPath);
        when(ex.getRequestMethod()).thenReturn(HttpString.EMPTY);
        when(ex.getAttachment(PathTemplateMatch.ATTACHMENT_KEY)).thenReturn(tmm);

        MongoRequest request = MongoRequest.init(ex, whereUri, whatUri);

        assertEquals("/restheart/acme_coll/docid", request.getUnmappedRequestUri());
        assertEquals("restheart", request.getDBName());
        assertEquals("acme_coll", request.getCollectionName());
    }

    /**
     * helper class to store mongo mounts info
     */
    private static record MongoMount(String resource, String uri) {
        public MongoMount(String resource, String uri) {
            if (uri == null) {
                throw new IllegalArgumentException("'where' cannot be null. check your 'mongo-mounts'.");
            }

            if (!uri.startsWith("/")) {
                throw new IllegalArgumentException("'where' must start with \"/\". check your 'mongo-mounts'");
            }

            if (resource == null) {
                throw new IllegalArgumentException("'what' cannot be null. check your 'mongo-mounts'.");
            }

            if (!uri.startsWith("/") && !uri.equals("*")) {
                throw new IllegalArgumentException("'what' must be * (all db resorces) or start with \"/\". (eg. /db/coll) check your 'mongo-mounts'");
            }

            this.resource = resource;
            this.uri = org.restheart.utils.URLUtils.removeTrailingSlashes(uri);
        }

        @Override
        public String toString() {
            return "MongoMount(" + uri + " -> " + resource + ")";
        }
    }
}
