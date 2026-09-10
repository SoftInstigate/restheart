/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.db;

import java.util.List;

import org.bson.BsonValue;

import com.mongodb.bulk.BulkWriteResult;

/**
 * Represents the result of a bulk write operation in MongoDB.
 * <p>
 * This class extends {@link OperationResult} to provide additional information specific to
 * bulk write operations. It encapsulates both the HTTP status code and the MongoDB
 * {@link BulkWriteResult} containing details about the executed bulk operation.
 * </p>
 * 
 * <p>Bulk operations in MongoDB allow multiple write operations (inserts, updates, deletes)
 * to be executed in a single request, improving performance when dealing with multiple
 * documents.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * BulkWriteResult mongoResult = // result from MongoDB bulk operation
 * BulkOperationResult result = new BulkOperationResult(200, etag, mongoResult);
 * 
 * // Access bulk operation details
 * int insertedCount = result.getBulkResult().getInsertedCount();
 * int modifiedCount = result.getBulkResult().getModifiedCount();
 * int deletedCount = result.getBulkResult().getDeletedCount();
 * }</pre>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @see OperationResult
 * @see BulkWriteResult
 */
public class BulkOperationResult extends OperationResult {
    private final BulkWriteResult bulkResult;

    private final List<BsonValue> patchedIds;

    /**
     * Constructs a new BulkOperationResult with the specified HTTP status code, ETag, and bulk write result.
     * 
     * @param httpCode the HTTP status code representing the outcome of the operation
     *                 (e.g., 200 for success, 400 for bad request, etc.)
     * @param etag the entity tag for cache validation and optimistic concurrency control.
     *             Can be null if not applicable
     * @param bulkResult the MongoDB bulk write result containing details about the executed
     *                   bulk operation, including counts of inserted, updated, and deleted documents
     */
    public BulkOperationResult(int httpCode, Object etag,
                               BulkWriteResult bulkResult) {
        this(httpCode, etag, bulkResult, null);
    }

    /**
     * Constructs a new BulkOperationResult that also carries the ids of the documents the operation
     * touched.
     *
     * @param httpCode the HTTP status code representing the outcome of the operation
     * @param etag the entity tag for cache validation and optimistic concurrency control
     * @param bulkResult the MongoDB bulk write result
     * @param patchedIds the ids of the patched documents, or null when they were not collected
     */
    public BulkOperationResult(int httpCode, Object etag,
                               BulkWriteResult bulkResult, List<BsonValue> patchedIds) {
        super(httpCode, etag);

        this.bulkResult = bulkResult;
        this.patchedIds = patchedIds;
    }

    /**
     * The ids of the documents a bulk patch modified, when they were collected.
     *
     * <p>A bulk patch updates by filter and MongoDB reports counts, not identities, so what was
     * touched is normally unknown. They are collected when the operation runs in a transaction —
     * there the ids are read and updated atomically, so the set cannot go stale — and that is what
     * makes it possible to validate the outcome of a bulk patch and undo it.
     *
     * @return the ids of the patched documents, or {@code null} when they were not collected
     */
    public List<BsonValue> getPatchedIds() {
        return patchedIds;
    }

    /**
     * Returns the MongoDB bulk write result.
     * <p>
     * The bulk write result contains detailed information about the bulk operation, including:
     * </p>
     * <ul>
     *   <li>Number of documents inserted</li>
     *   <li>Number of documents matched for update</li>
     *   <li>Number of documents modified</li>
     *   <li>Number of documents deleted</li>
     *   <li>List of upserted document IDs (if any)</li>
     * </ul>
     * 
     * @return the MongoDB {@link BulkWriteResult} containing details of the bulk operation
     */
    public BulkWriteResult getBulkResult() {
        return bulkResult;
    }
}
