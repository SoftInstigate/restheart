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
package org.restheart.handlers.files;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.restheart.handlers.RequestContext;

/**
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
public class PutFileHandlerTest {
    
    public PutFileHandlerTest() {
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
    public void testHandleRequest() throws Exception { 
        System.out.println("handleRequest");
        
        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setRequestPath("/db/filecoll/_files/123");
        exchange.setRequestMethod(new HttpString("PUT"));

        RequestContext context = new RequestContext(exchange, "/", "/");
        PutFileHandler instance = new PutFileHandler();
        instance.handleRequest(exchange, context);
        
       assertEquals(201, exchange.getResponseCode());
    }
    
}
