package org.restheart.test.performance;

import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import java.nio.file.Paths;
import org.bson.BsonDocument;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.restheart.Configuration;
import static org.restheart.db.CursorPool.MIN_SKIP_DISTANCE_PERCENTAGE;
import org.restheart.db.Database;
import org.restheart.db.DbsDAO;
import org.restheart.db.MongoDBClientSingleton;

/**
 * this is to proof the advantage of dbcursor preallocation engine strategy it
 * needs a collection huge in the db test with more than SKIPS documents
 *
 * uncomment @Test annotation to enabled it
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
@Ignore
public class SkipTimeTest {

    private static final int N = 5;
    private static final int REQUESTED_SKIPS = 1500000;
    private static final int POOL_SKIPS = 1400000;

    @BeforeClass
    public static void setUpClass() throws Exception {
        MongoDBClientSingleton.init(new Configuration(Paths.get("etc/test/restheart-integrationtest.yml")).getMongoUri());
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

    @Test
    public void testSkip() {

        final Database dbsDAO = new DbsDAO();
        MongoCollection<BsonDocument> coll = dbsDAO.getCollection("test", "huge");

        long tot = 0;

        for (int cont = 0; cont < N; cont++) {
            long start = System.nanoTime();

            FindIterable<BsonDocument> docs = coll
                    .find()
                    .sort(new BasicDBObject("_id", -1))
                    .skip(REQUESTED_SKIPS);

            docs.iterator().next();

            long end = System.nanoTime();

            tot = tot + end - start;
        }

    }

    @Test
    public void testTwoSkips() {

        final Database dbsDAO = new DbsDAO();
        MongoCollection<BsonDocument> coll = dbsDAO.getCollection("test", "huge");

        long tot = 0;

        for (int cont = 0; cont < N; cont++) {
            int ACTUAL_POOL_SKIPS;

            FindIterable<BsonDocument> docs;

            if (REQUESTED_SKIPS - POOL_SKIPS <= Math.round(MIN_SKIP_DISTANCE_PERCENTAGE * REQUESTED_SKIPS)) {
                docs = coll.find().sort(new BasicDBObject("_id", -1)).skip(POOL_SKIPS);
                docs.first(); // force skips
                ACTUAL_POOL_SKIPS = POOL_SKIPS;
            } else {
                docs = coll.find().sort(new BasicDBObject("_id", -1)).skip(REQUESTED_SKIPS);
                ACTUAL_POOL_SKIPS = REQUESTED_SKIPS;
            }

            long start = System.nanoTime();

            MongoCursor<BsonDocument> cursor = docs.iterator();

            for (int cont2 = 0; cont2 < REQUESTED_SKIPS - ACTUAL_POOL_SKIPS; cont2++) {
                cursor.next();
            }

            cursor.next();
            long end = System.nanoTime();
            tot = tot + end - start;
        }

    }
}
