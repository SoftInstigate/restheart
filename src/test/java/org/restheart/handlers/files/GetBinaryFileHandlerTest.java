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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class GetBinaryFileHandlerTest {
    
    public GetBinaryFileHandlerTest() {
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

//    @Test
//    public void testHandleRequest() throws Exception {
//        System.out.println("handleRequest");
//        HttpServerExchange exchange = null;
//        RequestContext context = null;
//        GetBinaryFileHandler instance = new GetBinaryFileHandler();
//        instance.handleRequest(exchange, context);
//        // TODO review the generated test code and remove the default call to fail.
//        fail("The test case is a prototype.");
//    }

    @Test
    public void testExtractBucket() {
        System.out.println("extractBucket");
        String expResult = "mybucket";
        String result = GetBinaryFileHandler.extractBucket("mybucket.files");
        assertEquals(expResult, result);
    }
    
}
