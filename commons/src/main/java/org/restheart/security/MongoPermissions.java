/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2024 SoftInstigate
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

package org.restheart.security;

import static org.restheart.plugins.ConfigurablePlugin.argValue;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonParseException;

import org.bson.BsonDocument;
import org.restheart.configuration.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.utils.BsonUtils;

/**
 * Encapsulates MongoDB-specific permissions for fine-grained access control over database operations.
 * 
 * <p>This class represents the MongoDB-specific portion of ACL permissions, defined by the `mongo`
 * property in permission configurations. It provides granular control over various aspects of
 * MongoDB operations including read/write filtering, request manipulation, and operation restrictions.</p>
 * 
 * <h2>Permission Components</h2>
 * <ul>
 *   <li><strong>Read/Write Filters:</strong> MongoDB query filters that restrict which documents
 *       a user can read or modify</li>
 *   <li><strong>Request Merging:</strong> Additional data to merge into requests before execution</li>
 *   <li><strong>Response Projection:</strong> Fields to include/exclude from response documents</li>
 *   <li><strong>Operation Flags:</strong> Boolean flags controlling specific operations like bulk
 *       updates or management requests</li>
 * </ul>
 * 
 * <h2>Filter Interpolation</h2>
 * <p>Filters support variable interpolation using {@link AclVarsInterpolator}, allowing dynamic
 * filters based on user context:</p>
 * <pre>{@code
 * {
 *   "readFilter": { "owner": "@user" },
 *   "writeFilter": { "owner": "@user", "created": { "$gte": "@now" } }
 * }
 * }</pre>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create permissions from configuration
 * var permissions = MongoPermissions.from(
 *     Map.of(
 *         "readFilter", "{ 'status': 'published' }",
 *         "writeFilter", "{ 'owner': '@user' }",
 *         "allowBulkDelete", false
 *     )
 * );
 * 
 * // Access permissions in a service
 * if (permissions.isAllowBulkDelete()) {
 *     // Process bulk delete request
 * }
 * 
 * // Apply read filter
 * var filter = permissions.getReadFilter();
 * collection.find(filter);
 * }</pre>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @since 5.0.0
 * @see BaseAclPermission
 * @see AclVarsInterpolator
 */
public class MongoPermissions {
    private final boolean allowManagementRequests;
    private final boolean allowBulkPatch;
    private final boolean allowBulkDelete;
    private final boolean allowWriteMode;
    private final BsonDocument mergeRequest;
    private final BsonDocument projectResponse;
    private final BsonDocument readFilter;
    private final BsonDocument writeFilter;

    /**
     * A predefined instance that allows all MongoDB operations without restrictions.
     * 
     * <p>This constant provides a convenient way to grant unrestricted MongoDB access. It has:</p>
     * <ul>
     *   <li>No read or write filters (all documents accessible)</li>
     *   <li>All operation flags set to true (all operations allowed)</li>
     *   <li>No request merging or response projection</li>
     * </ul>
     * 
     * <p><strong>Security Warning:</strong> Use with caution. This should only be assigned to
     * highly privileged administrative roles.</p>
     */
    public static final MongoPermissions ALLOW_ALL_MONGO_PERMISSIONS = new MongoPermissions(
        null, null,
        true, true, true, true,
        null, null);

