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

package org.restheart.security.authorizers;

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
import org.restheart.utils.JsonUtils;
import org.restheart.utils.LambdaUtils;

/**
 * Permissions specific for MongoService
 */
public class MongoPermissions {
    final boolean whitelistManagementRequests;
    final boolean whitelistBulkPatch;
    final boolean whitelistBulkDelete;
    final boolean allowAllWriteModes;
    private final BsonDocument readFilter;
    private final BsonDocument writeFilter;

    final Set<String> hiddenProps = Sets.newHashSet();
    final Set<String> protectedProps = Sets.newHashSet();
    final Map<String, BsonValue> overriddenProps = Maps.newHashMap();

    public MongoPermissions() {
        this.whitelistManagementRequests = false;
        this.whitelistBulkPatch = false;
        this.whitelistBulkDelete = false;
        this.allowAllWriteModes = false;
        this.readFilter = null;
        this.writeFilter = null;
    }

    MongoPermissions(BsonDocument readFilter, BsonDocument writeFilter, boolean whitelistManagementRequests,
            boolean whitelistBulkPatch, boolean whitelistBulkDelete, boolean allowAllWriteModes,
            Set<String> hiddenProps, Set<String> protectedProps, Map<String, BsonValue> overriddenProps) {
        this.readFilter = readFilter == null ? null
                : readFilter.isNull() ? null : JsonUtils.escapeKeys(readFilter.asDocument(), true).asDocument();

        this.writeFilter = writeFilter == null ? null
                : writeFilter.isNull() ? null : JsonUtils.escapeKeys(writeFilter.asDocument(), true).asDocument();

        this.whitelistManagementRequests = whitelistManagementRequests;
        this.whitelistBulkPatch = whitelistBulkPatch;
        this.whitelistBulkDelete = whitelistBulkDelete;
        this.allowAllWriteModes = allowAllWriteModes;
        if (hiddenProps != null) {
            this.hiddenProps.addAll(hiddenProps);
        }

        if (protectedProps != null) {
            this.protectedProps.addAll(protectedProps);
        }

        if (overriddenProps != null) {
            this.overriddenProps.putAll(overriddenProps);
        }
    }

    public static MongoPermissions from(BsonDocument args) throws ConfigurationException {
        if (args == null || args.isEmpty()) {
            // return default values
            return new MongoPermissions();
        } else {
            var _readFilter = args.get("readFilter");

            if (!(_readFilter == null || _readFilter.isNull()) && !_readFilter.isDocument()) {
                throw new ConfigurationException("Wrong permission: readFilter must be a JSON object or null");
            }

            var readFilter = _readFilter == null ? null
                    : _readFilter.isNull() ? null : JsonUtils.escapeKeys(_readFilter.asDocument(), true).asDocument();

            var _writeFilter = args.get("writeFilter");

            if (!(_writeFilter == null || _writeFilter.isNull()) && !_writeFilter.isDocument()) {
                throw new ConfigurationException("Wrong permission: writeFilter must be a JSON object or null");
            }

            var writeFilter = _writeFilter == null ? null
                    : _writeFilter.isNull() ? null : JsonUtils.escapeKeys(_writeFilter.asDocument(), true).asDocument();

            return new MongoPermissions(readFilter, writeFilter, parseBooleanArg(args, "whitelistManagementRequests"),
                    parseBooleanArg(args, "whitelistBulkPatch"), parseBooleanArg(args, "whitelistBulkDelete"),
                    parseBooleanArg(args, "allowAllWriteModes"), parseSetArg(args, "hiddenProps"),
                    parseSetArg(args, "protectedProps"), parseMapArg(args, "overriddenProps"));
        }
    }

    public static MongoPermissions from(Map<String, Object> args) throws ConfigurationException {
        if (args == null || args.isEmpty()) {
            // return default values
            return new MongoPermissions();
        } else {
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
                                    : JsonUtils.escapeKeys(_readFilter.asDocument(), true).asDocument();
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
                                    : JsonUtils.escapeKeys(_writeFilter.asDocument(), true).asDocument();
                } catch (ClassCastException | JsonParseException jpe) {
                    throw new ConfigurationException(
                            "Wrong permission: the writeFilter is not a string containing a JSON Object", jpe);
                }
            } else {
                writeFilter = null;
            }

            return new MongoPermissions(readFilter, writeFilter, parseBooleanArg(args, "whitelistManagementRequests"),
                    parseBooleanArg(args, "whitelistBulkPatch"), parseBooleanArg(args, "whitelistBulkDelete"),
                    parseBooleanArg(args, "allowAllWriteModes"), parseSetArg(args, "hiddenProps"),
                    parseSetArg(args, "protectedProps"), parseMapArg(args, "overriddenProps"));
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
                        throw new ConfigurationException(
                                "Wrong permission: mongo." + key + " must be a list of strings");
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
                            ret.put((String) ikey, JsonUtils.parse(svalue));
                        } catch (Throwable t) {
                            var ex = new ConfigurationException("Wrong permission: mongo." + key
                                    + " must be an object. A valid example is:\n\toverriddenProps:\n\t\tfoo: '\"bar\"'\n\t\tfoo: '{\"bar\": 1}'\n\t\tuser: \"@user._id\"",
                                    t);
                            LambdaUtils.throwsSneakyException(ex);
                        }
                    } else {
                        var ex = new ConfigurationException("Wrong permission: mongo." + key
                                + " must be an object. A valid example is:\n\toverriddenProps:\n\t\tfoo: '\"bar\"'\n\t\tfoo: '{\"bar\": 1}'\n\t\tuser: \"@user._id\"");
                        LambdaUtils.throwsSneakyException(ex);
                    }
                });

                return ret;
            } else {
                throw new ConfigurationException("Wrong permission: mongo." + key
                        + " must be an object. A valid example is:\n\toverriddenProps:\n\t\tfoo: '\"bar\"'\n\t\tfoo: '{\"bar\": 1}'\n\t\tuser: \"@user._id\"");
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
        return readFilter == null || readFilter.isNull() ? null : JsonUtils.unescapeKeys(readFilter).asDocument();
    }

    /**
     * @return the writeFilter
     */
    public BsonDocument getWriteFilter() {
        return writeFilter == null || writeFilter.isNull() ? writeFilter
                : JsonUtils.unescapeKeys(writeFilter).asDocument();
    }

    public boolean getWhitelistManagementRequests() {
        return this.whitelistManagementRequests;
    }

    public boolean isWhitelistManagementRequests() {
        return this.whitelistManagementRequests;
    }

    public boolean getWhitelistBulkPatch() {
        return this.whitelistBulkPatch;
    }

    public boolean isWhitelistBulkPatch() {
        return this.whitelistBulkPatch;
    }

    public boolean getWhitelistBulkDelete() {
        return this.whitelistBulkDelete;
    }

    public boolean isWhitelistBulkDelete() {
        return this.whitelistBulkDelete;
    }

    public boolean getAllowAllWriteModes() {
        return this.allowAllWriteModes;
    }

    public boolean isAllowAllWriteModes() {
        return this.allowAllWriteModes;
    }

    public Set<String> getHiddenProps() {
        return this.hiddenProps;
    }

    public Set<String> getProtectedProps() {
        return this.protectedProps;
    }

    public Map<String, BsonValue> getOverriddenProps() {
        return this.overriddenProps;
    }
}
