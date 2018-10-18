/*
 * uIAM - the IAM for microservices
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
package io.uiam.handlers;

import io.uiam.handlers.RequestContext;
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
public class RequestContextTest {

    private static final Logger LOG = LoggerFactory.getLogger(RequestContextTest.class);

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }
    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    public RequestContextTest() {
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
    public void testGetMappedRequestUri() {
        HttpServerExchange ex = mock(HttpServerExchange.class);
        when(ex.getRequestPath()).thenReturn("/");
        when(ex.getRequestMethod()).thenReturn(HttpString.EMPTY);

        String whatUri = "/db/mycollection";
        String whereUri = "/";

        RequestContext context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/db/mycollection", context.getUnmappedRequestUri());

        whatUri = "*";
        whereUri = "/";

        context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/", context.getUnmappedRequestUri());

        whatUri = "*";
        whereUri = "/data";

        context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/", context.getUnmappedRequestUri());

        whatUri = "/data";
        whereUri = "/";

        context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/data", context.getUnmappedRequestUri());

        whatUri = "/db/coll";
        whereUri = "/";

        context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/db/coll", context.getUnmappedRequestUri());

        whatUri = "/db/coll/doc";
        whereUri = "/";

        context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/db/coll/doc", context.getUnmappedRequestUri());

        whatUri = "/db/coll/";
        whereUri = "/";

        context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/db/coll", context.getUnmappedRequestUri());

        whatUri = "/db/coll////";
        whereUri = "/";

        context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/db/coll", context.getUnmappedRequestUri());
    }

    @Test
    public void testGetMappedRequestUri2() {
        HttpServerExchange ex = mock(HttpServerExchange.class);
        when(ex.getRequestPath()).thenReturn("/x");
        when(ex.getRequestMethod()).thenReturn(HttpString.EMPTY);

        String whatUri = "/db/mycollection";
        String whereUri = "/";

        RequestContext context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/db/mycollection/x", context.getUnmappedRequestUri());

        whatUri = "*";
        whereUri = "/";

        context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/x", context.getUnmappedRequestUri());

        whatUri = "db";
        whereUri = "/";

        context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/db/x", context.getUnmappedRequestUri());

        whatUri = "db/coll";
        whereUri = "/";

        context = new RequestContext(ex, whereUri, whatUri);
        assertEquals("/db/coll/x", context.getUnmappedRequestUri());
    }
}
