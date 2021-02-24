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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.jayway.jsonpath.JsonPath;

import org.bson.BsonArray;
import org.bson.BsonDateTime;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.restheart.ConfigurationException;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.attribute.ExchangeAttributes;
import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateParser;

/**
 * Helper class that allows to interpolate variables (@user, @request, @now) in
 * permissions
 */
public class AclVarsInterpolator {
    private static final Logger LOGGER = LoggerFactory.getLogger(AclVarsInterpolator.class);

    /**
     * Interpolate values in doc like '@user', '@user.property', @now
     *
     * Supports accounts handled by MongoRealAuthenticator and
     * FileRealmAuthenticator
     *
     * Legacy variable names %USER, %ROLES and %NOW are supported as well
     *
     * @param request
     * @param bson
     * @return bson with interpolated variables
     */
    public static BsonValue interpolateBson(MongoRequest request, BsonValue bson) {
        if (bson.isDocument()) {
            var ret = new BsonDocument();
            var doc = bson.asDocument();
            doc.keySet().stream().forEach(k -> {
                var value = doc.get(k);

                if (value.isString()) {
                    ret.put(k, interpolatePropValue(request, k, doc.get(k).asString().getValue()));
                } else if (value.isDocument()) {
                    ret.put(k, interpolateBson(request, value));
                } else if (value.isArray()) {
                    value.asArray().stream().forEachOrdered(e -> ret.put(k, interpolateBson(request, e)));
                } else {
                    ret.put(k, value);
                }
            });
            return ret;
        } else if (bson.isArray()) {
            var ret = new BsonArray();
            bson.asArray().stream().forEachOrdered(ae -> ret.add(interpolateBson(request, ae)));
            return ret;
        } else if (bson.isString()) {
            return interpolatePropValue(request, null, bson.asString().getValue());
        } else {
            return bson;
        }
    }

