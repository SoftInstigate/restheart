/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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
import org.restheart.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.utils.BsonUtils;

/**
 * Encapsulates the permissions specific to the MongoService, definined by `mongo` property of the permission
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

    public static final MongoPermissions ALLOW_ALL_MONGO_PERMISSIONS = new MongoPermissions(
        null, null,
        true, true, true, true,
        null, null);

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

    public static MongoPermissions from(BaseAclPermission p) throws ConfigurationException, IllegalArgumentException {
        return from(p.getRaw());
    }

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

    public static MongoPermissions from(BsonDocument args) throws ConfigurationException {
        if (args == null || args.isEmpty() || !args.containsKey("mongo") || !args.get("mongo").isDocument()) {
            // return default values
            return new MongoPermissions();
        } else {
            args = args.get("mongo").asDocument();

            var _readFilter = args.get("readFilter");

            if (!(_readFilter == null || _readFilter.isNull()) && !_readFilter.isDocument()) {
                throw new ConfigurationException("Wrong permission: readFilter must be a JSON object or null");
            }

            var readFilter = _readFilter == null ? null
                    : _readFilter.isNull() ? null : BsonUtils.escapeKeys(_readFilter.asDocument(), true).asDocument();

            var _writeFilter = args.get("writeFilter");

            if (!(_writeFilter == null || _writeFilter.isNull()) && !_writeFilter.isDocument()) {
                throw new ConfigurationException("Wrong permission: writeFilter must be a JSON object or null");
            }

            var writeFilter = _writeFilter == null ? null
                    : _writeFilter.isNull() ? null : BsonUtils.escapeKeys(_writeFilter.asDocument(), true).asDocument();

            var _mergeRequest = args.get("mergeRequest");

            if (!(_mergeRequest == null || _mergeRequest.isNull()) && !_mergeRequest.isDocument()) {
                throw new ConfigurationException("Wrong permission: mergeRequest must be a JSON object or null");
            }

            var mergeRequest = _mergeRequest == null ? null
                    : _mergeRequest.isNull() ? null : _mergeRequest.asDocument();

            var _projectResponse = args.get("projectResponse");

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

            return new MongoPermissions(readFilter, writeFilter, parseBooleanArg(args, "allowManagementRequests"),
                    parseBooleanArg(args, "allowBulkPatch"), parseBooleanArg(args, "allowBulkDelete"),
                    parseBooleanArg(args, "allowWriteMode"),
                    mergeRequest, projectResponse);
        }
    }

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

    @SuppressWarnings("unchecked")
    public static MongoPermissions from(Map<String, Object> args) throws ConfigurationException {
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
     * @return the readFilter
     */
    public BsonDocument getReadFilter() {
        return readFilter == null || readFilter.isNull() ? null : BsonUtils.unescapeKeys(readFilter).asDocument();
    }

    /**
     * @return the writeFilter
     */
    public BsonDocument getWriteFilter() {
        return writeFilter == null || writeFilter.isNull() ? writeFilter
                : BsonUtils.unescapeKeys(writeFilter).asDocument();
    }

    /**
     * @return the mergeRequest
     */
    public BsonDocument getMergeRequest() {
        return mergeRequest;
    }

    /**
     * @return the projectResponse
     */
    public BsonDocument getProjectResponse() {
        return projectResponse;
    }

    public boolean getAllowManagementRequests() {
        return this.allowManagementRequests;
    }

    public boolean isAllowManagementRequests() {
        return this.allowManagementRequests;
    }

    public boolean getAllowBulkPatch() {
        return this.allowBulkPatch;
    }

    public boolean isAllowBulkPatch() {
        return this.allowBulkPatch;
    }

    public boolean getAllowBulkDelete() {
        return this.allowBulkDelete;
    }

    public boolean isAllowBulkDelete() {
        return this.allowBulkDelete;
    }

    public boolean getAllowWriteMode() {
        return this.allowWriteMode;
    }

    public boolean isAllowWriteMode() {
        return this.allowWriteMode;
    }
}
