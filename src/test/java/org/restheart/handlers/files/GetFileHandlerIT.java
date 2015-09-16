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
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import java.io.File;
import java.io.InputStream;
import java.net.UnknownHostException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.restheart.test.integration.AbstactIT;

/**
 *
 * @author Maurizio Turatti <maurizio@softinstigate.com>
 */
public class GetFileHandlerIT extends AbstactIT {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    public static final String FILENAME = "RESTHeart_documentation.pdf";
    public static final String BUCKET = "mybucket";
    public static Object OID;

    public GetFileHandlerIT() {
    }

    @Test
    public void testHandleRequest() throws Exception {
        createFile();
        
        String url = dbTmpUri + "/" + BUCKET + ".files/" + OID + "/binary";
        Response resp = adminExecutor.execute(Request.Get(url));
        File tempFile = tempFolder.newFile(FILENAME);
        resp.saveContent(tempFile);
        assertTrue(tempFile.length() > 0);
    }

    private void createFile() throws UnknownHostException {
        DB db = getDatabase();
        InputStream is = GetFileHandlerIT.class.getResourceAsStream("/" + FILENAME);
        GridFS gridfs = new GridFS(db, BUCKET);
        GridFSInputFile gfsFile = gridfs.createFile(is);
        OID = gfsFile.getId();
        gfsFile.setFilename(FILENAME);
        gfsFile.save();
    }

    private static DB getDatabase() throws UnknownHostException {
        return mongoClient.getDB(dbTmpName);
    }
}