    /**
     * Constructs a new MongoPermissions instance with all operations disabled.
     * 
     * <p>This default constructor creates the most restrictive permissions where:</p>
     * <ul>
     *   <li>All operation flags are set to false</li>
     *   <li>No filters, merging, or projections are defined</li>
     * </ul>
     * 
     * <p>This is suitable as a base for building up specific permissions or when
     * MongoDB access should be completely restricted.</p>
     */
    public MongoPermissions() {
        this.allowManagementRequests = false;
        this.allowBulkPatch = false;
        this.allowBulkDelete = false;
        this.allowWriteMode = false;
        this.readFilter = null;
        this.writeFilter = null;
        this.mergeRequest = null;
        this.projectResponse = null;
    }
</end_text>

<old_text>
    /**
     * Retrieves the MongoPermissions associated with the given request.
     * 
     * <p>This method extracts MongoDB-specific permissions from the ACL permission that
     * authorized the request. It looks for the raw permission data attached to the request
     * and converts it to a MongoPermissions instance.</p>
     * 
     * <p>The method handles various formats of permission data:</p>
     * <ul>
     *   <li>BsonDocument: Direct BSON representation of permissions</li>
     *   <li>Map: Configuration map containing permission settings</li>
     *   <li>null: Returns default (restrictive) permissions</li>
     * </ul>
     * 
     * @param request The request to extract permissions from
     * @return The MongoPermissions for this request, or default permissions if none are found
     * @throws ConfigurationException if the permission data is malformed
     * @throws IllegalArgumentException if the permission data type is not supported
     * @see BaseAclPermission#getRaw(Request)
     */
    public static MongoPermissions of(Request<?> request) throws ConfigurationException, IllegalArgumentException {
    public static MongoPermissions of(Request<?> request) {

    MongoPermissions(BsonDocument readFilter, BsonDocument writeFilter, boolean allowManagementRequests,
            boolean allowBulkPatch, boolean allowBulkDelete, boolean allowWriteMode,
            BsonDocument mergeRequest, BsonDocument projectResponse) {
        this.readFilter = readFilter == null ? null
                : readFilter.isNull() ? null : BsonUtils.escapeKeys(readFilter.asDocument(), true).asDocument();

        this.writeFilter = writeFilter == null ? null
                : writeFilter.isNull() ? null : BsonUtils.escapeKeys(writeFilter.asDocument(), true).asDocument();

        this.allowManagementRequests = allowManagementRequests;
        this.allowBulkPatch = allowBulkPatch;
        this.allowBulkDelete = allowBulkDelete;
        this.allowWriteMode = allowWriteMode;

        this.mergeRequest = mergeRequest;
        this.projectResponse = projectResponse;
    }

    public static MongoPermissions of(Request<?> request) throws ConfigurationException, IllegalArgumentException {
        return from(BaseAclPermission.getRaw(request));
    }

    /**
     * Creates MongoPermissions from a BaseAclPermission instance.
     * 
     * <p>This method extracts the raw permission data from the ACL permission and converts
     * it to MongoDB-specific permissions. This is useful when working directly with permission
     * objects rather than requests.</p>
     * 
     * @param p The ACL permission containing MongoDB permission data in its raw field
     * @return The MongoPermissions extracted from the ACL permission
     * @throws ConfigurationException if the permission data is malformed
     * @throws IllegalArgumentException if the permission data type is not supported
     * @see #from(Object)
     */
    public static MongoPermissions from(BaseAclPermission p) throws ConfigurationException, IllegalArgumentException {
        return from(p.getRaw());
    }

    /**
     * Creates MongoPermissions from raw permission data.
     * 
     * <p>This factory method handles conversion from various data formats to MongoPermissions.
     * It serves as the central parsing point for all permission data sources.</p>
     * 
     * <p>Supported input formats:</p>
     * <ul>
     *   <li><strong>null:</strong> Returns default (most restrictive) permissions</li>
     *   <li><strong>BsonDocument:</strong> Direct BSON representation with permission fields</li>
     *   <li><strong>Map:</strong> Configuration map, typically from YAML/JSON config files</li>
     * </ul>
     * 
     * <p>Any other type will result in an IllegalArgumentException.</p>
     * 
     * @param raw The raw permission data in one of the supported formats
     * @return A MongoPermissions instance based on the input data
     * @throws ConfigurationException if the data structure is invalid or contains bad values
     * @throws IllegalArgumentException if the data type is not supported
     * @see #from(Map)
     * @see #from(BsonDocument)
     */
    @SuppressWarnings("unchecked")
    public static MongoPermissions from(Object raw) throws ConfigurationException, IllegalArgumentException {
        if (raw == null) {
            return new MongoPermissions();
        } else if (raw instanceof BsonDocument) {
            return from((BsonDocument)raw);
        } else if (raw instanceof Map) {
            return from((Map<String, Object>)raw);
        } else {
            throw new IllegalArgumentException("MongoPemissions cannot be built from " + raw.getClass().getSimpleName());
        }
    }

    /**
     * Creates MongoPermissions from a BSON document.
     * 
     * <p>This method parses a BSON document containing MongoDB permission settings. The document
     * should have a "mongo" field containing the permission configuration. If the "mongo" field
     * is missing or null, default permissions are returned.</p>
     * 
     * <h3>Expected Document Structure</h3>
     * <pre>{@code
     * {
     *   "mongo": {
     *     "readFilter": { "status": "active" },
     *     "writeFilter": { "owner": "@user" },
     *     "allowBulkDelete": true,
     *     "mergeRequest": { "updatedBy": "@user" }
     *   }
     * }
     * }</pre>
     * 
     * <p>The method handles BSON type conversions and validates boolean fields. Invalid
     * configurations will result in a ConfigurationException with details about the error.</p>
     * 
     * @param doc The BSON document containing permission configuration
     * @return A MongoPermissions instance based on the document
     * @throws ConfigurationException if the document structure is invalid or contains invalid values
     * @see #from(Map)
     */
    public static MongoPermissions from(BsonDocument doc) throws ConfigurationException {
        if (doc == null || doc.isEmpty() || !doc.containsKey("mongo") || !doc.get("mongo").isDocument()) {
            // return default values
            return new MongoPermissions();
        } else {
            var mongoDoc = doc.get("mongo").asDocument();

            var _readFilter = mongoDoc.get("readFilter");

            if (!(_readFilter == null || _readFilter.isNull()) && !_readFilter.isDocument()) {
                throw new ConfigurationException("Wrong permission: readFilter must be a JSON object or null");
            }

            var readFilter = _readFilter == null ? null
                    : _readFilter.isNull() ? null : BsonUtils.escapeKeys(_readFilter.asDocument(), true).asDocument();

            var _writeFilter = mongoDoc.get("writeFilter");

            if (!(_writeFilter == null || _writeFilter.isNull()) && !_writeFilter.isDocument()) {
                throw new ConfigurationException("Wrong permission: writeFilter must be a JSON object or null");
            }

            var writeFilter = _writeFilter == null ? null
                    : _writeFilter.isNull() ? null : BsonUtils.escapeKeys(_writeFilter.asDocument(), true).asDocument();

            var _mergeRequest = mongoDoc.get("mergeRequest");

            if (!(_mergeRequest == null || _mergeRequest.isNull()) && !_mergeRequest.isDocument()) {
                throw new ConfigurationException("Wrong permission: mergeRequest must be a JSON object or null");
            }

            var mergeRequest = _mergeRequest == null ? null
                    : _mergeRequest.isNull() ? null : _mergeRequest.asDocument();

            var _projectResponse = mongoDoc.get("projectResponse");

            if (!(_projectResponse == null || _projectResponse.isNull()) && !_projectResponse.isDocument()) {
                throw new ConfigurationException("Wrong permission: _projectResponse must be a JSON object or null");
            }

            var projectResponse = _projectResponse == null ? null
                    : _projectResponse.isNull() ? null : _projectResponse.asDocument();

            if (projectResponse != null && projectResponse.isDocument()) {
                var zeros= false;
                var ones= false;

                for (var key: projectResponse.keySet()) {
                    if (projectResponse.get(key).isInt32()) {
                        if (projectResponse.get(key).asInt32().getValue() == 0) {
                            if (ones) {
                                throw new ConfigurationException(
                                "Wrong permission: the projectResponse contains invalid projection options, cannot have a mix of inclusion and exclusion");
                            }
                            zeros = true;
                        } else if (projectResponse.get(key).asInt32().getValue() == 1) {
                            if (zeros) {
                                throw new ConfigurationException(
                                "Wrong permission: the projectResponse contains invalid projection options, cannot have a mix of inclusion and exclusion");
                            }
                            ones = true;
                        } else {
                            throw new ConfigurationException(
                                "Wrong permission: the projectResponse contains invalid projection options, valid values are 0 and 1");
                        }
                    } else {
                        throw new ConfigurationException(
                            "Wrong permission: the projectResponse contains invalid projection options, valid values are 0 and 1");
                    }
                }
            } else {
                projectResponse = null;
            }

            return new MongoPermissions(readFilter, writeFilter, parseBooleanArg(mongoDoc, "allowManagementRequests"),
                    parseBooleanArg(mongoDoc, "allowBulkPatch"), parseBooleanArg(mongoDoc, "allowBulkDelete"),
                    parseBooleanArg(mongoDoc, "allowWriteMode"),
                    mergeRequest, projectResponse);
        }
    }

    /**
     * Converts this MongoPermissions instance to a BSON document representation.
     * 
     * <p>This method creates a BSON document containing all permission settings, which can be
     * used for serialization, logging, or passing permissions to other components. Only non-null
     * values and non-false boolean flags are included in the output.</p>
     * 
     * @return A BsonDocument representation of these permissions
     */
    public BsonDocument asBson() {
        var map = new HashMap<String, Object>();

        map.put("allowManagementRequests", this.allowManagementRequests);
        map.put("allowBulkPatch", this.allowBulkPatch);
        map.put("allowBulkDelete", this.allowBulkDelete);
        map.put("allowWriteMode", this.allowWriteMode);
        map.put("readFilter", this.readFilter);
        map.put("writeFilter", this.writeFilter);

        return BsonUtils.toBsonDocument(map);
    }

    /**
     * Creates MongoPermissions from a configuration map.
     * 
     * <p>This method parses MongoDB permission settings from a map structure, typically
     * loaded from configuration files. It supports the following properties:</p>
     * 
     * <table border="1">
     *   <tr><th>Property</th><th>Type</th><th>Description</th><th>Default</th></tr>
     *   <tr><td>readFilter</td><td>String/Doc</td><td>MongoDB filter for read operations</td><td>null</td></tr>
     *   <tr><td>writeFilter</td><td>String/Doc</td><td>MongoDB filter for write operations</td><td>null</td></tr>
     *   <tr><td>mergeRequest</td><td>String/Doc</td><td>Data to merge into requests</td><td>null</td></tr>
     *   <tr><td>projectResponse</td><td>String/Doc</td><td>Response projection</td><td>null</td></tr>
     *   <tr><td>allowManagementRequests</td><td>Boolean</td><td>Allow database/collection management</td><td>false</td></tr>
     *   <tr><td>allowBulkPatch</td><td>Boolean</td><td>Allow bulk PATCH operations</td><td>false</td></tr>
     *   <tr><td>allowBulkDelete</td><td>Boolean</td><td>Allow bulk DELETE operations</td><td>false</td></tr>
     *   <tr><td>allowWriteMode</td><td>Boolean</td><td>Allow ?wm query parameter</td><td>false</td></tr>
     * </table>
     * 
     * <p>Filter and document properties can be specified as:</p>
     * <ul>
     *   <li>JSON strings: {@code "{ 'status': 'active' }"}</li>
     *   <li>Nested maps/documents: {@code { status: "active" }}</li>
     * </ul>
     * 
     * @param args The configuration map containing MongoDB permission settings
     * @return A new MongoPermissions instance based on the configuration
     * @throws ConfigurationException if the configuration contains invalid values
     */
    @SuppressWarnings("unchecked")
    public static MongoPermissions from(Map<String, ?> args) throws ConfigurationException {
        if (args == null || args.isEmpty() || !args.containsKey("mongo") || !(args.get("mongo") instanceof Map<?,?>)) {
            // return default values
            return new MongoPermissions();
        } else {
            args = (Map<String, Object>) args.get("mongo");

            BsonDocument readFilter = null;
            BsonDocument writeFilter = null;

            if (args.containsKey("readFilter")) {
                try {
                    String __readFilter = argValue(args, "readFilter");

                    var _readFilter = BsonDocument.parse(__readFilter);

                    if (!(_readFilter == null || _readFilter.isNull()) && !_readFilter.isDocument()) {
                        throw new IllegalArgumentException(
                                "Wrong permission: readFilter must be a JSON object or null");
                    }

                    readFilter = _readFilter == null ? null
                            : _readFilter.isNull() ? null
                                    : BsonUtils.escapeKeys(_readFilter.asDocument(), true).asDocument();
                } catch (ClassCastException | JsonParseException jpe) {
                    throw new ConfigurationException(
                            "Wrong permission: the readFilter is not a string containing a JSON Object", jpe);
                }
            } else {
                readFilter = null;
            }

            if (args.containsKey("writeFilter")) {
                try {
                    String __writeFilter = argValue(args, "writeFilter");

                    var _writeFilter = BsonDocument.parse(__writeFilter);

                    if (!(_writeFilter == null || _writeFilter.isNull()) && !_writeFilter.isDocument()) {
                        throw new ConfigurationException("writeFilter must be a JSON object or null");
                    }

                    writeFilter = _writeFilter == null ? null
                            : _writeFilter.isNull() ? null
                                    : BsonUtils.escapeKeys(_writeFilter.asDocument(), true).asDocument();
                } catch (ClassCastException | JsonParseException jpe) {
                    throw new ConfigurationException(
                            "Wrong permission: the writeFilter is not a string defining a JSON Object", jpe);
                }
            } else {
                writeFilter = null;
            }

            BsonDocument mergeRequest = null;

            if (args.containsKey("mergeRequest")) {
                try {
                    String __mergeRequest = argValue(args, "mergeRequest");

                    var _mergeRequest = BsonDocument.parse(__mergeRequest);

                    if (!(_mergeRequest == null || _mergeRequest.isNull()) && !_mergeRequest.isDocument()) {
                        throw new ConfigurationException("mergeRequest must be a JSON object or null");
                    }

                    mergeRequest = _mergeRequest == null ? null
                            : _mergeRequest.isNull() ? null : _mergeRequest.asDocument();
                } catch (ClassCastException | JsonParseException jpe) {
                    throw new ConfigurationException(
                            "Wrong permission: the mergeRequest is not a string defining a JSON Object", jpe);
                }
            } else {
                mergeRequest = null;
            }

            BsonDocument projectResponse = null;

            if (args.containsKey("projectResponse")) {
                try {
                    String __projectResponse = argValue(args, "projectResponse");

                    var _projectResponse = BsonDocument.parse(__projectResponse);

                    if (!(_projectResponse == null || _projectResponse.isNull()) && !_projectResponse.isDocument()) {
                        throw new ConfigurationException("projectResponse must be a JSON object or null");
                    }

                    _projectResponse = _projectResponse == null ? null
                            : _projectResponse.isNull() ? null : _projectResponse.asDocument();

                    if (_projectResponse != null && _projectResponse.isDocument()) {
                        var zeros= false;
                        var ones= false;

                        for (var key: _projectResponse.keySet()) {
                            if (_projectResponse.get(key).isInt32()) {
                                if (_projectResponse.get(key).asInt32().getValue() == 0) {
                                    if (ones) {
                                        throw new ConfigurationException(
                                        "Wrong permission: the projectResponse contains invalid projection options, cannot have a mix of inclusion and exclusion");
                                    }
                                    zeros = true;
                                } else if (_projectResponse.get(key).asInt32().getValue() == 1) {
                                    if (zeros) {
                                        throw new ConfigurationException(
                                        "Wrong permission: the projectResponse contains invalid projection options, cannot have a mix of inclusion and exclusion");
                                    }
                                    ones = true;
                                } else {
                                    throw new ConfigurationException(
                                        "Wrong permission: the projectResponse contains invalid projection options, valid values are 0 and 1");
                                }
                            } else {
                                throw new ConfigurationException(
                                    "Wrong permission: the projectResponse contains invalid projection options, valid values are 0 and 1");
                            }
                        }

                        projectResponse = _projectResponse.asDocument();
                    }
                } catch (ClassCastException | JsonParseException jpe) {
                    throw new ConfigurationException(
                            "Wrong permission: the projectResponse is not a string defining a JSON Object", jpe);
                }
            } else {
                projectResponse = null;
            }

            return new MongoPermissions(readFilter, writeFilter, parseBooleanArg(args, "allowManagementRequests"),
                    parseBooleanArg(args, "allowBulkPatch"), parseBooleanArg(args, "allowBulkDelete"),
                    parseBooleanArg(args, "allowWriteMode"),
                    mergeRequest, projectResponse);
        }
    }

    /**
     * Parses a boolean value from a configuration map.
     * 
     * <p>This helper method extracts and validates boolean configuration values from a Map-based
     * permission configuration. It provides consistent error handling and default values.</p>
     * 
     * @param args The configuration map containing permission settings
     * @param key The key to look up in the configuration map
     * @return The boolean value if present and valid, false if not present (default)
     * @throws ConfigurationException if the value exists but is not a boolean
     */
    private static boolean parseBooleanArg(Map<String, Object> args, String key) throws ConfigurationException {
        if (args.containsKey(key)) {
            Object _value = argValue(args, key);

            if (_value != null && _value instanceof Boolean) {
                return (Boolean) _value;
            } else {
                throw new ConfigurationException("Wrong permission: mongo." + key + " must be a boolean");
            }
        } else {
            // default value
            return false;
        }
    }

    /**
     * Parses a boolean value from a BSON document.
     * 
     * <p>This helper method extracts and validates boolean configuration values from a BSON-based
     * permission configuration. It handles BSON type checking and provides consistent error messages.</p>
     * 
     * @param args The BSON document containing permission settings
     * @param key The key to look up in the BSON document
     * @return The boolean value if present and valid, false if not present (default)
     * @throws ConfigurationException if the value exists but is not a boolean
     */
    private static boolean parseBooleanArg(BsonDocument args, String key) throws ConfigurationException {
        if (args.containsKey(key)) {
            var _value = args.get(key);

            if (_value != null && _value.isBoolean()) {
                return _value.asBoolean().getValue();
            } else {
                throw new ConfigurationException("Wrong permission: mongo." + key + " must be a boolean");
            }
        } else {
            // default value
            return false;
        }
    }

    /**
     * Returns the MongoDB filter applied to read operations.
     * 
     * <p>This filter is automatically combined with user queries to restrict which documents
     * can be read. For example, a filter of {@code { "status": "published" }} ensures users
     * can only read published documents.</p>
     * 
     * @return The read filter as a BsonDocument, or null if no read filter is defined
     */
    public BsonDocument getReadFilter() {
        return readFilter == null || readFilter.isNull() ? null : BsonUtils.unescapeKeys(readFilter).asDocument();
    }

    /**
     * Returns the MongoDB filter applied to write operations.
     * 
     * <p>This filter restricts which documents can be modified or deleted. Write operations
     * that would affect documents not matching this filter are rejected. For example, a filter
     * of {@code { "owner": "@user" }} ensures users can only modify their own documents.</p>
     * 
     * @return The write filter as a BsonDocument, or null if no write filter is defined
     */
    public BsonDocument getWriteFilter() {
        return writeFilter == null || writeFilter.isNull() ? writeFilter
                : BsonUtils.unescapeKeys(writeFilter).asDocument();
    }

    /**
     * Returns the document to merge into incoming requests.
     * 
     * <p>This document is merged with request bodies before processing, allowing permissions
     * to inject required fields. Common uses include:</p>
     * <ul>
     *   <li>Setting ownership: {@code { "owner": "@user" }}</li>
     *   <li>Adding timestamps: {@code { "created": "@now" }}</li>
     *   <li>Enforcing defaults: {@code { "status": "draft" }}</li>
     * </ul>
     * 
     * @return The merge document as a BsonDocument, or null if no merging is configured
     */
    public BsonDocument getMergeRequest() {
        return mergeRequest;
    }

    /**
     * Returns the projection applied to response documents.
     * 
     * <p>This projection controls which fields are included or excluded in responses, following
     * MongoDB projection syntax. Examples:</p>
     * <ul>
     *   <li>Include only specific fields: {@code { "name": 1, "email": 1 }}</li>
     *   <li>Exclude sensitive fields: {@code { "password": 0, "ssn": 0 }}</li>
     * </ul>
     * 
     * @return The projection as a BsonDocument, or null if no projection is configured
     */
    public BsonDocument getProjectResponse() {
        return projectResponse;
    }

    /**
     * Returns whether management requests are allowed.
     * 
     * <p>Management requests include operations that create, modify, or delete databases
     * and collections. This flag must be true to allow:</p>
     * <ul>
     *   <li>Creating/dropping databases</li>
     *   <li>Creating/dropping collections</li>
     *   <li>Creating/dropping indexes</li>
     *   <li>Modifying collection options</li>
     * </ul>
     * 
     * @return Boolean indicating if management requests are allowed
     */
    /**
     * Checks if management requests are allowed.
     * 
     * @return true if management requests are allowed, false otherwise
     */
    public boolean isAllowManagementRequests() {
        return this.allowManagementRequests;
    }

    /**
     * Returns whether bulk PATCH operations are allowed.
     * 
     * <p>Bulk PATCH operations allow updating multiple documents in a single request using
     * MongoDB's bulk write API. This is more efficient than individual updates but requires
     * additional permissions due to its potential impact.</p>
     * 
     * @return Boolean indicating if bulk PATCH operations are allowed
     */
    /**
     * Checks if bulk PATCH operations are allowed.
     * 
     * @return true if bulk PATCH operations are allowed, false otherwise
     */
    public boolean isAllowBulkPatch() {
        return this.allowBulkPatch;
    }

    /**
     * Returns whether bulk DELETE operations are allowed.
     * 
     * <p>Bulk DELETE operations allow removing multiple documents matching a filter in a
     * single request. This flag must be true to allow operations that could potentially
     * delete large numbers of documents.</p>
     * 
     * @return Boolean indicating if bulk DELETE operations are allowed
     */
    /**
     * Checks if bulk DELETE operations are allowed.
     * 
     * @return true if bulk DELETE operations are allowed, false otherwise
     */
    public boolean isAllowBulkDelete() {
        return this.allowBulkDelete;
    }

    /**
     * Returns whether the write mode query parameter is allowed.
     * 
     * <p>The write mode parameter (?wm) allows clients to control how writes are handled:</p>
     * <ul>
     *   <li>INSERT: Only allow new document creation</li>
     *   <li>UPDATE: Only allow updates to existing documents</li>
     *   <li>UPSERT: Allow both inserts and updates</li>
     * </ul>
     * 
     * <p>When false, the server's default write mode is used.</p>
     * 
     * @return Boolean indicating if the write mode parameter is allowed
     */
    /**
     * Checks if the write mode query parameter is allowed.
     * 
     * @return true if the write mode parameter is allowed, false otherwise
     */
    public boolean isAllowWriteMode() {
        return this.allowWriteMode;
    }
}
