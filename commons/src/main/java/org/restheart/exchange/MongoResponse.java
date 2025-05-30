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
package org.restheart.exchange;

import com.mongodb.client.MongoClient;
import com.mongodb.MongoCommandException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.bson.conversions.Bson;
import org.bson.json.JsonParseException;
import org.restheart.utils.HttpStatus;
import org.restheart.mongodb.db.OperationResult;
import org.restheart.utils.BsonUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static org.restheart.utils.BsonUtils.document;

/**
 * Response implementation specialized for MongoDB operations through RESTHeart.
 * <p>
 * This class extends BsonResponse to provide comprehensive support for MongoDB-specific
 * HTTP responses, including database operation results, warning messages, and
 * error handling. It handles the conversion of MongoDB operation results to
 * appropriate HTTP responses with proper status codes and content formatting.
 * </p>
 * <p>
 * MongoResponse supports all MongoDB operation types including:
 * <ul>
 *   <li>Document CRUD operations (create, read, update, delete)</li>
 *   <li>Collection and database operations</li>
 *   <li>Bulk operations and transactions</li>
 *   <li>GridFS file operations</li>
 *   <li>Index and aggregation operations</li>
 *   <li>Schema validation results</li>
 * </ul>
 * </p>
 * <p>
 * The class provides specialized features for MongoDB responses:
 * <ul>
 *   <li>Integration with MongoDB operation results and metadata</li>
 *   <li>Warning message collection and inclusion in responses</li>
 *   <li>Document count information for collection operations</li>
 *   <li>Rollback capabilities for failed operations</li>
 *   <li>Enhanced error formatting for MongoDB-specific exceptions</li>
 *   <li>Automatic JSON mode selection based on request preferences</li>
 * </ul>
 * </p>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class MongoResponse extends BsonResponse {
    private final static ReplaceOptions R_NOT_UPSERT_OPS = new ReplaceOptions().upsert(false);

    /** The result of the MongoDB database operation that generated this response. */
    private OperationResult dbOperationResult;

    /** List of warning messages to be included in the response. */
    private final List<String> warnings = new ArrayList<>();

    /** The total count of documents (for collection operations), or -1 if not applicable. */
    private long count = -1;

    /**
     * Constructs a new MongoResponse wrapping the given HTTP exchange.
     * <p>
     * This constructor is protected and should only be called by factory methods
     * or subclasses. Use {@link #init(HttpServerExchange)} or {@link #of(HttpServerExchange)}
     * to create instances.
     * </p>
     *
     * @param exchange the HTTP server exchange to wrap
     */
    protected MongoResponse(HttpServerExchange exchange) {
        super(exchange);
    }

    /**
     * Factory method to create a new MongoResponse instance.
     * <p>
     * This method creates a fresh MongoResponse instance for the given exchange
     * with MongoDB-specific capabilities enabled.
     * </p>
     *
     * @param exchange the HTTP server exchange for the MongoDB response
     * @return a new MongoResponse instance
     */
    public static MongoResponse init(HttpServerExchange exchange) {
        return new MongoResponse(exchange);
    }

    /**
     * Factory method to retrieve or create a MongoResponse from an existing exchange.
     * <p>
     * This method retrieves an existing MongoResponse instance that has been
     * previously attached to the exchange, or creates a new one if none exists.
     * </p>
     *
     * @param exchange the HTTP server exchange
     * @return the MongoResponse associated with the exchange
     */
    public static MongoResponse of(HttpServerExchange exchange) {
        return of(exchange, MongoResponse.class);
    }

    /**
     * Converts the BSON content to a JSON string for HTTP transmission.
     * <p>
     * This method serializes the internal BSON content to JSON format, applying
     * MongoDB-specific formatting rules and including warning messages when appropriate.
     * The JSON mode is determined from the request context if available.
     * </p>
     * <p>
     * For non-GET requests with document content, warning messages are automatically
     * included in the response. The JSON serialization respects the request's
     * preferred JSON mode (strict, relaxed, etc.) for proper MongoDB extended JSON formatting.
     * </p>
     *
     * @return the JSON string representation of the response content, or null if no content is set
     */
    @Override
    public String readContent() {
        var request = Request.of(wrapped);
        BsonValue tosend;

        if (!request.isGet() && (content == null || content.isDocument())) {
            tosend = addWarnings(content == null ? null : content.asDocument());
        } else {
            tosend = content;
        }

        if (tosend != null) {
            if (request instanceof MongoRequest) {
                return BsonUtils.toJson(tosend, ((MongoRequest) request).getJsonMode());
            } else {
                return BsonUtils.toJson(tosend);
            }
        } else {
            return null;
        }
    }

    /**
     * Adds warning messages to the response content.
     * <p>
     * This method integrates warning messages into the response content by adding
     * a "_warnings" field containing an array of warning strings. If the content
     * is null but warnings exist, a new document is created containing only the warnings.
     * </p>
     *
     * @param content the existing response content, or null
     * @return the content with warnings added, or the original content if no warnings exist
     */
    private BsonDocument addWarnings(BsonDocument content) {
        if (content != null) {
            if (warnings != null && !warnings.isEmpty() && content.isDocument()) {
                var contentWithWarnings = new BsonDocument();

                var ws = new BsonArray();

                warnings.stream().map(w -> new BsonString(w)).forEachOrdered(ws::add);

                contentWithWarnings.put("_warnings", ws);

                contentWithWarnings.putAll(content.asDocument());

                return contentWithWarnings;
            } else {
                return content;
            }
        } else if (warnings != null && !warnings.isEmpty()) {
            var contentWithWarnings = new BsonDocument();

            var ws = new BsonArray();

            warnings.stream().map(w -> new BsonString(w)).forEachOrdered(ws::add);

            contentWithWarnings.put("_warnings", ws);

            return contentWithWarnings;
        } else {
            return content;
        }
    }

    /**
     * Returns the MongoDB database operation result associated with this response.
     * <p>
     * The operation result contains metadata about the MongoDB operation that was
     * performed, including information about modified documents, operation success,
     * and any relevant database-specific details.
     * </p>
     *
     * @return the database operation result, or null if no operation was performed
     */
    public OperationResult getDbOperationResult() {
        return dbOperationResult;
    }

    /**
     * Sets the MongoDB database operation result for this response.
     * <p>
     * This method is typically called by MongoDB service handlers to attach
     * operation metadata to the response for later processing or error handling.
     * </p>
     *
     * @param dbOperationResult the database operation result to associate with this response
     */
    public void setDbOperationResult(OperationResult dbOperationResult) {
        this.dbOperationResult = dbOperationResult;
    }

    /**
     * Returns an unmodifiable list of warning messages associated with this response.
     * <p>
     * Warning messages are non-fatal issues that occurred during request processing
     * that the client should be aware of. They are included in the response content
     * under the "_warnings" field.
     * </p>
     *
     * @return an unmodifiable list of warning messages
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * Adds a warning message to this response.
     * <p>
     * Warning messages are collected during request processing and automatically
     * included in the response content. Common warnings include deprecated API usage,
     * performance concerns, or non-critical operation issues.
     * </p>
     *
     * @param warning the warning message to add
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }

    /**
     * Sets the response in an error state with MongoDB-specific error formatting.
     * <p>
     * This method configures the response for error conditions by setting the HTTP status code
     * and creating a standardized error response document with MongoDB-specific error handling.
     * The error response includes HTTP status information, custom messages, exception details,
     * and any accumulated warning messages.
     * </p>
     *
     * @param code the HTTP status code to set (e.g., 400, 404, 500)
     * @param message the error message to include in the response, or null
     * @param t an optional throwable that caused the error
     */
    @Override
    public void setInError(int code, String message, Throwable t) {
        setStatusCode(code);
        setInError(true);
        setContent(getErrorContent(code, HttpStatus.getStatusText(code), message, t, false));
    }

    /**
     * Returns the document count for collection operations.
     * <p>
     * This count represents the total number of documents in a collection or
     * the number of documents matching a query, depending on the operation context.
     * A value of -1 indicates that count information is not applicable or available.
     * </p>
     *
     * @return the document count, or -1 if not applicable
     */
    public long getCount() {
        return count;
    }

    /**
     * Sets the document count for collection operations.
     * <p>
     * This method is typically called by collection handlers to provide count
     * information in responses, particularly for paginated results or collection
     * metadata operations.
     * </p>
     *
     * @param count the document count to set, or -1 if not applicable
     */
    public void setCount(long count) {
        this.count = count;
    }

    /**
     * Creates a standardized error response document with MongoDB-specific formatting.
     * <p>
     * This method generates a comprehensive error response that includes HTTP status
     * information, custom error messages, exception details, and warning messages.
     * Special handling is provided for MongoDB-specific exceptions like MongoCommandException.
     * </p>
     *
     * @param code the HTTP status code
     * @param httpStatusText the HTTP status text description
     * @param message custom error message, or null
     * @param t the throwable that caused the error, or null
     * @param includeStackTrace whether to include the full stack trace
     * @return a BsonDocument containing the formatted error response
     */
    private BsonDocument getErrorContent(int code,
            String httpStatusText,
            String message,
            Throwable t,
            boolean includeStackTrace) {
        var rep = new BsonDocument();

        rep.put("http status code", new BsonInt32(code));
        rep.put("http status description", new BsonString(httpStatusText));

        if (message != null) {
            rep.put("message", new BsonString(avoidEscapedChars(message)));
        }

        if (t != null) {
            rep.put("exception", new BsonString(t.getClass().getName()));

            if (t.getMessage() != null) {
                if (t instanceof JsonParseException) {
                    rep.put("exception message", new BsonString("invalid json"));
                } else if (t instanceof MongoCommandException mce) {
                    var errorDoc = document().put("code", mce.getResponse().get("code"))
                        .put("codeName", mce.getResponse().get("codeName"));

                    var errmsg = mce.getResponse().get("errmsg");

                    // the erromsg in some cases can contain input data
                    // that can be huge or contain sensitive information
                    // let truncate errmsg at 100chars
                    if (errmsg != null && errmsg.isString()) {
                        var _errmsg = errmsg.asString().getValue();
                        _errmsg = _errmsg.length() <= 100 ? _errmsg: _errmsg.substring(0, 100) + "...";
                        errorDoc.put("errmsg", _errmsg);
                    }

                    rep.put("exception message", errorDoc.get());
                } else {
                    rep.put("exception message", new BsonString(avoidEscapedChars(t.getMessage())));
                }
            }

            if (includeStackTrace) {
                BsonArray stackTrace = getStackTrace(t);

                if (stackTrace != null) {
                    rep.put("stack trace", stackTrace);
                }
            }
        }

        var _warnings = new BsonArray();

        // add warnings
        if (getWarnings() != null && !getWarnings().isEmpty()) {
            getWarnings().forEach(w -> _warnings.add(new BsonString(w)));

            rep.put("_warnings", _warnings);
        }

        return rep;
    }

    /**
     * Converts a throwable's stack trace to a BSON array format.
     * <p>
     * This method extracts the complete stack trace from a throwable and converts
     * it into a BSON array where each line of the stack trace becomes a separate
     * array element. The content is sanitized to prevent JSON formatting issues.
     * </p>
     *
     * @param t the throwable to extract stack trace from, or null
     * @return a BSON array containing the stack trace lines, or null if no stack trace available
     */
    private BsonArray getStackTrace(Throwable t) {
        if (t == null || t.getStackTrace() == null) {
            return null;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        String st = sw.toString();
        st = avoidEscapedChars(st);
        String[] lines = st.split("\n");

        BsonArray list = new BsonArray();

        for (String line : lines) {
            list.add(new BsonString(line));
        }

        return list;
    }

    /**
     * Sanitizes string content to avoid JSON escaping issues.
     * <p>
     * This method replaces potentially problematic characters in strings that
     * will be included in JSON responses. It converts double quotes to single
     * quotes and tabs to spaces to prevent JSON parsing issues.
     * </p>
     *
     * @param s the string to sanitize, or null
     * @return the sanitized string, or null if input was null
     */
    private String avoidEscapedChars(String s) {
        return s == null
                ? null
                : s.replaceAll("\"", "'").replaceAll("\t", "  ");
    }

    /**
     * Restores a document to its previous state, effectively rolling back changes.
     * <p>
     * This method can be used when verification of a document after being updated
     * determines that the changes should be reverted. A common use case is when
     * the request body contains update operators and an Interceptor cannot verify
     * the update at REQUEST time; it can check at RESPONSE time and restore the
     * original data if the updated document doesn't fulfill required conditions.
     * </p>
     * <p>
     * The rollback operation:
     * <ul>
     *   <li>For updated documents: restores the original document using the old data</li>
     *   <li>For created documents: deletes the newly created document</li>
     *   <li>Updates response headers (ETag, Location) appropriately</li>
     * </ul>
     * </p>
     * <p>
     * <strong>Note:</strong> rollback() does not support bulk updates due to the
     * complexity of tracking multiple document states.
     * </p>
     *
     * @param mclient the MongoClient instance to use for the rollback operation
     * @throws Exception if any error occurs during the rollback operation
     * @throws UnsupportedOperationException if called on bulk update operations
     */
    public void rollback(MongoClient mclient) throws Exception {
        var request = MongoRequest.of(getExchange());
        var response = MongoResponse.of(getExchange());

        if (request.isBulkDocuments() || (request.isPost() && request.getContent() != null && request.getContent().isArray())) {
            throw new UnsupportedOperationException("rollback() does not support bulk updates");
        }

        var mdb = mclient.getDatabase(request.getDBName());

        var coll = mdb.getCollection(request.getCollectionName(), BsonDocument.class);

        var oldData = getDbOperationResult().getOldData();

        var newEtag = getDbOperationResult().getEtag();

        if (oldData != null) {
            // document was updated, restore old one
            restoreDocument(
                request.getClientSession(),
                coll,
                oldData.get("_id"),
                request.getShardKey(),
                oldData,
                newEtag,
                "_etag");

            // add to response old etag
            if (oldData.get("$set") != null
                && oldData.get("$set").isDocument()
                && oldData.get("$set")
                        .asDocument()
                        .get("_etag") != null) {
                response.getHeaders().put(Headers.ETAG,
                    oldData.get("$set")
                            .asDocument()
                            .get("_etag")
                            .asObjectId()
                            .getValue()
                            .toString());
            } else {
                response.getHeaders().remove(Headers.ETAG);
            }

        } else {
            // document was created, delete it
            var newId = getDbOperationResult().getNewData().get("_id");

            coll.deleteOne(and(eq("_id", newId), eq("_etag", newEtag)));

            response.getHeaders().remove(Headers.LOCATION);
            response.getHeaders().remove(Headers.ETAG);
        }
    }

    /**
     * Restores a specific document in a MongoDB collection to its previous state.
     * <p>
     * This method performs the actual document restoration by replacing the current
     * document with the provided data. It handles ETag validation to ensure that
     * the document hasn't been modified by another operation since the rollback
     * was initiated.
     * </p>
     *
     * @param cs the client session for transaction support, or null for no session
     * @param coll the MongoDB collection containing the document
     * @param documentId the ID of the document to restore
     * @param shardKeys shard key values for sharded collections, or null
     * @param data the original document data to restore
     * @param etag the expected ETag value for optimistic concurrency control
     * @param etagLocation the field name where the ETag is stored (typically "_etag")
     * @return true if the document was successfully restored, false otherwise
     */
    private static boolean restoreDocument(
        final ClientSession cs,
        final MongoCollection<BsonDocument> coll,
        final Object documentId,
        final BsonDocument shardKeys,
        final BsonDocument data,
        final Object etag,
        final String etagLocation) {
        Objects.requireNonNull(coll);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(data);

        Bson query;

        if (etag == null) {
            query = eq("_id", documentId);
        } else {
            query = and(eq("_id", documentId), eq(etagLocation != null && !etagLocation.isEmpty() ? etagLocation : "_etag", etag));
        }

        if (shardKeys != null) {
            query = and(query, shardKeys);
        }

        var result = cs == null
            ? coll.replaceOne(query, data, R_NOT_UPSERT_OPS)
            : coll.replaceOne(cs, query, data, R_NOT_UPSERT_OPS);

        return result.getModifiedCount() == 1;
    }
}
