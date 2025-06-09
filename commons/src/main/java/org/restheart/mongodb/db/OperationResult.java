/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2025 SoftInstigate
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

import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * Represents the result of a database operation in MongoDB.
 * <p>
 * This class encapsulates the outcome of various MongoDB operations, providing a unified
 * way to handle operation results across different types of database interactions.
 * It includes HTTP status codes, ETags for optimistic concurrency control, and optionally
 * the old and new states of affected documents.
 * </p>
 * 
 * <p>The class supports various operation scenarios:</p>
 * <ul>
 *   <li>Simple operations with just an HTTP status code</li>
 *   <li>Operations with ETags for cache validation</li>
 *   <li>Operations that track document state changes (old and new data)</li>
 *   <li>Operations that generate new document IDs</li>
 *   <li>Failed operations with exception information</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Simple success result
 * OperationResult result = new OperationResult(200);
 * 
 * // Result with ETag
 * OperationResult result = new OperationResult(200, "etag-value");
 * 
 * // Result with document state change
 * BsonDocument oldDoc = new BsonDocument("name", new BsonString("John"));
 * BsonDocument newDoc = new BsonDocument("name", new BsonString("Jane"));
 * OperationResult result = new OperationResult(200, etag, oldDoc, newDoc);
 * }</pre>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class OperationResult {
    private final int httpCode;
    private final Object etag;
    private final BsonDocument newData;
    private final BsonDocument oldData;
    private final BsonValue newId;
    private final Throwable cause;

    /**
     * Constructs a new OperationResult with only an HTTP status code.
     * <p>
     * This constructor is typically used for simple operations where only the
     * success or failure status needs to be communicated, without additional
     * metadata or document state information.
     * </p>
     * 
     * @param httpCode the HTTP status code representing the outcome of the operation
     *                 (e.g., 200 for success, 404 for not found, 500 for server error)
     */
    public OperationResult(int httpCode) {
        this.httpCode = httpCode;
        this.etag = null;
        this.newData = null;
        this.oldData = null;
        this.newId = null;
        this.cause = null;
    }

    /**
     * Constructs a new OperationResult with HTTP status code and document state information.
     * <p>
     * This constructor is used when an operation modifies a document and both the
     * original and modified states need to be tracked. The newId is automatically
     * extracted from the newData document if present.
     * </p>
     * 
     * @param httpCode the HTTP status code representing the outcome of the operation
     * @param oldData the document state before the operation, can be null
     * @param newData the document state after the operation, can be null.
     *                If present and contains an "_id" field, it will be used as the newId
     */
    public OperationResult(int httpCode, BsonDocument oldData, BsonDocument newData) {
        this.httpCode = httpCode;
        this.etag = null;
        this.newData = newData;
        this.oldData = oldData;
        this.newId = newData == null ? null : newData.get("_id");
        this.cause = null;
    }

    /**
     * Constructs a new OperationResult with HTTP status code and ETag.
     * <p>
     * This constructor is used for operations that need to support HTTP caching
     * and optimistic concurrency control through ETags.
     * </p>
     * 
     * @param httpCode the HTTP status code representing the outcome of the operation
     * @param etag the entity tag for cache validation and optimistic concurrency control.
     *             Typically a version number, hash, or timestamp. Can be null
     */
    public OperationResult(int httpCode, Object etag) {
        this.httpCode = httpCode;
        this.etag = etag;
        this.newData = null;
        this.oldData = null;
        this.newId = null;
        this.cause = null;
    }

    /**
     * Constructs a new OperationResult with HTTP status code, ETag, and new document ID.
     * <p>
     * This constructor is typically used for insert operations where a new document
     * ID is generated and needs to be communicated back to the client.
     * </p>
     * 
     * @param httpCode the HTTP status code representing the outcome of the operation
     * @param etag the entity tag for cache validation and optimistic concurrency control
     * @param newId the ID of the newly created document, can be null
     */
    public OperationResult(int httpCode, Object etag, BsonValue newId) {
        this.httpCode = httpCode;
        this.etag = etag;
        this.newId = newId;
        this.newData = null;
        this.oldData = null;
        this.cause = null;
    }

    /**
     * Constructs a new OperationResult with HTTP status code, ETag, and document state information.
     * <p>
     * This constructor provides the most complete information about an operation,
     * including both caching support through ETag and full document state tracking.
     * </p>
     * 
     * @param httpCode the HTTP status code representing the outcome of the operation
     * @param etag the entity tag for cache validation and optimistic concurrency control
     * @param oldData the document state before the operation, can be null
     * @param newData the document state after the operation, can be null.
     *                If present and contains an "_id" field, it will be used as the newId
     */
    public OperationResult(int httpCode, Object etag, BsonDocument oldData, BsonDocument newData) {
        this.httpCode = httpCode;
        this.etag = etag;
        this.newData = newData;
        this.oldData = oldData;
        this.newId = newData == null ? null : newData.get("_id");
        this.cause = null;
    }

    /**
     * Constructs a new OperationResult with complete operation information including error details.
     * <p>
     * This constructor is used when an operation completes but with an error or exception
     * that needs to be tracked alongside the operation result.
     * </p>
     * 
     * @param httpCode the HTTP status code representing the outcome of the operation
     * @param etag the entity tag for cache validation and optimistic concurrency control
     * @param oldData the document state before the operation, can be null
     * @param newData the document state after the operation, can be null
     * @param cause the exception or error that occurred during the operation, can be null
     */
    public OperationResult(int httpCode, Object etag, BsonDocument oldData, BsonDocument newData, Throwable cause) {
        this.httpCode = httpCode;
        this.etag = etag;
        this.newData = newData;
        this.oldData = oldData;
        this.newId = newData == null ? null : newData.get("_id");
        this.cause = cause;
    }

    /**
     * Constructs a new OperationResult for a failed operation with error details.
     * <p>
     * This constructor is used when an operation fails and only the original document
     * state and error information need to be preserved.
     * </p>
     * 
     * @param httpCode the HTTP status code representing the failure (typically 4xx or 5xx)
     * @param oldData the document state before the failed operation, can be null
     * @param cause the exception or error that caused the operation to fail
     */
    public OperationResult(int httpCode, BsonDocument oldData, Throwable cause) {
        this.httpCode = httpCode;
        this.etag = null;
        this.newData = null;
        this.oldData = oldData;
        this.newId = null;
        this.cause = cause;
    }

    /**
     * Returns the HTTP status code of the operation.
     * <p>
     * The status code follows standard HTTP conventions:
     * </p>
     * <ul>
     *   <li>2xx - Success (200 OK, 201 Created, 204 No Content, etc.)</li>
     *   <li>4xx - Client errors (400 Bad Request, 404 Not Found, 409 Conflict, etc.)</li>
     *   <li>5xx - Server errors (500 Internal Server Error, etc.)</li>
     * </ul>
     * 
     * @return the HTTP status code
     */
    public int getHttpCode() {
        return httpCode;
    }

    /**
     * Returns the ETag associated with the operation result.
     * <p>
     * ETags are used for web cache validation and optimistic concurrency control.
     * They help prevent mid-air collisions when multiple clients try to update
     * the same resource.
     * </p>
     * 
     * @return the ETag value, or null if not applicable to this operation
     */
    public Object getEtag() {
        return etag;
    }

    /**
     * Returns the document state after the operation.
     * <p>
     * This represents the new state of the document after a successful modification
     * operation (update, replace, findAndModify, etc.). For insert operations,
     * this would be the newly inserted document.
     * </p>
     * 
     * @return the document after the operation, or null if not applicable or operation failed
     */
    public BsonDocument getNewData() {
        return newData;
    }

    /**
     * Returns the document state before the operation.
     * <p>
     * This represents the original state of the document before a modification
     * operation. Useful for operations that need to track what changed or for
     * rollback scenarios.
     * </p>
     * 
     * @return the document before the operation, or null if not applicable
     *         (e.g., for insert operations or when the document didn't exist)
     */
    public BsonDocument getOldData() {
        return oldData;
    }

    /**
     * Returns the ID of the newly created or modified document.
     * <p>
     * For insert operations, this is the generated document ID. For update operations
     * that result in a new document (upsert), this is the ID of the upserted document.
     * This value is automatically extracted from newData if it contains an "_id" field.
     * </p>
     * 
     * @return the document ID, or null if not applicable or no ID was generated
     */
    public BsonValue getNewId() {
        return newId;
    }

    /**
     * Returns the exception that caused the operation to fail.
     * <p>
     * If the operation completed with an error, this method returns the underlying
     * exception. This is useful for error handling and debugging purposes.
     * </p>
     * 
     * @return the exception that occurred during the operation, or null if the
     *         operation completed successfully or without throwing an exception
     */
    public Throwable getCause() {
        return cause;
    }
}
