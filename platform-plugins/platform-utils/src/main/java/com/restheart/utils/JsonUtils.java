/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.restheart.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.stream.StreamSupport;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonUtils {
    private static final String ESCAPED_DOLLAR = "_$";
    private static final String ESCAPED_DOT = "::";
    private static final String DOLLAR = "$";

    /**
     * replaces the dollar prefixed keys (eg $exists) with the corresponding
     * underscore prefixed key (eg _$exists). Also replaces dots if escapeDots
     * is true. This is needed because MongoDB does not allow to store keys that
     * starts with $ and that contains dots.
     *
     * @param json
     * @param escapeDots
     * @return the json object where the underscore prefixed keys are replaced
     * with the corresponding keys
     */
    public static JsonElement escapeKeys(JsonElement json, boolean escapeDots) {
        if (json == null || json.isJsonNull()) {
            return JsonNull.INSTANCE;
        }
        
        if (json.isJsonObject()) {
            var ret = new JsonObject();

            json.getAsJsonObject().keySet().stream().forEach(k -> {
                String newKey = k.startsWith(DOLLAR) ? "_" + k : k;

                if (escapeDots) {
                    newKey = newKey.replaceAll("\\.", ESCAPED_DOT);
                }

                var value = json.getAsJsonObject().get(k);

                if (value.isJsonObject()) {
                    ret.add(newKey, escapeKeys(value, escapeDots));
                } else if (value.isJsonArray()) {
                    var newList = new JsonArray();

                    StreamSupport.stream(value.getAsJsonArray().spliterator(),
                            true)
                            .forEach(v -> {
                                newList.add(escapeKeys(v, escapeDots));
                            });

                    ret.add(newKey, newList);
                } else {
                    ret.add(newKey, value);
                }

            });

            return ret;
        } else if (json.isJsonArray()) {
            var ret = new JsonArray();

            StreamSupport.stream(json.getAsJsonArray().spliterator(), true)
                    .forEach(value -> {
                        if (value.isJsonObject()) {
                            ret.add(escapeKeys(value, escapeDots));
                        } else if (value.isJsonArray()) {
                            var newList = new JsonArray();

                            StreamSupport.stream(value.getAsJsonArray()
                                    .spliterator(), true)
                                    .forEach(v -> {
                                        newList.add(escapeKeys(v, escapeDots));
                                    });

                            ret.add(newList);
                        } else {
                            ret.add(value);
                        }

                    });

            return ret;
        } else if (json.isJsonPrimitive()
                && json.getAsJsonPrimitive().isString()) {
            return json.getAsJsonPrimitive().getAsString().startsWith(DOLLAR)
                    ? new JsonPrimitive("_"
                            + json.getAsJsonPrimitive().getAsString())
                    : json;
        } else {
            return json;
        }
    }

    /**
     * replaces the underscore prefixed keys (eg _$exists) with the
     * corresponding key (eg $exists) and the dot (.) in property names. This is
     * needed because MongoDB does not allow to store keys that starts with $
     * and with dots in it
     *
     * @see
     * https://docs.mongodb.org/manual/reference/limits/#Restrictions-on-Field-Names
     *
     * @param json
     * @return the json object where the underscore prefixed keys are replaced
     * with the corresponding keys
     */
    public static JsonElement unescapeKeys(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return JsonNull.INSTANCE;
        }

        if (json.isJsonObject()) {
            var ret = new JsonObject();

            json.getAsJsonObject().keySet().stream().forEach(k -> {
                String newKey = k.startsWith(ESCAPED_DOLLAR)
                        ? k.substring(1)
                        : k;
                newKey = newKey.replaceAll(ESCAPED_DOT, ".");

                var value = json.getAsJsonObject().get(k);

                if (value.isJsonObject()) {
                    ret.add(newKey, unescapeKeys(value));
                } else if (value.isJsonArray()) {
                    var newList = new JsonArray();

                    StreamSupport.stream(value.getAsJsonArray()
                            .spliterator(), true)
                            .forEach(v -> {
                                newList.add(unescapeKeys(v));
                            });

                    ret.add(newKey, newList);
                } else {
                    ret.add(newKey, unescapeKeys(value));
                }
            });

            return ret;
        } else if (json.isJsonArray()) {
            var ret = new JsonArray();

            StreamSupport.stream(json.getAsJsonArray().spliterator(), true)
                    .forEach(value -> {
                        if (value.isJsonObject()) {
                            ret.add(unescapeKeys(value));
                        } else if (value.isJsonArray()) {
                            var newList = new JsonArray();

                            StreamSupport.stream(value.getAsJsonArray()
                                    .spliterator(), true)
                                    .forEach(v -> {
                                        newList.add(unescapeKeys(v));
                                    });

                            ret.add(newList);
                        } else {
                            ret.add(unescapeKeys(value));
                        }

                    });

            return ret;
        } else if (json.isJsonPrimitive()
                && json.getAsJsonPrimitive().isString()) {
            return json.getAsJsonPrimitive().getAsString()
                    .startsWith(ESCAPED_DOLLAR)
                    ? new JsonPrimitive(json.getAsJsonPrimitive()
                            .getAsString().substring(1))
                    : json;
        } else {
            return json;
        }
    }
}
