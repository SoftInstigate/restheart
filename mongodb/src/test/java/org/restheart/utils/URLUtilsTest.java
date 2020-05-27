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
package org.restheart.utils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
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
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.UnsupportedDocumentIdException;
import org.restheart.mongodb.utils.URLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class URLUtilsTest {

    private static final Logger LOG = LoggerFactory.getLogger(URLUtilsTest.class);

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
    public URLUtilsTest() {
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
    public void testRemoveTrailingSlashes() {
        String s = "/ciao/this/has/trailings/////";
        String expResult = "/ciao/this/has/trailings";
        String result = URLUtils.removeTrailingSlashes(s);
        assertEquals(expResult, result);
    }

    /**
     *
     */
    @Test
    public void testDecodeQueryString() {
        String qs = "one%2Btwo";
        String expResult = "one+two";
        String result = URLUtils.decodeQueryString(qs);
        assertEquals(expResult, result);
    }

    /**
     *
     */
    @Test
    public void testGetParentPath() {
        String path = "/a/b/c/d";
        String expResult = "/a/b/c";
        String result = URLUtils.getParentPath(path);
        assertEquals(expResult, result);
    }

    /**
     *
     */
    @Test
    public void testGetUriWithDocId() {
        var context = prepareRequest();
        String expResult = "/dbName/collName/documentId";
        String result;
        try {
            result = URLUtils.getUriWithDocId(context, "dbName", "collName", new BsonString("documentId"));
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetUriWithDocIdStringValidObjectId() {
        var context = prepareRequest();
        String expResult = "/dbName/collName/54d13711c2e692941728e1d3?id_type=STRING";
        String result;
        try {
            result = URLUtils.getUriWithDocId(context, "dbName", "collName", new BsonString("54d13711c2e692941728e1d3"));
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetUriWithLongDocId() {
        var context = prepareRequest();
        String expResult = "/dbName/collName/123?id_type=NUMBER";
        String result;
        try {
            result = URLUtils.getUriWithDocId(context, "dbName", "collName", new BsonInt32(123));
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetUriWithFilterMany() {
        BsonValue[] ids = new BsonValue[]{
            new BsonInt32(1),
            new BsonDouble(20.0d),
            new BsonString("id")};

        var context = prepareRequest();
        String expResult = "/dbName/collName?filter={'_id':{'$in':[1,20.0,\'id\']}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterMany(context, "dbName", "collName", ids);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetUriWithFilterManyIdsWithSpaces() {
        BsonValue[] ids = new BsonValue[]{
            new BsonString("Three Imaginary Boys"),
            new BsonString("Seventeen Seconds")
        };
        var context = prepareRequest();
        String expResult = "/dbName/collName?filter={'_id':{'$in':[\'Three Imaginary Boys\','Seventeen Seconds\']}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterMany(context, "dbName", "collName", ids);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetUriWithFilterManyString() {
        BsonValue[] ids = new BsonValue[]{
            new BsonInt32(1),
            new BsonDouble(20.0d),
            new BsonString("id")};

        var context = prepareRequest();
        String expResult = "/dbName/collName?filter={'_id':{'$in':[1,20.0,'id']}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterMany(context, "dbName", "collName", ids);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetUriWithFilterOne() {
        var context = prepareRequest();
        String expResult = "/dbName/collName?filter={'referenceField':'id'}";
        String result;
        try {
            result = URLUtils.getUriWithFilterOne(context, "dbName", "collName", "referenceField", new BsonString("id"));
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetUriWithFilterOneInteger() {
        var context = prepareRequest();
        String expResult = "/dbName/collName?filter={'referenceField':123}";
        String result;
        try {
            result = URLUtils.getUriWithFilterOne(context, "dbName", "collName", "referenceField", new BsonInt32(123));
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetUriWithFilterOneObjectId() {
        BsonObjectId id = new BsonObjectId(new ObjectId());
        var context = prepareRequest();
        String expResult = "/dbName/collName?filter={'referenceField':{'$oid':'" + id.getValue().toString() + "'}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterOne(context, "dbName", "collName", "referenceField", id);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetUriWithFilterManyInverse() {
        var context = prepareRequest();
        String expResult = "/dbName/collName?filter={'referenceField':{'$elemMatch':{'$eq':'ids'}}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterManyInverse(context, "dbName", "collName", "referenceField", new BsonString("ids"));
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetUriWithFilterManyInverseLong() {
        var context = prepareRequest();
        String expResult = "/dbName/collName?filter={'referenceField':{'$elemMatch':{'$eq':123}}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterManyInverse(context, "dbName", "collName", "referenceField", new BsonInt32(123));
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetUriWithFilterManyInverseObjectId() {
        var context = prepareRequest();
        BsonObjectId id = new BsonObjectId(new ObjectId());
        String expResult = "/dbName/collName?filter={'referenceField':{'$elemMatch':{'$eq':{'$oid':'" + id.getValue().toString() + "'}}}}";
        String result;
        try {
            result = URLUtils.getUriWithFilterManyInverse(context, "dbName", "collName", "referenceField", id);
            assertEquals(expResult, result);
        } catch (UnsupportedDocumentIdException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     *
     */
    @Test
    public void testGetQueryStringRemovingParams() {
        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setQueryString("a=1&b=2&c=3");
        exchange.addQueryParam("a", "1").addQueryParam("b", "2").addQueryParam("c", "3");
        String expResult = "a=1&c=3";
        String result = URLUtils.getQueryStringRemovingParams(exchange, "b");
        assertEquals(expResult, result);
    }

    private MongoRequest prepareRequest() {
        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setRequestPath("");
        exchange.setRequestMethod(HttpString.EMPTY);
        return MongoRequest.init(exchange, "", "");
    }

}
