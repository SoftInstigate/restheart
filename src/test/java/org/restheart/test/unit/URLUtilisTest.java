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
package org.restheart.test.unit;

import org.restheart.hal.UnsupportedDocumentIdException;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class URLUtilisTest {
    private static final Logger LOG = LoggerFactory.getLogger(URLUtilisTest.class);
    
    @Rule
    public TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info("executing test {}", description.toString());
        }
    };

    public URLUtilisTest() {
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
    public void testRemoveTrailingSlashes() {
        String s = "/ciao/this/has/trailings/////";
        String expResult = "/ciao/this/has/trailings";
        String result = URLUtils.removeTrailingSlashes(s);
        assertEquals(expResult, result);
    }

    @Test
    public void testDecodeQueryString() {
        String qs = "one%2Btwo";
        String expResult = "one+two";
        String result = URLUtils.decodeQueryString(qs);
        assertEquals(expResult, result);
    }

    @Test
    public void testGetParentPath() {
        String path = "/a/b/c/d";
        String expResult = "/a/b/c";
        String result = URLUtils.getParentPath(path);
        assertEquals(expResult, result);
    }

    @Test
    public void testGetUriWithDocId() {
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName/documentId";
        String result;
        try {
            result = URLUtils.getUriWithDocId(context, "dbName", "collName", "documentId");
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }
    
    @Test
    public void testGetUriWithDocIdStringValidObjectId() {
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName/54d13711c2e692941728e1d3?id_type=STRING";
        String result;
        try {
            result = URLUtils.getUriWithDocId(context, "dbName", "collName", "54d13711c2e692941728e1d3");
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void testGetUriWithLongDocId() {
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName/123?id_type=NUMBER";
        String result;
        try {
            result = URLUtils.getUriWithDocId(context, "dbName", "collName", 123);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void testGetUriWithFilterMany() {
        Object[] ids = new Object[]{1, 20.0f, "id"};
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName?filter={'_id':{'$in':[1,20.0,\'id\']}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterMany(context, "dbName", "collName", ids);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }
    
    @Test
    public void testGetUriWithFilterManyIdsWithSpaces() {
        Object[] ids = new Object[]{"Three Imaginary Boys", "Seventeen Seconds"};
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName?filter={'_id':{'$in':[\'Three Imaginary Boys\','Seventeen Seconds\']}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterMany(context, "dbName", "collName", ids);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void testGetUriWithFilterManyString() {
        Object[] ids = new Object[]{1, 20.0f, "id"};
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName?filter={'_id':{'$in':[1,20.0,'id']}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterMany(context, "dbName", "collName", ids);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void testGetUriWithFilterOne() {
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName?filter={'referenceField':'id'}";
        String result;
        try {
            result = URLUtils.getUriWithFilterOne(context, "dbName", "collName", "referenceField", "id");
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void testGetUriWithFilterOneInteger() {
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName?filter={'referenceField':123}";
        String result;
        try {
            result = URLUtils.getUriWithFilterOne(context, "dbName", "collName", "referenceField", 123);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void testGetUriWithFilterOneObjectId() {
        ObjectId id = new ObjectId();
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName?filter={'referenceField':{'$oid':'" + id.toString() + "'}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterOne(context, "dbName", "collName", "referenceField", id);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void testGetUriWithFilterManyInverse() {
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName?filter={'referenceField':{'$elemMatch':{'$eq':'ids'}}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterManyInverse(context, "dbName", "collName", "referenceField", "ids");
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void testGetUriWithFilterManyInverseLong() {
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName?filter={'referenceField':{'$elemMatch':{'$eq':123}}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterManyInverse(context, "dbName", "collName", "referenceField", 123);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void testGetUriWithFilterManyInverseObjectId() {
        RequestContext context = prepareRequestContext();
        ObjectId id = new ObjectId();
        String expResult = "/dbName/collName?filter={'referenceField':{'$elemMatch':{'$eq':{'$oid':'" + id.toString() + "'}}}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterManyInverse(context, "dbName", "collName", "referenceField", id);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    @Test
    public void testGetQueryStringRemovingParams() {
        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setQueryString("a=1&b=2&c=3");
        exchange.addQueryParam("a", "1").addQueryParam("b", "2").addQueryParam("c", "3");
        String expResult = "a=1&c=3";
        String result = URLUtils.getQueryStringRemovingParams(exchange, "b");
        assertEquals(expResult, result);
    }

    private RequestContext prepareRequestContext() {
        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setRequestPath("");
        exchange.setRequestMethod(HttpString.EMPTY);
        RequestContext context = new RequestContext(exchange, "", "");
        return context;
    }

}
