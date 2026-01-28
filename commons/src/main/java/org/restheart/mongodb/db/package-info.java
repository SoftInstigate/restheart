/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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

/**
 * Database operation result classes for MongoDB interactions.
 * 
 * <p>This package contains classes that represent the outcomes of various MongoDB database
 * operations. These classes provide a unified way to handle operation results, including
 * success/failure status, affected documents, and metadata such as ETags for optimistic
 * concurrency control.</p>
 * 
 * <h2>Core Classes</h2>
 * 
 * <h3>{@link org.restheart.mongodb.db.OperationResult}</h3>
 * <p>The base class for all operation results, providing:</p>
 * <ul>
 *   <li>HTTP status codes for RESTful responses</li>
 *   <li>ETag support for cache validation and optimistic locking</li>
 *   <li>Document state tracking (before and after operation)</li>
 *   <li>Error cause tracking for failed operations</li>
 *   <li>New document ID for insert operations</li>
 * </ul>
 * 
 * <h3>{@link org.restheart.mongodb.db.BulkOperationResult}</h3>
 * <p>Specialized result class for bulk write operations, extending OperationResult with:</p>
 * <ul>
 *   <li>Detailed bulk operation statistics</li>
 *   <li>Insert, update, and delete counts</li>
 *   <li>Upserted document information</li>
 * </ul>
 * 
 * <h2>Usage Patterns</h2>
 * 
 * <h3>Simple Operation Result</h3>
 * <pre>{@code
 * // Success with no additional data
 * OperationResult result = new OperationResult(200);
 * 
 * // Success with ETag
 * OperationResult result = new OperationResult(200, "etag-12345");
 * 
 * // Not found
 * OperationResult result = new OperationResult(404);
 * }</pre>
 * 
 * <h3>Document Modification Result</h3>
 * <pre>{@code
 * BsonDocument oldDoc = // ... original document
 * BsonDocument newDoc = // ... updated document
 * 
 * OperationResult result = new OperationResult(
 *     200,                    // HTTP status
 *     "new-etag",            // ETag for the new version
 *     oldDoc,                // Document before update
 *     newDoc                 // Document after update
 * );
 * }</pre>
 * 
 * <h3>Bulk Operation Result</h3>
 * <pre>{@code
 * BulkWriteResult mongoResult = collection.bulkWrite(operations);
 * 
 * BulkOperationResult result = new BulkOperationResult(
 *     200,                    // HTTP status
 *     null,                   // ETag (often not used for bulk)
 *     mongoResult            // MongoDB bulk result
 * );
 * 
 * // Access bulk statistics
 * int inserted = result.getBulkResult().getInsertedCount();
 * int modified = result.getBulkResult().getModifiedCount();
 * int deleted = result.getBulkResult().getDeletedCount();
 * }</pre>
 * 
 * <h3>Error Handling</h3>
 * <pre>{@code
 * try {
 *     // ... database operation
 * } catch (MongoException e) {
 *     OperationResult result = new OperationResult(
 *         500,                // Internal server error
 *         oldDoc,             // Original document if available
 *         e                   // The exception cause
 *     );
 * }
 * }</pre>
 * 
 * <h2>HTTP Status Code Conventions</h2>
 * 
 * <p>The package follows standard HTTP status codes:</p>
 * <ul>
 *   <li><b>200 OK</b> - Successful read or update operation</li>
 *   <li><b>201 Created</b> - Successful document creation</li>
 *   <li><b>204 No Content</b> - Successful deletion</li>
 *   <li><b>400 Bad Request</b> - Invalid request data</li>
 *   <li><b>404 Not Found</b> - Document or collection not found</li>
 *   <li><b>409 Conflict</b> - Concurrent modification conflict</li>
 *   <li><b>412 Precondition Failed</b> - ETag mismatch</li>
 *   <li><b>500 Internal Server Error</b> - Unexpected server error</li>
 * </ul>
 * 
 * <h2>ETag Support</h2>
 * 
 * <p>ETags (Entity Tags) are used for:</p>
 * <ul>
 *   <li><b>Optimistic Concurrency Control</b> - Prevent lost updates when multiple
 *       clients modify the same document</li>
 *   <li><b>HTTP Caching</b> - Enable efficient client-side caching with conditional
 *       requests (If-None-Match, If-Match headers)</li>
 * </ul>
 * 
 * <h2>Design Considerations</h2>
 * 
 * <p>The classes in this package are designed to:</p>
 * <ul>
 *   <li>Bridge MongoDB operations with RESTful HTTP responses</li>
 *   <li>Provide consistent error handling across different operation types</li>
 *   <li>Support RESTHeart's features like ETags and change streams</li>
 *   <li>Enable detailed operation tracking for audit and debugging</li>
 * </ul>
 * 
 * @since 4.0
 */
package org.restheart.mongodb.db;