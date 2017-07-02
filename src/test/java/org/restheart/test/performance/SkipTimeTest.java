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
import static org.restheart.db.CursorPool.MIN_SKIP_DISTANCE_PERCENTAGE;
import org.restheart.db.Database;
import org.restheart.db.DbsDAO;
import org.restheart.db.MongoDBClientSingleton;
import org.restheart.utils.FileUtils;

/**
 * this is to proof the advantage of dbcursor preallocation engine strategy it
 * needs a collection huge in the db test with more than SKIPS documents
 *
 * uncomment @Test annotation to enabled it
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class SkipTimeTest {

    private static final int N = 5;
    private static final int REQUESTED_SKIPS = 1500000;
    private static final int POOL_SKIPS = 1400000;

    private static final Path CONF_FILE = new File("./etc/restheart-dev.yml").toPath();

    @BeforeClass
    public static void setUpClass() throws Exception {
        MongoDBClientSingleton.init(FileUtils.getConfiguration(CONF_FILE, false));
    }

    @AfterClass
    public static void tearDownClass() {
    }

    public SkipTimeTest() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    //@Test
    public void testSkip() {
        System.out.println("skipping cursor " + N + " times by " + REQUESTED_SKIPS + " documents");

        final Database dbsDAO = new DbsDAO();
        DBCollection coll = dbsDAO.getCollectionLegacy("test", "huge");

        long tot = 0;

        for (int cont = 0; cont < N; cont++) {
            long start = System.nanoTime();

            DBCursor cursor = coll.find().sort(new BasicDBObject("_id", -1)).skip(REQUESTED_SKIPS);
            DBObject data = cursor.next();

            long end = System.nanoTime();

            tot = tot + end - start;
        }

    }

    //@Test
    public void testTwoSkips() {
        System.out.println("skipping cursor that has been pre-skipped by " + POOL_SKIPS + " documents, " + N + " times by " + (REQUESTED_SKIPS - POOL_SKIPS) + " documents");

        final Database dbsDAO = new DbsDAO();
        DBCollection coll = dbsDAO.getCollectionLegacy("test", "huge");

        long tot = 0;

        for (int cont = 0; cont < N; cont++) {
            int ACTUAL_POOL_SKIPS;

            DBCursor cursor;

            if (REQUESTED_SKIPS - POOL_SKIPS <= Math.round(MIN_SKIP_DISTANCE_PERCENTAGE * REQUESTED_SKIPS)) {
                System.out.print("\tpreskipping ");
                cursor = coll.find().sort(new BasicDBObject("_id", -1)).skip(POOL_SKIPS);
                cursor.hasNext(); // force skips
                ACTUAL_POOL_SKIPS = POOL_SKIPS;
            } else {
                cursor = coll.find().sort(new BasicDBObject("_id", -1)).skip(REQUESTED_SKIPS);
                ACTUAL_POOL_SKIPS = REQUESTED_SKIPS;
            }

            long start = System.nanoTime();

            System.out.print("\tskipping data with next() ");

            for (int cont2 = 0; cont2 < REQUESTED_SKIPS - ACTUAL_POOL_SKIPS; cont2++) {
                cursor.next();
            }

            DBObject data = cursor.next();

            long end = System.nanoTime();

            tot = tot + end - start;

        }

    }
}
