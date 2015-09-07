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
package org.restheart.test.performance;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.io.File;
import java.nio.file.Path;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.restheart.db.Database;
import org.restheart.db.DbsDAO;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.utils.FileUtils;

/**
 * this is to proof the advantage of dbcursor preallocation engine strategy
 * it needs a collection huge in the db test with more than SKIPS documents
 * 
 * uncomment @Test annotation to enabled it
 * 
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class SkipTimeTest {
    
    private static final int N = 5;
    private static final int SKIPS = 1000000;
    private static final int DELTA = 1000;
    
    private static final Path CONF_FILE = new File("./etc/restheart-dev.yml").toPath();

    public SkipTimeTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        MongoDBClientSingleton.init(FileUtils.getConfiguration(CONF_FILE, false));
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
    public void testSkip() {
        System.out.println("skipping cursor " + N + " times by " + SKIPS + " documents");
        
        final Database dbsDAO = new DbsDAO();
        DBCollection coll = dbsDAO.getCollection("test", "huge");
        
        long tot = 0;
        
        for (int cont = 0; cont < N; cont++) {
            long start = System.nanoTime();
            
            DBCursor cursor = coll.find().sort(new BasicDBObject("_id", -1)).skip(SKIPS);
            DBObject data = cursor.next();
            
            long end = System.nanoTime();
            
            System.out.println("\t" + data.get("_id") + " took " + ((end - start) / 1000000000d) + " sec");
            
            tot = tot + end - start;
        }
        
        System.out.println("*** total time: " + (tot / 1000000000d) + " sec");
    }
    
    //@Test
    public void testTwoSkips() {
        System.out.println("skipping cursor that has been pre-skipped by " + (SKIPS-DELTA) + " documents, " + N + " times by " + DELTA + " documents");
        
        final Database dbsDAO = new DbsDAO();
        DBCollection coll = dbsDAO.getCollection("test", "huge");
        
        long tot = 0;
        
        for (int cont = 0; cont < N; cont++) {
            DBObject data;
            
            System.out.print("\tpreskipping ");
            DBCursor cursor = coll.find().sort(new BasicDBObject("_id", -1)).skip(SKIPS-DELTA);
            cursor.hasNext(); // force skips
            System.out.println("done");
            
            long start = System.nanoTime();
            
            // this cursor has been pre-skipped, just DELTA to go.
            for (int cont2 = 0; cont2 < DELTA; cont2++) {
                cursor.next();
            }
            
            data = cursor.next();
            
            long end = System.nanoTime();
            
            tot = tot + end - start;
            
            System.out.println("\t" + data.get("_id") + " took " + ((end - start) / 1000000000d) + " sec");
        }
        
        System.out.println("*** total time: " + (tot / 1000000000d) + " sec");
    }
}
