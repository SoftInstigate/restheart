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

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.UnknownHostException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.restheart.handlers.files.PutFileHandler;

/**
 *
 * @author Maurizio Turatti <info@maurizioturatti.com>
 */
public class RequestDispacherHandlerTest {

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
    }

    @After
    public void tearDown() {
    }

    //@Test
    public void testGridFS() throws UnknownHostException, IOException {
        System.out.println("+++ testGridFS");
        String filedb = "filedb";
        Mongo mongo = new MongoClient();
        if (mongo.getDatabaseNames().stream().filter(s -> s.equals(filedb)).count() > 0) {
            System.out.println("Dropping old db");
            mongo.getDB(filedb).dropDatabase();
        }

        DB db = mongo.getDB(filedb);

        InputStream is = getClass().getResourceAsStream("/RESTHeart_documentation.pdf");

        GridFS gfsPhoto = new GridFS(db, "mybucket");
        GridFSInputFile gfsFile = gfsPhoto.createFile(is);
        gfsFile.save();
    }

    //@Test
    public void test_put_file_request() throws Exception {
        System.out.println("+++ test_put_file_request");

        HttpServerExchange exchange = new HttpServerExchange();
        exchange.setRequestPath("/testdb/testcoll/files");
        exchange.setRequestMethod(new HttpString("PUT"));

        RequestContext context = new RequestContext(exchange, "/", "/");

        RequestDispacherHandlerBuilder builder = new RequestDispacherHandlerBuilder();
        builder.setFilePut(new PutFileHandler());
        RequestDispacherHandler instance = builder.createRequestDispacherHandler();
        instance.handleRequest(exchange, context);

        assertEquals(201, exchange.getResponseCode());
    }

}
