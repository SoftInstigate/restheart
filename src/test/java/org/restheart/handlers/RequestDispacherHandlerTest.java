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
package org.restheart.handlers;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import static org.hamcrest.CoreMatchers.instanceOf;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.restheart.handlers.collection.GetCollectionHandler;
import org.restheart.handlers.collection.PutCollectionHandler;
import org.restheart.handlers.database.GetDBHandler;
import org.restheart.handlers.files.PostBucketHandler;
import org.restheart.handlers.root.GetRootHandler;
import org.restheart.utils.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
public class RequestDispacherHandlerTest {
    private static final Logger LOG = LoggerFactory.getLogger(RequestDispacherHandlerTest.class);

    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    private RequestDispacherHandler dispacher;

    public RequestDispacherHandlerTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        dispacher = new RequestDispacherHandler(false);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testCreateHttpHandlers() {
        dispacher.putPipedHttpHandler(RequestContext.TYPE.ROOT, RequestContext.METHOD.GET, new GetRootHandler(null, null));
        dispacher.putPipedHttpHandler(RequestContext.TYPE.DB, RequestContext.METHOD.GET, new GetDBHandler(null, null));
        dispacher.putPipedHttpHandler(RequestContext.TYPE.COLLECTION, RequestContext.METHOD.PUT, new PutCollectionHandler(null, null));
        dispacher.putPipedHttpHandler(RequestContext.TYPE.FILES_BUCKET, RequestContext.METHOD.GET, new GetCollectionHandler(null, null));

        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.ROOT, RequestContext.METHOD.GET), instanceOf(GetRootHandler.class));
        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.DB, RequestContext.METHOD.GET), instanceOf(GetDBHandler.class));
        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.COLLECTION, RequestContext.METHOD.PUT), instanceOf(PutCollectionHandler.class));
        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.FILES_BUCKET, RequestContext.METHOD.GET), instanceOf(GetCollectionHandler.class));

        assertNull(dispacher.getPipedHttpHandler(RequestContext.TYPE.COLLECTION, RequestContext.METHOD.POST));
    }

    @Ignore
    public void testPostBinaryFileHandler() throws Exception {
        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setRequestPath("/testdb/mybucket.files");
        exchange.setRequestMethod(new HttpString("POST"));
        RequestContext context = new RequestContext(exchange, "/", "*");

        dispacher.putPipedHttpHandler(RequestContext.TYPE.FILES_BUCKET, RequestContext.METHOD.POST, new PostBucketHandler(null, null));
        dispacher.handleRequest(exchange, context);

        assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, exchange.getResponseCode());
    }
}
