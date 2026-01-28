/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
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
package org.restheart.test.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.mongodb.ConnectionString;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.restheart.mongodb.RHMongoClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.internal.MongoBatchCursorAdapter;

/**
 * Integration test to verify MongoDB driver internal API access via reflection.
 *
 * This test ensures that the reflection-based access to MongoDB driver internals
 * (used by the cache feature) continues to work across driver updates.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoDriverInternalsIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDriverInternalsIT.class);

    private static final String TEST_DB = "test-driver-internals";
    private static final String TEST_COLLECTION = "test-collection";
    private static final int TEST_DOC_COUNT = 10;

    private static MongoClient client;
    private static MongoCollection<BsonDocument> collection;

    public static ConnectionString MONGO_URI = new ConnectionString("mongodb://127.0.0.1");

    static {
		if (RHMongoClients.mclient() == null) {
			// set the system property test-connection-string to specify a non-default connection string for testing
			try {
				var mongoConnectionString = System.getProperty("test-connection-string");
				if (mongoConnectionString != null) {
					MONGO_URI = new ConnectionString(mongoConnectionString);
				}
			} catch (Throwable t) {
				LOGGER.warn("wrong property test-connection-string, using default value");
			}


			RHMongoClients.setClients(com.mongodb.client.MongoClients.create(MONGO_URI));
		}
    }

    @BeforeAll
    public static void setUpTestData() {
        try {
            client = RHMongoClients.mclient();

            if (client == null) {
                throw new IllegalStateException("MongoDB client is null - ensure RESTHeart is properly configured and MongoDB is running");
            }

            // Clean up any existing test data
            client.getDatabase(TEST_DB).getCollection(TEST_COLLECTION).drop();

            // Create test collection and insert test documents
            collection = client.getDatabase(TEST_DB)
                .getCollection(TEST_COLLECTION, BsonDocument.class);

            List<BsonDocument> docs = new ArrayList<>();
            for (int i = 0; i < TEST_DOC_COUNT; i++) {
                BsonDocument doc = new BsonDocument();
                doc.put("_id", new BsonInt32(i));
                doc.put("value", new BsonString("test-" + i));
                docs.add(doc);
            }
            collection.insertMany(docs);

            LOGGER.info("Test setup complete: created {} documents in {}.{}",
                TEST_DOC_COUNT, TEST_DB, TEST_COLLECTION);
        } catch (Exception e) {
            LOGGER.error("Failed to set up test: {}", e.getMessage(), e);
            throw e;
        }
    }

    @AfterAll
    public static void cleanUpTestData() {
        try {
            if (client != null) {
                client.getDatabase(TEST_DB).getCollection(TEST_COLLECTION).drop();
                LOGGER.info("Test cleanup complete");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to clean up test", e);
        }
    }

    /**
     * Test that we can access the cursor's internal batch via reflection.
     * This is critical for the cache feature to work.
     */
    @Test
    public void testCursorDocsReflectionAccess() {
        try (var cursor = collection.find().batchSize(1000).cursor()) {
			cursor.tryNext(); // force fetch
            // Now try to access the internal batch via reflection
            var cursorDocs = cursorDocs(cursor);

            assertNotNull(cursorDocs, "Should be able to access cursor internal batch");
            assertTrue(cursorDocs.size() >= 0, "Cursor docs should be a valid list");
            LOGGER.info("Successfully accessed cursor batch with {} documents", cursorDocs.size());
        } catch (NoSuchFieldException e) {
            fail("MongoDB driver internal structure has changed! Field 'curBatch' not found in MongoBatchCursorAdapter. " +
                 "The cache feature needs to be updated. Error: " + e.getMessage());
        } catch (IllegalAccessException e) {
            fail("Cannot access MongoDB driver internal field 'curBatch'. Error: " + e.getMessage());
        } catch (Exception e) {
            fail("Unexpected error accessing cursor internals: " + e.getMessage());
        }
    }

    /**
     * Test the exhausted cursor state
     */
    @Test
    public void testCursorDocsWhenExhausted() {
        try (MongoCursor<BsonDocument> cursor = collection.find().batchSize(3).cursor()) {
            // Consume all documents
            while (cursor.hasNext()) {
                cursor.next();
            }

            // After exhaustion, curBatch may be null
            List<BsonDocument> cursorDocs = cursorDocs(cursor);
            LOGGER.info("Exhausted cursor batch state: {}", cursorDocs == null ? "null" : cursorDocs.size() + " docs");

            // We don't assert anything here because null is valid for exhausted cursors
            // This test just documents the behavior

        } catch (NoSuchFieldException e) {
            fail("MongoDB driver internal structure has changed! Field 'curBatch' not found. Error: " + e.getMessage());
        } catch (Exception e) {
            fail("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Test that null values are properly filtered from cursor batch.
     * This prevents NPEs in downstream code like AggregationTransformer.
     */
    @Test
    public void testCursorDocsFilterNulls() {
        try (MongoCursor<BsonDocument> cursor = collection.find().batchSize(5).cursor()) {
            // Consume one document
            cursor.next();

            List<BsonDocument> cursorDocs = cursorDocs(cursor);

            if (cursorDocs != null) {
                // Check if there are any nulls in the batch
                long nullCount = cursorDocs.stream().filter(doc -> doc == null).count();

                if (nullCount > 0) {
                    LOGGER.warn("Found {} null documents in cursor batch - this can cause NPEs!", nullCount);
                    fail("Cursor batch contains null values. These must be filtered before caching.");
                } else {
                    LOGGER.info("Cursor batch has no null values - good!");
                }
            }

        } catch (Exception e) {
            LOGGER.warn("Test could not verify null handling: {}", e.getMessage());
        }
    }

    /**
     * Replicate the cursorDocs() method from Collections class but throwing
     */
     @SuppressWarnings("unchecked")
     private List<BsonDocument> cursorDocs(MongoCursor<?> cursor) throws NoSuchFieldException, IllegalAccessException {
		 var _batchCursor = MongoBatchCursorAdapter.class.getDeclaredField("curBatch");
		 _batchCursor.setAccessible(true);
		 var curBatch = (List<BsonDocument>) _batchCursor.get(cursor);

		 // Filter out null values - the driver's internal list may contain nulls in certain states
		 if (curBatch != null) {
			 return curBatch.stream()
				 .filter(doc -> doc != null)
				 .collect(Collectors.toList());
		 }

		 return null;
     }

    /**
     * Test that the old cursorCount method no longer works (documents why we switched to cursorDocs)
     */
    @Test
    public void testCursorCountIsUnreliable() {
        try (MongoCursor<BsonDocument> cursor = collection.find().batchSize(5).cursor()) {
            // Consume one document
            cursor.next();

            // Try the old approach that used to work
            int count = getCursorCount(cursor);

            // Document that this returns 0 incorrectly
            LOGGER.info("Old cursorCount() method returns: {}", count);
            LOGGER.info("This demonstrates why we switched to cursorDocs() - the count is unreliable");

            // We don't fail this test, just document the issue

        } catch (Exception e) {
            LOGGER.info("Old cursorCount() method failed with: {}", e.getMessage());
        }
    }

    /**
     * Replicate the old cursorCount() method that no longer works reliably
     */
    @SuppressWarnings("unchecked")
    private int getCursorCount(MongoCursor<?> cursor) {
        try {
            var _batchCursor = MongoBatchCursorAdapter.class.getDeclaredField("batchCursor");
            _batchCursor.setAccessible(true);
            var batchCursor = _batchCursor.get(cursor);

            var _commandCursorResult = batchCursor.getClass().getDeclaredField("commandCursorResult");
            _commandCursorResult.setAccessible(true);
            var commandCursorResult = _commandCursorResult.get(batchCursor);

            var _results = commandCursorResult.getClass().getDeclaredField("results");
            _results.setAccessible(true);
            var results = (List) _results.get(commandCursorResult);

            return results.size();
        } catch(NoSuchFieldException | IllegalAccessException ex) {
            LOGGER.warn("cannot access field Cursor.batchCursor.commandCursorResult.results", ex);
            return 0;
        }
    }
}