    /**
     * If value is a '@user', '@user.property', '@request', '@request.remoteIp',
     * '@mongoPermissions', '@mongoPermissions.readFilter', '@now', returns the
     * interpolated value.
     *
     * For '@user' and '@mongoPermissions' supports accounts handled by
     * MongoRealAuthenticator and FileRealmAuthenticator
     *
     * Legacy variable names %USER, %ROLES and %NOW are supported as well
     *
     * @param request
     * @param key
     * @param value
     * @return
     */
    public static BsonValue interpolatePropValue(MongoRequest request, String key, String value) {
        if (value == null) {
            return BsonNull.VALUE;
        } else if ("%USER".equals(value)) {
            return new BsonString(ExchangeAttributes.remoteUser().readAttribute(request.getExchange()));
        } else if ("%ROLES".equals(value)) {
            if (Objects.nonNull(request.getAuthenticatedAccount())
                    && Objects.nonNull(request.getAuthenticatedAccount().getRoles())) {
                var ret = new BsonArray();
                request.getAuthenticatedAccount().getRoles().stream().map(s -> new BsonString(s))
                        .forEachOrdered(ret::add);
                return ret;
            } else {
                return new BsonArray();
            }
        } else if ("%NOW".equals(value) || "@now".equals(value)) {
            return new BsonDateTime(Instant.now().getEpochSecond() * 1000);
        } else if (value.equals("@user")) {
            if (request.getAuthenticatedAccount() instanceof MongoRealmAccount) {
                var maccount = (MongoRealmAccount) request.getAuthenticatedAccount();
                return maccount.getAccountDocument();
            } else if (request.getAuthenticatedAccount() instanceof FileRealmAccount) {
                var faccount = (FileRealmAccount) request.getAuthenticatedAccount();
                return toBson(faccount.getAccountProperties());
            } else {
                return BsonNull.VALUE;
            }
        } else if (value.equals("@request")) {
            return getRequestObject(request);
        } else if (value.startsWith("@request.") && value.length() > 8) {
            var requestObject = getRequestObject(request);
            var prop = value.substring(9);

            LOGGER.debug("request doc: {}", requestObject.toJson());

            if (prop.contains(".")) {
                try {
                    var v = JsonPath.read(requestObject.toJson(), "$.".concat(prop));

                    return BsonUtils.parse(v.toString());
                } catch (Throwable pnfe) {
                    return BsonNull.VALUE;
                }
            } else {
                if (requestObject.containsKey(prop)) {
                    return requestObject.get(prop);
                } else {
                    return BsonNull.VALUE;
                }
            }
        } else if (value.equals("@mongoPermissions")) {
            if (MongoPermissions.of(request) != null) {
                return MongoPermissions.of(request).asBson();
            } else {
                return BsonNull.VALUE;
            }
        } else if (value.startsWith("@mongoPermissions.") && value.length() > 17) {
            if (MongoPermissions.of(request) != null) {
                var doc = MongoPermissions.of(request).asBson();
                var prop = value.substring(18);

                LOGGER.debug("permission doc: {}", doc);

                if (prop.contains(".")) {
                    try {
                        JsonElement v = JsonPath.read(doc.toJson(), "$.".concat(prop));

                        return BsonUtils.parse(v.toString());
                    } catch (Throwable pnfe) {
                        return BsonNull.VALUE;
                    }
                } else {
                    if (doc.containsKey(prop)) {
                        return doc.get(prop);
                    } else {
                        return BsonNull.VALUE;
                    }
                }
            } else {
                return BsonNull.VALUE;
            }
        } else if (value.startsWith("@user.") && value.length() > 5) {
            if (request.getAuthenticatedAccount() instanceof MongoRealmAccount) {
                var maccount = (MongoRealmAccount) request.getAuthenticatedAccount();
                var accountDoc = maccount.getAccountDocument();
                var prop = value.substring(6);

                LOGGER.debug("account doc: {}", accountDoc.toJson());

                if (prop.contains(".")) {
                    try {
                        JsonElement v = JsonPath.read(accountDoc.toJson(), "$.".concat(prop));

                        return BsonUtils.parse(v.toString());
                    } catch (Throwable pnfe) {
                        return BsonNull.VALUE;
                    }
                } else {
                    if (accountDoc.containsKey(prop)) {
                        return accountDoc.get(prop);
                    } else {
                        return BsonNull.VALUE;
                    }
                }
            } else if (request.getAuthenticatedAccount() instanceof FileRealmAccount) {
                var faccount = (FileRealmAccount) request.getAuthenticatedAccount();

                return fromProperties(faccount.getAccountProperties(), value.substring(6));
            } else {
                return BsonNull.VALUE;
            }
        } else {
            return new BsonString(value);
        }
    }

    /**
     * interpolate the permission predicate substituting @user.x variables
     *
     * @param predicate the predicate containing the placeholder valiable to
     *                  interpolate
     * @param request   the request
     * @return the interpolated predicate
     */
    public static Predicate interpolatePredicate(Request<?> request, String predicate) throws ConfigurationException {
        var a = getAccountDocument(request);

        try {
            if (a == null || a.isEmpty()) {
                return PredicateParser.parse(predicate, AclVarsInterpolator.class.getClassLoader());
            } else {
                var interpolatedPredicate = interpolatePredicate(predicate, "@user.", a);
                return PredicateParser.parse(interpolatedPredicate, AclVarsInterpolator.class.getClassLoader());
            }
        } catch(Throwable t) {
            throw new ConfigurationException("Wrong permission: invalid predicate " + predicate, t);
        }
    }

    private static BsonDocument getAccountDocument(Request<?> request) {
        if (request.getAuthenticatedAccount() instanceof MongoRealmAccount) {
            return ((MongoRealmAccount) request.getAuthenticatedAccount()).getAccountDocument();
        } else if (request.getAuthenticatedAccount() instanceof FileRealmAccount) {
            return toBson(((FileRealmAccount) request.getAuthenticatedAccount()).getAccountProperties()).asDocument();
        } else {
            return null;
        }
    }

