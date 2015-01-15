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
package org.restheart.utils;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
public class URLUtilisTest {

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
        System.out.println("removeTrailingSlashes");
        String s = "/ciao/this/has/trailings/////";
        String expResult = "/ciao/this/has/trailings";
        String result = URLUtilis.removeTrailingSlashes(s);
        assertEquals(expResult, result);
    }

    @Test
    public void testDecodeQueryString() {
        System.out.println("decodeQueryString");
        String qs = "one%2Btwo";
        String expResult = "one+two";
        String result = URLUtilis.decodeQueryString(qs);
        assertEquals(expResult, result);
    }

    @Test
    public void testGetParentPath() {
        System.out.println("getParentPath");
        String path = "/a/b/c/d";
        String expResult = "/a/b/c";
        String result = URLUtilis.getParentPath(path);
        assertEquals(expResult, result);
    }

    @Test
    public void testGetUriWithDocId() {
        System.out.println("getUriWithDocId");
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName/documentId";
        String result = URLUtilis.getUriWithDocId(context, "dbName", "collName", "documentId");
        assertEquals(expResult, result);
    }

    @Test
    public void testGetUriWithFilterMany() {
        System.out.println("getUriWithFilterMany");
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName?filter={'referenceField':{'$in':ids}}";
        String result = URLUtilis.getUriWithFilterMany(context, "dbName", "collName", "referenceField", "ids");
        assertEquals(expResult, result);
    }

    @Test
    public void testGetUriWithFilterOne() {
        System.out.println("getUriWithFilterOne");
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName?filter={'referenceField':ids}";
        String result = URLUtilis.getUriWithFilterOne(context, "dbName", "collName", "referenceField", "ids");
        assertEquals(expResult, result);
    }

    @Test
    public void testGetUriWithFilterManyInverse() {
        System.out.println("getUriWithFilterManyInverse");
        RequestContext context = prepareRequestContext();
        String expResult = "/dbName/collName?filter={'referenceField':{'$elemMatch':{'$eq':ids}}}";
        String result = URLUtilis.getUriWithFilterManyInverse(context, "dbName", "collName", "referenceField", "ids");
        assertEquals(expResult, result);
    }

    @Test
    public void testGetQueryStringRemovingParams() {
        System.out.println("getQueryStringRemovingParams");
        HttpServerExchange exchange = new HttpServerExchange(null);
        exchange.setQueryString("a=1&b=2&c=3");
        exchange.addQueryParam("a", "1").addQueryParam("b", "2").addQueryParam("c", "3");
        String expResult = "a=1&c=3";
        String result = URLUtilis.getQueryStringRemovingParams(exchange,"b");
        assertEquals(expResult, result);
    }

    private RequestContext prepareRequestContext() {
        HttpServerExchange exchange = new HttpServerExchange(null);
        exchange.setRequestPath("");
        exchange.setRequestMethod(HttpString.EMPTY);
        RequestContext context = new RequestContext(exchange, "", "");
        return context;
    }

}
