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

//import com.mongodb.DB;
//import com.mongodb.Mongo;
//import com.mongodb.MongoClient;
//import com.mongodb.gridfs.GridFS;
//import com.mongodb.gridfs.GridFSInputFile;
//import io.undertow.server.HttpServerExchange;
//import io.undertow.util.HttpString;
//import java.io.IOException;
//import java.io.InputStream;
//import java.net.UnknownHostException;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import static org.hamcrest.CoreMatchers.instanceOf;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import static org.junit.Assert.*;
import org.junit.Test;
import static org.mockito.Mockito.mock;
import org.restheart.handlers.collection.GetCollectionHandler;
import org.restheart.handlers.collection.PutCollectionHandler;
import org.restheart.handlers.database.GetDBHandler;
import org.restheart.handlers.files.PostBinaryFileHandler;
import org.restheart.handlers.root.GetRootHandler;
import org.restheart.utils.HttpStatus;

/**
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
public class RequestDispacherHandlerTest {

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
        System.out.println("testCreateHttpHandlers");

        dispacher.putPipedHttpHandler(RequestContext.TYPE.ROOT, RequestContext.METHOD.GET, new GetRootHandler(null, null));
        dispacher.putPipedHttpHandler(RequestContext.TYPE.DB, RequestContext.METHOD.GET, new GetDBHandler(null, null));
        dispacher.putPipedHttpHandler(RequestContext.TYPE.COLLECTION, RequestContext.METHOD.PUT, new PutCollectionHandler(null, null));
        dispacher.putPipedHttpHandler(RequestContext.TYPE.COLLECTION_FILES, RequestContext.METHOD.GET, new GetCollectionHandler(null, null));

        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.ROOT, RequestContext.METHOD.GET), instanceOf(GetRootHandler.class));
        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.DB, RequestContext.METHOD.GET), instanceOf(GetDBHandler.class));
        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.COLLECTION, RequestContext.METHOD.PUT), instanceOf(PutCollectionHandler.class));
        assertThat(dispacher.getPipedHttpHandler(RequestContext.TYPE.COLLECTION_FILES, RequestContext.METHOD.GET), instanceOf(GetCollectionHandler.class));

        assertNull(dispacher.getPipedHttpHandler(RequestContext.TYPE.COLLECTION, RequestContext.METHOD.POST));
    }

    @Test
    public void testPostBinaryFileHandler() throws Exception {
        System.out.println("testPostBinaryFileHandler");
        
        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setRequestPath("/testdb/mybucket.files");
        exchange.setRequestMethod(new HttpString("POST"));
        RequestContext context = new RequestContext(exchange, "/", "/");
        
        dispacher.putPipedHttpHandler(RequestContext.TYPE.COLLECTION_FILES, RequestContext.METHOD.POST, new PostBinaryFileHandler(null, null));
        dispacher.handleRequest(exchange, context);
        
        assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, exchange.getResponseCode());
    }

//    @Test
//    public void testGridFS() throws UnknownHostException, IOException {
//        System.out.println("+++ testGridFS");
//        String filedb = "filedb";
//        Mongo mongo = new MongoClient();
//        if (mongo.getDatabaseNames().stream().filter(s -> s.equals(filedb)).count() > 0) {
//            System.out.println("Dropping old db");
//            mongo.getDB(filedb).dropDatabase();
//        }
//
//        DB db = mongo.getDB(filedb);
//
//        InputStream is = getClass().getResourceAsStream("/RESTHeart_documentation.pdf");
//
//        GridFS gridfs = new GridFS(db, "mybucket");
//        
//        GridFSInputFile gfsFile = gridfs.createFile(is);
//        gfsFile.setFilename("RESTHeart_documentation.pdf");
//        gfsFile.save();
//    }

}
