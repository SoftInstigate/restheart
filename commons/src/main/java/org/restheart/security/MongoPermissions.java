/*-
 * ========================LICENSE_START=================================
 * restheart-security
 * %%
 * Copyright (C) 2018 - 2020 SoftInstigate
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

package org.restheart.security;

import static org.restheart.plugins.ConfigurablePlugin.argValue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonParseException;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.ConfigurationException;
import org.restheart.exchange.Request;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.LambdaUtils;

/**
 * Encapsulates the permissions specific to the MongoService, definined by `mongo` property of the permission
 */
public class MongoPermissions {
    final boolean allowManagementRequests;
    final boolean allowBulkPatch;
    final boolean allowBulkDelete;
    final boolean allowWriteMode;
    private final BsonDocument readFilter;
    private final BsonDocument writeFilter;

    // an hidden property is removed from the response a GET requests
    final Set<String> hideProps = Sets.newHashSet();
    // the value of an overridden property is set by the server
    final Map<String, BsonValue> overrideProps = Maps.newHashMap();

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
    }

    MongoPermissions(BsonDocument readFilter, BsonDocument writeFilter, boolean allowManagementRequests,
            boolean allowBulkPatch, boolean allowBulkDelete, boolean allowWriteMode,
            Set<String> hideProps, Map<String, BsonValue> overrideProps) {
        this.readFilter = readFilter == null ? null
                : readFilter.isNull() ? null : BsonUtils.escapeKeys(readFilter.asDocument(), true).asDocument();

        this.writeFilter = writeFilter == null ? null
                : writeFilter.isNull() ? null : BsonUtils.escapeKeys(writeFilter.asDocument(), true).asDocument();

        this.allowManagementRequests = allowManagementRequests;
        this.allowBulkPatch = allowBulkPatch;
        this.allowBulkDelete = allowBulkDelete;
        this.allowWriteMode = allowWriteMode;

        if (hideProps != null) {
            this.hideProps.addAll(hideProps);
        }

        if (overrideProps != null) {
            this.overrideProps.putAll(overrideProps);
        }
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

            return new MongoPermissions(readFilter, writeFilter, parseBooleanArg(args, "allowManagementRequests"),
                    parseBooleanArg(args, "allowBulkPatch"), parseBooleanArg(args, "allowBulkDelete"),
                    parseBooleanArg(args, "allowWriteMode"), parseSetArg(args, "hideProps"),
                    parseMapArg(args, "overrideProps"));
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
                            "Wrong permission: the writeFilter is not a string containing a JSON Object", jpe);
                }
            } else {
                writeFilter = null;
            }

            return new MongoPermissions(readFilter, writeFilter, parseBooleanArg(args, "allowManagementRequests"),
                    parseBooleanArg(args, "allowBulkPatch"), parseBooleanArg(args, "allowBulkDelete"),
                    parseBooleanArg(args, "allowWriteMode"), parseSetArg(args, "hideProps"),
                    parseMapArg(args, "overrideProps"));
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

    private static Set<String> parseSetArg(Map<String, Object> args, String key) throws ConfigurationException {
        if (args.containsKey(key)) {
            Object _value = argValue(args, key);

            if (_value != null && _value instanceof List<?>) {
                HashSet<String> ret = Sets.newHashSet();
                ;
                List<?> _set = (List<?>) _value;

                for (var _entry : _set) {
                    if (_entry instanceof String) {
                        ret.add((String) _entry);
                    } else {
                        throw new ConfigurationException("Wrong permission: mongo." + key + " must be a list of strings");
                    }
                }
                return ret;
            } else {
                throw new ConfigurationException("Wrong permission: mongo." + key + " must be a list of strings");
            }
        } else {
            return Sets.newHashSet();
        }
    }

    private static Map<String, BsonValue> parseMapArg(Map<String, Object> args, String key)
            throws ConfigurationException {
        if (args.containsKey(key)) {
            Object _value = argValue(args, key);

            if (_value != null && _value instanceof Map<?, ?>) {
                Map<String, BsonValue> ret = Maps.newHashMap();
                var item = (Map<?, ?>) _value;
                item.entrySet().stream().forEach(e -> {
                    var ikey = e.getKey();
                    var ivalue = e.getValue();

                    if (ikey instanceof String && ivalue instanceof String) {
                        try {
                            var svalue = ((String) ivalue).trim();

                            if (svalue.startsWith("@")) {
                                // quote value if starts with @
                                // this allows to use variables as follows
                                // userId: '@user._id' (and not the annoying userId: '"@user._id"')
                                svalue = "\"".concat(svalue).concat("\"");
                            }
                            ret.put((String) ikey, BsonUtils.parse(svalue));
                        } catch (Throwable t) {
                            var ex = new ConfigurationException("Wrong permission: mongo." + key
                                    + " must be an object. A valid example is:\n\toverrideProps:\n\t\tfoo: '\"bar\"'\n\t\tfoo: '{\"bar\": 1}'\n\t\tuser: \"@user._id\"",
                                    t);
                            LambdaUtils.throwsSneakyException(ex);
                        }
                    } else {
                        var ex = new ConfigurationException("Wrong permission: mongo." + key
                                + " must be an object. A valid example is:\n\toverrideProps:\n\t\tfoo: '\"bar\"'\n\t\tfoo: '{\"bar\": 1}'\n\t\tuser: \"@user._id\"");
                        LambdaUtils.throwsSneakyException(ex);
                    }
                });

                return ret;
            } else {
                throw new ConfigurationException("Wrong permission: mongo." + key
                        + " must be an object. A valid example is:\n\toverrideProps:\n\t\tfoo: '\"bar\"'\n\t\tfoo: '{\"bar\": 1}'\n\t\tuser: \"@user._id\"");
            }
        } else {
            return Maps.newHashMap();
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

    private static Set<String> parseSetArg(BsonDocument args, String key) throws ConfigurationException {
        if (args.containsKey(key)) {
            var _value = args.get(key);

            if (_value != null && _value.isArray()) {
                HashSet<String> ret = Sets.newHashSet();
                ;
                var _array = _value.asArray();

                for (var _entry : _array) {
                    if (_entry != null && _entry.isString()) {
                        ret.add(_entry.asString().getValue());
                    } else {
                        throw new ConfigurationException(
                                "Wrong permission: mongo." + key + " must be an array of strings");
                    }
                }
                return ret;
            } else {
                throw new ConfigurationException("Wrong permission: mongo." + key + " must be an array of strings");
            }
        } else {
            return Sets.newHashSet();
        }
    }

    private static Map<String, BsonValue> parseMapArg(BsonDocument args, String key) throws ConfigurationException {
        if (args.containsKey(key)) {
            var _value = args.get(key);

            if (_value.isDocument()) {
                HashMap<String, BsonValue> ret = Maps.newHashMap();
                var doc = _value.asDocument();

                doc.entrySet().stream().forEach(e -> ret.put(e.getKey(), e.getValue()));

                return ret;
            } else {
                throw new ConfigurationException(
                        "Wrong permission: mongo." + key + " must be a JSON object {key1:json, key2:json, ..}");
            }
        } else {
            return Maps.newHashMap();
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

    public Set<String> getHideProps() {
        return this.hideProps;
    }

    public Map<String, BsonValue> getOverrideProps() {
        return this.overrideProps;
    }
}
