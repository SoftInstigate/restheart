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
package org.restheart.security.interceptors.mongo;

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
import org.restheart.exchange.MongoRequest;
import org.restheart.idm.FileRealmAccount;
import org.restheart.idm.MongoRealmAccount;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.undertow.attribute.ExchangeAttributes;

public class MongoPermissionsUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoPermissionsUtils.class);

    /**
     * Interpolate values in doc like '@user', '@user.property', @now
     *
     * Supports accounts handled by MongoRealAuthenticator and FileRealmAuthenticator
     *
     * Legacy variable names %USER, %ROLES and %NOW are supported as well
     *
     * @param request
     * @param doc
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
        } else if (bson.isString()){
            return interpolatePropValue(request, null, bson.asString().getValue());
        } else {
            return bson;
        }
    }

    /**
     * If value is a '@user', '@user.property', @now, returns the interpolated value from
     * authenticated account.
     *
     * Supports accounts handled by MongoRealAuthenticator and FileRealmAuthenticator
     *
     * Legacy variable names %USER, %ROLES and %NOW are supported as well
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
        } else if (value.startsWith("@user.")) {
            if (request.getAuthenticatedAccount() instanceof MongoRealmAccount) {
                var maccount = (MongoRealmAccount) request.getAuthenticatedAccount();
                var accountDoc = maccount.getAccountDocument();
                var prop = value.substring(6);

                LOGGER.debug("account doc: {}", accountDoc.toJson());

                if (prop.contains(".")) {
                    try {
                        JsonElement v = JsonPath.read(accountDoc.toJson(), "$.".concat(prop));

                        return JsonUtils.parse(v.toString());
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

    @SuppressWarnings("unchecked")
    private static BsonValue fromProperties(Map<String, Object> properties, String key) {
        if (key.contains(".")) {
            var first = key.substring(0, key.indexOf("."));
            var last = key.substring(key.indexOf(".")+1);
            var subProperties = properties.get(first);

            if (subProperties != null && subProperties instanceof Map<?,?>) {
                return fromProperties((Map<String,Object>)subProperties, last);
            } else if (subProperties != null && subProperties instanceof List<?>) {
                List<Object> list = (List<Object>) subProperties;

                // we have a list, the next subkey must be a number as in first.0 or first.0.b
                if (last.contains(".")) {
                    var next = last.substring(0, last.indexOf("."));

                    try {
                        var idx = Integer.parseInt(next);
                        var elementAtIdx = list.get(idx);
                        var afterNext = last.substring(last.indexOf(".")+1);

                        if (elementAtIdx instanceof Map<?,?>) {
                            return fromProperties((Map<String,Object>)elementAtIdx, afterNext);
                        } else {
                            LOGGER.warn("Key {} at {} matches an array but selected element is not an object", key, first);
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
        } else if (obj instanceof Map<?,?>) {
            var map = (Map<String,Object>) obj;
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
}