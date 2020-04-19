/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
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
package org.restheart.exchange;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class BsonRequestTest {

    private static final Logger LOG = LoggerFactory.getLogger(BsonRequestTest.class);

    /**
     *
     */
    @BeforeClass
    public static void setUpClass() {
    }

    /**
     *
     */
    @AfterClass
    public static void tearDownClass() {
    }

    /**
     *
     */
    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    /**
     *
     */
    public BsonRequestTest() {
    }

    /**
     *
     */
    @Before
    public void setUp() {
    }

    /**
     *
     */
    @After
    public void tearDown() {
    }

    /**
     *
     */
    @Test
    public void testSelectRequestType() {
        String[] pathTokens = "/".split("/");
        assertEquals(ExchangeKeys.TYPE.ROOT, BsonRequest.selectRequestType(pathTokens));

        pathTokens = "/db".split("/");
        assertEquals(ExchangeKeys.TYPE.DB, BsonRequest.selectRequestType(pathTokens));

        pathTokens = "/db/collection".split("/");
        assertEquals(ExchangeKeys.TYPE.COLLECTION, BsonRequest.selectRequestType(pathTokens));

        pathTokens = "/db/collection/document".split("/");
        assertEquals(ExchangeKeys.TYPE.DOCUMENT, BsonRequest.selectRequestType(pathTokens));

        pathTokens = "/db/collection/_indexes".split("/");
        assertEquals(ExchangeKeys.TYPE.COLLECTION_INDEXES, BsonRequest.selectRequestType(pathTokens));

        pathTokens = "/db/collection/_indexes/123".split("/");
        assertEquals(ExchangeKeys.TYPE.INDEX, BsonRequest.selectRequestType(pathTokens));

        pathTokens = "/db/collection/_aggrs/test".split("/");
        assertEquals(ExchangeKeys.TYPE.AGGREGATION, BsonRequest.selectRequestType(pathTokens));
    }

    /**
     *
     */
    @Test
    public void test_COLLECTION_FILES_selectRequestType() {
        String[] pathTokens = "/db/mybucket.files".split("/");
        assertEquals(ExchangeKeys.TYPE.FILES_BUCKET, BsonRequest.selectRequestType(pathTokens));
    }

    /**
     *
     */
    @Test
    public void test_FILE_selectRequestType() {
        String[] pathTokens = "/db/mybucket.files/123".split("/");
        assertEquals(ExchangeKeys.TYPE.FILE, BsonRequest.selectRequestType(pathTokens));

        pathTokens = "/db/mybucket.files/123/binary".split("/");
        assertEquals(ExchangeKeys.TYPE.FILE_BINARY, BsonRequest.selectRequestType(pathTokens));

        pathTokens = "/db/mybucket.files/123/456".split("/");
        assertEquals(ExchangeKeys.TYPE.FILE, BsonRequest.selectRequestType(pathTokens));
    }

    /**
     *
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testGetMappedRequestUri() {
        HttpServerExchange ex = mock(HttpServerExchange.class);
        when(ex.getRequestPath()).thenReturn("/");
        when(ex.getRequestMethod()).thenReturn(HttpString.EMPTY);

        String whatUri = "/db/mycollection";
        String whereUri = "/";

        BsonRequest request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/mycollection", request.getUnmappedRequestUri());

        whatUri = "*";
        whereUri = "/";

        request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/", request.getUnmappedRequestUri());

        whatUri = "*";
        whereUri = "/data";

        request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/", request.getUnmappedRequestUri());

        whatUri = "/data";
        whereUri = "/";

        request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/data", request.getUnmappedRequestUri());

        whatUri = "/db/coll";
        whereUri = "/";

        request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll", request.getUnmappedRequestUri());

        whatUri = "/db/coll/doc";
        whereUri = "/";

        request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll/doc", request.getUnmappedRequestUri());

        whatUri = "/db/coll/";
        whereUri = "/";

        request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll", request.getUnmappedRequestUri());

        whatUri = "/db/coll////";
        whereUri = "/";

        request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll", request.getUnmappedRequestUri());
    }

    /**
     *
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testGetMappedRequestUri2() {
        HttpServerExchange ex = mock(HttpServerExchange.class);
        when(ex.getRequestPath()).thenReturn("/x");
        when(ex.getRequestMethod()).thenReturn(HttpString.EMPTY);

        String whatUri = "/db/mycollection";
        String whereUri = "/";

        BsonRequest request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/mycollection/x", request.getUnmappedRequestUri());

        whatUri = "*";
        whereUri = "/";

        request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/x", request.getUnmappedRequestUri());

        whatUri = "db";
        whereUri = "/";

        request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/x", request.getUnmappedRequestUri());

        whatUri = "db/coll";
        whereUri = "/";

        request = BsonRequest.init(ex, whereUri, whatUri);
        assertEquals("/db/coll/x", request.getUnmappedRequestUri());
    }
}
