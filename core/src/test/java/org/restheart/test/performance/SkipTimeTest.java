/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.test.performance;

import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.BsonDocument;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import static org.restheart.mongodb.db.CursorPool.MIN_SKIP_DISTANCE_PERCENTAGE;
import org.restheart.mongodb.db.Databases;
import org.restheart.mongodb.db.MongoClientSingleton;
import static org.restheart.test.integration.AbstactIT.MONGO_URI;
import static org.restheart.test.integration.AbstactIT.TEST_DB_PREFIX;

@Ignore
public class SkipTimeTest {

    private static final int N = 5;
    private static final int REQUESTED_SKIPS = 1500000;
    private static final int POOL_SKIPS = 1400000;

    /**
     *
     * @throws Exception
     */
    @BeforeClass
    public static void setUpClass() throws Exception {
        MongoClientSingleton.init(MONGO_URI, null);
    }

    /**
     *
     */
    @AfterClass
    public static void tearDownClass() {
    }

    /**
     *
     */
    public SkipTimeTest() {
    }

    /**
     *
     */
    @Before
    public void setUp() {
    }

    /**
     *
     */
    @After
    public void tearDown() {
    }

    /**
     *
     */
    @Test
    public void testSkip() {


        final Databases dbs = Databases.get();
        MongoCollection<BsonDocument> coll = dbs.getCollection(TEST_DB_PREFIX, "huge");

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

    /**
     *
     */
    @Test
    public void testTwoSkips() {
        final var dbs = Databases.get();
        MongoCollection<BsonDocument> coll = dbs.getCollection(TEST_DB_PREFIX, "huge");

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