    /**
     * interpolate the permission predicate substituting variables with values found
     * in variableValues
     *
     * @param predicate      the predicate containing the placeholder valiable to
     *                       interpolate
     * @param prefix         the variable prefix, eg. '@user.' or '@request.'
     * @param variableValues the document that specifies the values of the variables
     * @return
     */
    static String interpolatePredicate(String predicate, String prefix, BsonDocument variableValues) {
        if (variableValues == null || variableValues.isEmpty()) {
            return predicate;
        }

        var flatten = BsonUtils.flatten(variableValues, true);

        String[] ret = { predicate };

        flatten.keySet().stream().filter(key -> flatten.get(key) != null)
                .filter(key -> isJsonPrimitive(flatten.get(key))).forEach(key -> ret[0] = ret[0]
                        .replaceAll(prefix.concat(key), quote(jsonPrimitiveValue(flatten.get(key)))));

        return ret[0];
    }

    private static boolean isJsonPrimitive(BsonValue value) {
        return value.isNull() || value.isBoolean() || value.isNumber() || value.isString() || value.isObjectId()
                || value.isTimestamp() || value.isDateTime();
    }

    private static String jsonPrimitiveValue(BsonValue value) {
        switch (value.getBsonType()) {
            case NULL:
                return "null";
            case BOOLEAN:
                return value.asBoolean().toString();
            case INT32:
                return "" + value.asInt32().getValue();
            case INT64:
                return "" + value.asInt64().getValue();
            case DOUBLE:
                return "" + value.asDouble().getValue();
            case STRING:
                return value.asString().getValue();
            case OBJECT_ID:
                return value.asObjectId().getValue().toHexString();
            case DATE_TIME:
                return "" + value.asDateTime().getValue();
            case TIMESTAMP:
                return "" + value.asTimestamp().getValue();
            default:
                throw new IllegalArgumentException("Cannot use in predicate field of type " + value.getBsonType());
        }
    }

    private static String quote(String s) {
        return "\"".concat(s).concat("\"");
    }

