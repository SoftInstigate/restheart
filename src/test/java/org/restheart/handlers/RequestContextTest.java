/*
 * RESTHeart - the data REST API server
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
package org.restheart.handlers;

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
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class RequestContextTest {
    private static final Logger LOG = LoggerFactory.getLogger(RequestContextTest.class);

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    public RequestContextTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testSelectRequestMethod() {
        HttpString _method = new HttpString("UNKNOWN");
        assertEquals(RequestContext.METHOD.OTHER, RequestContext.selectRequestMethod(_method));

        _method = new HttpString("GET");
        assertEquals(RequestContext.METHOD.GET, RequestContext.selectRequestMethod(_method));

        _method = new HttpString("PATCH");
        assertEquals(RequestContext.METHOD.PATCH, RequestContext.selectRequestMethod(_method));
    }

    @Test
    public void testSelectRequestType() {
        String[] pathTokens = "/".split("/");
        assertEquals(RequestContext.TYPE.ROOT, RequestContext.selectRequestType(pathTokens));

        pathTokens = "/db".split("/");
        assertEquals(RequestContext.TYPE.DB, RequestContext.selectRequestType(pathTokens));

        pathTokens = "/db/collection".split("/");
        assertEquals(RequestContext.TYPE.COLLECTION, RequestContext.selectRequestType(pathTokens));

        pathTokens = "/db/collection/document".split("/");
        assertEquals(RequestContext.TYPE.DOCUMENT, RequestContext.selectRequestType(pathTokens));

        pathTokens = "/db/collection/_indexes".split("/");
        assertEquals(RequestContext.TYPE.COLLECTION_INDEXES, RequestContext.selectRequestType(pathTokens));

        pathTokens = "/db/collection/_indexes/123".split("/");
        assertEquals(RequestContext.TYPE.INDEX, RequestContext.selectRequestType(pathTokens));
    }

    @Test
    public void test_COLLECTION_FILES_selectRequestType() {
        String[] pathTokens = "/db/mybucket.files".split("/");
        assertEquals(RequestContext.TYPE.FILES_BUCKET, RequestContext.selectRequestType(pathTokens));
    }

    @Test
    public void test_FILE_selectRequestType() {
        String[] pathTokens = "/db/mybucket.files/123".split("/");
        assertEquals(RequestContext.TYPE.FILE, RequestContext.selectRequestType(pathTokens));
        
        pathTokens = "/db/mybucket.files/123/binary".split("/");
        assertEquals(RequestContext.TYPE.FILE_BINARY, RequestContext.selectRequestType(pathTokens));

        pathTokens = "/db/mybucket.files/123/456".split("/");
        assertEquals(RequestContext.TYPE.FILE, RequestContext.selectRequestType(pathTokens));
    }

    @Test
    public void testGetMappedRequestUri() {
        HttpServerExchange ex = mock(HttpServerExchange.class);
        when(ex.getRequestPath()).thenReturn("/");
        when(ex.getRequestMethod()).thenReturn(HttpString.EMPTY);

        String whatUri = "/mydb/mycollection";
        String whereUri = "/";

        RequestContext context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/mydb/mycollection", context.getMappedRequestUri());

        whatUri = "*";
        whereUri = "/data";

        context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/", context.getMappedRequestUri());
    }
}