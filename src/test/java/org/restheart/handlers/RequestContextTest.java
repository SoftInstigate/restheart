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
import org.junit.Test;
/**
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
public class RequestContextTest {

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
    public void selectRequestMethod() {
        System.out.println("selectRequestMethod");

        HttpString _method = new HttpString("UNKNOWN");
        assertEquals(RequestContext.METHOD.OTHER, RequestContext.selectRequestMethod(_method));
        
        _method = new HttpString("GET");
        assertEquals(RequestContext.METHOD.GET, RequestContext.selectRequestMethod(_method));
        
        _method = new HttpString("PATCH");
        assertEquals(RequestContext.METHOD.PATCH, RequestContext.selectRequestMethod(_method));
    }

    @Test
    public void selectRequestType() {
        System.out.println("selectRequestType");

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
    public void selectRequestType_File() {
        System.out.println("selectRequestType_File");
        
        String[] pathTokens = "/db/collection/_files".split("/");
        assertEquals(RequestContext.TYPE.FILE, RequestContext.selectRequestType(pathTokens));
    }

    @Test
    public void getMappedRequestUri() {
        System.out.println("getMappedRequestUri");
        
        HttpServerExchange exchange = new HttpServerExchange(null);
        exchange.setRequestPath("/");
        exchange.setRequestMethod(HttpString.EMPTY);

        String whatUri = "/mydb/mycollection";
        String whereUri = "/";
        
        RequestContext context = new RequestContext(exchange, whereUri, whatUri);
        assertEquals("/mydb/mycollection", context.getMappedRequestUri());
        
        whatUri = "*";
        whereUri = "/data";
        
        context = new RequestContext(exchange, whereUri, whatUri);
        assertEquals("/", context.getMappedRequestUri());
    }

}