    @SuppressWarnings("unchecked")
    private static BsonValue fromProperties(Map<String, Object> properties, String key) {
        if (key.contains(".")) {
            var first = key.substring(0, key.indexOf("."));
            var last = key.substring(key.indexOf(".") + 1);
            var subProperties = properties.get(first);

            if (subProperties != null && subProperties instanceof Map<?, ?>) {
                return fromProperties((Map<String, Object>) subProperties, last);
            } else if (subProperties != null && subProperties instanceof List<?>) {
                List<Object> list = (List<Object>) subProperties;

                // we have a list, the next subkey must be a number as in first.0 or first.0.b
                if (last.contains(".")) {
                    var next = last.substring(0, last.indexOf("."));

                    try {
                        var idx = Integer.parseInt(next);
                        var elementAtIdx = list.get(idx);
                        var afterNext = last.substring(last.indexOf(".") + 1);

                        if (elementAtIdx instanceof Map<?, ?>) {
                            return fromProperties((Map<String, Object>) elementAtIdx, afterNext);
                        } else {
                            LOGGER.warn("Key {} at {} matches an array but selected element is not an object", key,
                                    first);
                            return BsonNull.VALUE;
                        }
                    } catch (Throwable t) {
                        LOGGER.warn("Key {} at {} matches an array but following part is not a number", key, first);
                        return BsonNull.VALUE;
                    }
                } else {
                    try {
                        var idx = Integer.parseInt(last);
                        return toBson(list.get(idx));
                    } catch (Throwable t) {
                        LOGGER.error("Key {} at {} matches an array but following part is not a number", key, first);
                        return BsonNull.VALUE;
                    }
                }
            } else {
                return BsonNull.VALUE;
            }
        } else {
            if (properties.containsKey(key)) {
                return toBson(properties.get(key));
            } else {
                return BsonNull.VALUE;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static BsonValue toBson(Object obj) {
        if (obj instanceof String) {
            return new BsonString((String) obj);
        } else if (obj instanceof Map<?, ?>) {
            var map = (Map<String, Object>) obj;
            var ret = new BsonDocument();
            map.entrySet().stream().forEachOrdered(e -> ret.put(e.getKey(), toBson(e.getValue())));
            return ret;
        } else if (obj instanceof List<?>) {
            var list = (List<Object>) obj;
            var ret = new BsonArray();
            list.stream().forEachOrdered(e -> ret.add(toBson(e)));
            return ret;
        } else if (obj instanceof Integer) {
            return new BsonInt32((Integer) obj);
        } else if (obj instanceof Long) {
            return new BsonInt64((Long) obj);
        } else if (obj instanceof Double) {
            return new BsonDouble((Double) obj);
        } else {
            LOGGER.warn("ovverridendProp value not suppored {}", obj);
            return BsonNull.VALUE;
        }
    }

    private static BsonDocument getRequestObject(final MongoRequest request) {
        var exchange = request.getExchange();

        var properties = new BsonDocument();

        // the name of the db
        properties.put("db", request.getDBName() == null ? BsonNull.VALUE : new BsonString(request.getDBName()));

        // the name of the collection
        properties.put("collection", request.getDBName() == null ? BsonNull.VALUE : new BsonString(request.getCollectionName()));

        // the _id of the document
        properties.put("_id", request.getDocumentId() == null ? BsonNull.VALUE : request.getDocumentId());

        // the TYPE of the resource:
        // - INVALID, ROOT, ROOT_SIZE, DB, DB_SIZE, DB_META, CHANGE_STREAM, COLLECTION,
        // - COLLECTION_SIZE, COLLECTION_META, DOCUMENT, COLLECTION_INDEXES, INDEX,
        // - FILES_BUCKET, FILES_BUCKET_SIZE, FILES_BUCKET_META, FILE, FILE_BINARY,
        // - AGGREGATION, SCHEMA, SCHEMA_STORE, SCHEMA_STORE_SIZE, SCHEMA_STORE_META,
        // - BULK_DOCUMENTS, METRICS, SESSION, SESSIONS, TRANSACTIONS, TRANSACTION
        properties.put("resourceType", new BsonString(request.getType().name()));

        var _userName = ExchangeAttributes.remoteUser().readAttribute(exchange);

        var userName = _userName != null ? new BsonString(_userName) : BsonNull.VALUE;

        // remote user
        properties.put("userName", userName);

        // dateTime
        properties.put("epochTimeStamp", new BsonDateTime(Instant.now().getEpochSecond() * 1000));

        // dateTime
        properties.put("dateTime", new BsonString(ExchangeAttributes.dateTime().readAttribute(exchange)));

        // local ip
        properties.put("localIp", new BsonString(ExchangeAttributes.localIp().readAttribute(exchange)));

        // local port
        properties.put("localPort", new BsonString(ExchangeAttributes.localPort().readAttribute(exchange)));

        // local server name
        properties.put("localServerName", new BsonString(ExchangeAttributes.localServerName().readAttribute(exchange)));

        // request query string
        properties.put("queryString", new BsonString(ExchangeAttributes.queryString().readAttribute(exchange)));

        // request relative path
        properties.put("relativePath", new BsonString(ExchangeAttributes.relativePath().readAttribute(exchange)));

        // remote ip
        properties.put("remoteIp", new BsonString(ExchangeAttributes.remoteIp().readAttribute(exchange)));

        // request method
        properties.put("method", new BsonString(ExchangeAttributes.requestMethod().readAttribute(exchange)));

        // request protocol
        properties.put("protocol", new BsonString(ExchangeAttributes.requestProtocol().readAttribute(exchange)));

        return properties;
    }
}