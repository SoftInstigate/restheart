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
package org.restheart.handlers.injectors;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class CollectionPropsInjectorHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionPropsInjectorHandlerTest.class);

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

    public CollectionPropsInjectorHandlerTest() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testCheckCollectionPut() {
        RequestContext context = createContext("/db/collection", "PUT");

        assertEquals(RequestContext.TYPE.COLLECTION, context.getType());
        assertEquals(RequestContext.METHOD.PUT, context.getMethod());
        assertEquals(false, CollectionPropsInjectorHandler.checkCollection(context));
    }

    @Test
    public void testCheckCollectionFilesPost() {
        RequestContext context = createContext("/db/fs.files", "POST");

        assertEquals(RequestContext.TYPE.FILES_BUCKET, context.getType());
        assertEquals(RequestContext.METHOD.POST, context.getMethod());
        assertEquals(true, CollectionPropsInjectorHandler.checkCollection(context));
    }

    @Test
    public void testCheckCollectionRoot() {
        RequestContext context = createContext("/", "PUT");

        assertEquals(RequestContext.TYPE.ROOT, context.getType());
        assertEquals(RequestContext.METHOD.PUT, context.getMethod());
        assertEquals(false, CollectionPropsInjectorHandler.checkCollection(context));
    }

    private RequestContext createContext(String requestPath, String httpMethod) {
        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setRequestPath(requestPath);
        exchange.setRequestMethod(new HttpString(httpMethod));
        RequestContext context = new RequestContext(exchange, "/", "*");
        return context;
    }

}
