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

import com.mongodb.DB;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import java.io.File;
import java.io.InputStream;
import java.net.UnknownHostException;
import org.apache.http.HttpHost;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class GetFileHandlerIT {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public static final String HOST = "localhost";
    public static final String FILENAME = "RESTHeart_documentation.pdf";
    public static final String DB_NAME = "testdb-" + System.currentTimeMillis();
    public static final String DB_URL = "http://localhost:18080/" + DB_NAME;
    public static final String BUCKET = "mybucket";
    public static Object OID;
    static Executor executor = null;

    @BeforeClass
    public static void setUpClass() throws UnknownHostException {
        DB db = getDatabase();
        InputStream is = GetFileHandlerIT.class.getResourceAsStream("/" + FILENAME);
        GridFS gridfs = new GridFS(db, BUCKET);
        GridFSInputFile gfsFile = gridfs.createFile(is);
        OID = gfsFile.getId();
        gfsFile.setFilename(FILENAME);
        gfsFile.save();

        executor = Executor.newInstance()
                .authPreemptive(new HttpHost(HOST, 18080, "HTTP"))
                .auth(new HttpHost(HOST), "admin", "changeit");
    }

    @AfterClass
    public static void afterClass() throws UnknownHostException {
        DB db = getDatabase();
        db.dropDatabase();
    }

    public GetFileHandlerIT() {
    }

    @Test
    public void testHandleRequest() throws Exception {
        System.out.println("testHandleRequest");
        String url = DB_URL + "/" + BUCKET + ".files/" + OID + "/binary";
        System.out.println("URL = " + url);
        Response resp = executor.execute(Request.Get(url));
        File tempFile = tempFolder.newFile(FILENAME);
        resp.saveContent(tempFile);
        assertTrue(tempFile.length() > 0);
    }

    private static DB getDatabase() throws UnknownHostException {
        Mongo mongo = new MongoClient();
        DB db = mongo.getDB(DB_NAME);
        return db;
    }

}
