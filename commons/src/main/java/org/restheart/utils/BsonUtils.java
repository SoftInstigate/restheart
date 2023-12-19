/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2023 SoftInstigate
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

package org.restheart.utils;

import com.google.common.collect.Sets;
import com.mongodb.MongoClientSettings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.jxpath.JXPathContext;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDecimal128;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonInvalidOperationException;
import org.bson.BsonJavaScript;
import org.bson.BsonMaxKey;
import org.bson.BsonMinKey;
import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.codecs.BsonArrayCodec;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.json.JsonMode;
import org.bson.json.JsonParseException;
import org.bson.json.JsonReader;
import org.bson.json.JsonWriterSettings;
import org.bson.json.StrictJsonWriter;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class BsonUtils {

    static final Logger LOGGER = LoggerFactory.getLogger(BsonUtils.class);

    private static final BsonArrayCodec BSON_ARRAY_CODEC = new BsonArrayCodec(CodecRegistries.fromProviders(new BsonValueCodecProvider()));

    private static final String ESCAPED_DOLLAR = "_$";
    private static final String ESCAPED_DOT = "::";
    private static final String DOLLAR = "$";

    /**
     * replaces the underscore prefixed keys (eg _$exists) with the
     * corresponding key (eg $exists) and the dot (.) in property names. This is
     * needed because MongoDB does not allow to store keys that starts with $
     * and with dots in it
     *
     * See
     * https://docs.mongodb.org/manual/reference/limits/#Restrictions-on-Field-Names
     *
     * @param json
     * @return the json object where the underscore prefixed keys are replaced
     * with the corresponding keys
     */
    public static BsonValue unescapeKeys(BsonValue json) {
        if (json == null) {
            return null;
        }

        if (json.isDocument()) {
            var ret = new BsonDocument();

            json.asDocument().keySet().stream().forEach(k -> {
                var newKey = k.startsWith(ESCAPED_DOLLAR) ? k.substring(1) : k;
                newKey = newKey.replaceAll(ESCAPED_DOT, ".");

                var value = json.asDocument().get(k);

                if (value.isDocument()) {
                    ret.put(newKey, unescapeKeys(value));
                } else if (value.isArray()) {
                    var newList = new BsonArray();

                    value.asArray().stream().forEach(v -> newList.add(unescapeKeys(v)));

                    ret.put(newKey, newList);
                } else {
                    ret.put(newKey, unescapeKeys(value));
                }

            });

            return ret;
        } else if (json.isArray()) {
            var ret = new BsonArray();

            json.asArray().stream().forEach(value -> {
                if (value.isDocument()) {
                    ret.add(unescapeKeys(value));
                } else if (value.isArray()) {
                    var newList = new BsonArray();
                    value.asArray().stream().forEach(v -> newList.add(unescapeKeys(v)));
                    ret.add(newList);
                } else {
                    ret.add(unescapeKeys(value));
                }

            });

            return ret;
        } else if (json.isString()) {
            return json.asString().getValue().startsWith(ESCAPED_DOLLAR) ? new BsonString(json.asString().getValue().substring(1)) : json;
        } else {
            return json;
        }
    }

    /**
     * replaces the dollar prefixed keys (eg $exists) with the corresponding
     * underscore prefixed key (eg _$exists). Also replaces dots if escapeDots
     * is true. This is needed because MongoDB does not allow to store keys that
     * starts with $ and that contains dots.
     *
     * @param json
     * @param escapeDots
     * @return the json object where the keys are escaped
     */
    public static BsonValue escapeKeys(BsonValue json, boolean escapeDots) {
        return escapeKeys(json, escapeDots, false);
    }

    /**
     * replaces the dollar prefixed keys (eg $exists) with the corresponding
     * underscore prefixed key (eg _$exists). Also replaces dots if escapeDots
     * is true. This is needed because MongoDB does not allow to store keys that
     * starts with $ and that contains dots. Root level keys containing dots can be
     * escluded from escaping (dontEscapeDotsInRootKeys=true) to allow using the
     * dot notation to refer to nested keys but still escaping nested keys.
     * In the following example the root level key is used to refer to a sub document property,
     * while the nested key with dots must be escaped because it is an aggregation stage:
     * PATCH { "mappings.Query.TheatersByCity.find": {"location.address.city": { "$arg": "city"} } }
     *
     * @param json
     * @param escapeDots
     * @param dontEscapeDotsInRootKeys specify if dots in root level keys should not be escaped when escapeDots=true. root level
     * @return the json object where the keys are escaped
     * with the corresponding keys
     */
    public static BsonValue escapeKeys(BsonValue json, boolean escapeDots, boolean dontEscapeDotsInRootKeys) {
        if (json == null) {
            return null;
        }

        if (json.isDocument()) {
            var ret = new BsonDocument();

            boolean root[] = { true };

            json.asDocument().keySet().stream().forEach(k -> {
                var newKey = k.startsWith(DOLLAR) ? "_" + k : k;

                if (escapeDots && !(dontEscapeDotsInRootKeys && root[0])) {
                    newKey = newKey.replaceAll("\\.", ESCAPED_DOT);
                }

                root[0] = false;

                var value = json.asDocument().get(k);

                if (value.isDocument()) {
                    ret.put(newKey, escapeKeys(value, escapeDots, false));
                } else if (value.isArray()) {
                    var newList = new BsonArray();
                    value.asArray().stream().forEach(v -> newList.add(escapeKeys(v, escapeDots, false)));
                    ret.put(newKey, newList);
                } else {
                    ret.put(newKey, value);
                }

            });

            return ret;
        } else if (json.isArray()) {
            var ret = array();

            json.asArray().stream().forEach(value -> {
                if (value.isDocument()) {
                    ret.add(escapeKeys(value, escapeDots, dontEscapeDotsInRootKeys));
                } else if (value.isArray()) {
                    var newList = new BsonArray();
                    value.asArray().stream().forEach(v -> newList.add(escapeKeys(v, escapeDots, false)));
                    ret.add(newList);
                } else {
                    ret.add(value);
                }
            });

            return ret.get();
        } else if (json.isString()) {
            return json.asString().getValue().startsWith(DOLLAR) ? new BsonString("_" + json.asString().getValue()) : json;
        } else {
            return json;
        }
    }

    /**
     *
     * @param root the Bson to extract properties from
     * @param path the path of the properties to extract
     * @return the List of Optional&lt;Object&gt;s extracted from root ojbect
     * and identified by the path or null if path does not exist
     *
     *
     */
    public static List<Optional<BsonValue>> getPropsFromPath(BsonValue root, String path) throws IllegalArgumentException {
        String pathTokens[] = path.split(Pattern.quote("."));

        if (pathTokens == null || pathTokens.length == 0 || !pathTokens[0].equals(DOLLAR)) {
            throw new IllegalArgumentException("wrong path. it must use the . notation and start with $");
        } else if (!(root instanceof BsonDocument)) {
            throw new IllegalArgumentException("wrong json. it must be an object");
        } else {
            return _getPropsFromPath(root, pathTokens, pathTokens.length);
        }
    }

    /**
     *
     * @param doc
     * @param path the path of the field, can use the dot notation
     * @return
     */
    public static Optional<BsonValue> get(BsonDocument doc, String path) {
        final String xpath = dotNotationToXPath(path);

        if (path == null) {
            return Optional.empty();
        } else if (doc.containsKey(path)) {
            return Optional.of(doc.get(path));
        }

        var ctx = JXPathContext.newContext(doc);

        try {
            return Optional.of((BsonValue) ctx.getValue(xpath));
        } catch(Throwable t) {
            return Optional.empty();
        }
    }

    /**
     * converts the dot notation syntax to xpath
     * a.b.c -> /a/b/c
     * a[1].c -> /a[2]/c (xpath indexes are 1-based!)
     * @param xpath
     * @return
     */
    private static String dotNotationToXPath(String dn) {
        if (dn == null) {
            return null;
        }

        // replace all dots (.) with / excluding dots inside brachetes as in ['a.b']
        dn = dn.replaceAll("\\.(?!.*'\\])", "/");

        // replace all [' and '] with /
        dn = dn.replaceAll("\\['", "/");
        dn = dn.replaceAll("'\\]", "/");
        // remove tailing /
        if (dn.endsWith("/")) {
            dn = dn.substring(0, dn.length() - 1);
        }

        // replace all array indexes [n] with[n+1]
        dn = Pattern.compile("\\[(?<idx>\\d*)\\]").matcher(dn)
            .replaceAll(mr ->inc(mr.group()));

        // // add leading /
        return "/".concat(dn);
    }

    private static String inc(String n) {
        var _n = n.replaceAll("\\[", "").replaceAll("\\]", "");
        try {
            return "[" + (Integer.parseInt(_n) + 1) + "]";
        } catch(NumberFormatException nfe) {
            return n;
        }
    }

    private static List<Optional<BsonValue>> _getPropsFromPath(BsonValue json, String[] pathTokens, int totalTokensLength) throws IllegalArgumentException {
        if (pathTokens == null) {
            throw new IllegalArgumentException("pathTokens argument cannot be null");
        }

        String pathToken;

        if (pathTokens.length > 0) {
            if (json == null) {
                return null;
            } else {
                pathToken = pathTokens[0];

                if ("".equals(pathToken)) {
                    throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " path tokens cannot be empty strings");
                }
            }
        } else if (json.isNull()) {
            // if value is null return an empty optional
            var ret = new ArrayList<Optional<BsonValue>>();
            ret.add(Optional.empty());
            return ret;
        } else {
            var ret = new ArrayList<Optional<BsonValue>>();
            ret.add(Optional.ofNullable(json));
            return ret;
        }

        List<Optional<BsonValue>> nested;

        switch (pathToken) {
            case DOLLAR -> {
                if (!(json.isDocument())) {
                    throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " at token " + pathToken + "; it should be an object but found " + json.toString());
                }

                if (pathTokens.length != totalTokensLength) {
                    throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " at token " + pathToken + "; $ can only start the expression");
                }

                return _getPropsFromPath(json, subpath(pathTokens), totalTokensLength);
            }
            case "*" -> {
                if (!(json.isDocument())) {
                    return null;
                } else {
                    var ret = new ArrayList<Optional<BsonValue>>();

                    for (String key : json.asDocument().keySet()) {
                        nested = _getPropsFromPath(json.asDocument().get(key), subpath(pathTokens), totalTokensLength);

                        // only add null if subpath(pathTokens) was the last token
                        if (nested == null && pathTokens.length == 2) {
                            ret.add(null);
                        } else if (nested != null) {
                            ret.addAll(nested);
                        }
                    }

                    return ret;
                }
            }
            case "[*]" -> {
                if (!(json.isArray())) {
                    if (json.isDocument()) {
                        // this might be the case of PATCHING an element array using the dot notation
                        // e.g. object.array.2
                        // if so, the array comes as an BsonDocument with all numberic keys
                        // in any case, it might also be the object { "object": { "array": {"2": xxx }}}

                        var allNumbericKeys = json.asDocument().keySet()
                                .stream().allMatch(k -> {
                                    try {
                                        Integer.valueOf(k);
                                        return true;
                                    } catch (NumberFormatException nfe) {
                                        return false;
                                    }
                                });

                        if (allNumbericKeys) {
                            var ret = new ArrayList<Optional<BsonValue>>();

                            for (var key : json.asDocument().keySet()) {
                                nested = _getPropsFromPath(json.asDocument().get(key), subpath(pathTokens), totalTokensLength);

                                // only add null if subpath(pathTokens) was the last token
                                if (nested == null && pathTokens.length == 2) {
                                    ret.add(null);
                                } else if (nested != null) {
                                    ret.addAll(nested);
                                }
                            }

                            return ret;
                        }
                    }

                    return null;
                } else {
                    var ret = new ArrayList<Optional<BsonValue>>();

                    if (!json.asArray().isEmpty()) {
                        for (var index = 0; index < json.asArray().size(); index++) {
                            nested = _getPropsFromPath(json.asArray().get(index), subpath(pathTokens), totalTokensLength);

                            // only add null if subpath(pathTokens) was the last token
                            if (nested == null && pathTokens.length == 2) {
                                ret.add(null);
                            } else if (nested != null) {
                                ret.addAll(nested);
                            }
                        }
                    }

                    return ret;
                }
            }
            default -> {
                if (json.isArray()) {
                    throw new IllegalArgumentException("wrong path " + pathFromTokens(pathTokens) + " at token " + pathToken + "; it should be '[*]'");
                } else if (json.isDocument()) {
                    if (json.asDocument().containsKey(pathToken)) {
                        return _getPropsFromPath(json.asDocument().get(pathToken), subpath(pathTokens), totalTokensLength);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
    }

    /**
     *
     * @param left the json path expression
     * @param right the json path expression
     *
     * @return true if the left json path is an acestor of the right path, i.e.
     * left path selects a values set that includes the one selected by the
     * right path
     *
     * {@literal examples: ($, $.a) -> true, ($.a, $.b) -> false, ($.*, $.a) -> true,
     * ($.a.[*].c, $.a.0.c) -> true, ($.a.[*], $.a.b) -> false }
     *
     */
    public static boolean isAncestorPath(final String left, final String right) {
        if (left == null || !left.startsWith(DOLLAR)) {
            throw new IllegalArgumentException("wrong left path: " + left);
        }
        if (right == null || !right.startsWith(DOLLAR)) {
            throw new IllegalArgumentException("wrong right path: " + right);
        }

        var ret = true;

        if (!right.startsWith(left)) {
            String leftPathTokens[] = left.split(Pattern.quote("."));
            String rightPathTokens[] = right.split(Pattern.quote("."));

            if (leftPathTokens.length > rightPathTokens.length) {
                ret = false;
            } else {
                outerloop:
                for (int cont = 0; cont < leftPathTokens.length; cont++) {
                    var lt = leftPathTokens[cont];
                    var rt = rightPathTokens[cont];

                    switch (lt) {
                        case "*" -> {
                        }
                        case "[*]" -> {
                            try {
                                Integer.valueOf(rt);
                            }catch (NumberFormatException nfe) {
                                ret = false;
                                break outerloop;
                            }
                        }
                        default -> {
                            ret = rt.equals(lt);
                            if (!ret) {
                                break outerloop;
                            } else {
                            }
                        }
                    }
                }
            }
        }

        LOGGER.trace("isAncestorPath: {} -> {} -> {}", left, right, ret);
        return ret;
    }

    /**
     * @param root
     * @param path
     * @return then number of properties identitified by the json path
     * expression or null if path does not exist
     * @throws IllegalArgumentException
     */
    public static Integer countPropsFromPath(BsonValue root, String path) throws IllegalArgumentException {
        var items = getPropsFromPath(root, path);

        if (items == null) {
            return null;
        } else {
            return items.size();
        }
    }

    private static String pathFromTokens(String[] pathTokens) {
        if (pathTokens == null) {
            return null;
        }

        var ret = new StringBuilder();

        for (int cont = 1; cont < pathTokens.length; cont++) {
            ret = ret.append(pathTokens[cont]);

            if (cont < pathTokens.length - 1) {
                ret = ret.append(".");
            }
        }

        return ret.toString();
    }

    private static String[] subpath(String[] pathTokens) {
        var subpath = new ArrayList<String>();

        for (int cont = 1; cont < pathTokens.length; cont++) {
            subpath.add(pathTokens[cont]);
        }

        return subpath.toArray(String[]::new);
    }

    /**
     *
     * @param o
     * @param type
     * @return
     */
    public static boolean checkType(Optional<BsonValue> o, String type) {
        if (!o.isPresent() && !"null".equals(type) && !"notnull".equals(type)) {
            return false;
        }

        return switch (type.toLowerCase().strip()) {
            case "null" -> !o.isPresent();
            case "notnull" -> o.isPresent();
            case "object" -> o.get().isDocument();
            case "array" -> o.get().isArray();
            case "string" -> o.get().isString();
            case "number" -> o.get().isNumber();
            case "boolean" -> o.get().isBoolean();
            case "objectid" -> o.get().isObjectId();
            case "objectidstring" -> o.get().isString() && ObjectId.isValid(o.get().asString().getValue());
            case "date" -> o.get().isDateTime();
            case "timestamp" -> o.get().isTimestamp();
            case "maxkey" -> o.get() instanceof BsonMaxKey;
            case "minkey" -> o.get() instanceof BsonMinKey;
            case "symbol" -> o.get().isSymbol();
            case "code" -> o.get() instanceof BsonJavaScript;
            default -> false;
        };
    }

    /**
     * @param jsonString
     * @return minified json string
     */
    public static String minify(String jsonString) {
        // Minify is not thread safe. don to declare as static object
        // see https://softinstigate.atlassian.net/browse/RH-233
        return new Minify().minify(jsonString);
    }

    /**
     * @param json
     * @return either a BsonDocument or a BsonArray from the json string orr null if argument is an blank String
     * @throws JsonParseException
     */
    public static BsonValue parse(String json) throws JsonParseException {
        if (json == null) {
            return null;
        }

        final var fnws = firstNonWhitespace(json);

        return switch(fnws) {
            case Character.MIN_VALUE -> null;
            case '{' -> {
                try {
                    yield BsonDocument.parse(json);
                } catch (BsonInvalidOperationException ex) {
                    // this can happen parsing a bson type, e.g.
                    // {"$oid": "xxxxxxxx" }
                    // the string starts with { but is not a document
                    yield getBsonValue(json);
                }
            }
            case '[' -> {
                try (var jr = new JsonReader(json)) {
                    yield BSON_ARRAY_CODEC.decode(jr, DecoderContext.builder().build());
                }
            }
            default -> getBsonValue(json);
        };
    }

    /**
     * @return the first not whitespace character in the string or Character.MIN_VALUE
     * if the string is empty or contains only whitespaces
     */
    static char firstNonWhitespace(String s){
        if (s == null || s.isBlank()) {
            return Character.MIN_VALUE;
        }

        var f = s.chars()
            .filter(c -> !Character.isWhitespace(c))
            .mapToObj(c -> (char) c)
            .findFirst();

        return f.isPresent() ? f.get() : Character.MIN_VALUE;
    }

    private static BsonValue getBsonValue(String json) {
        return BsonDocument.parse("{'x':".concat(json).concat("}")).get("x");
    }

    /**
     * @param bson either a BsonDocument or a BsonArray
     * @return the minified string representation of the bson value
     * @throws IllegalArgumentException if bson is not a BsonDocument or a
     * BsonArray
     */
    public static String toJson(BsonValue bson) {
        return toJson(bson, null);
    }

    /**
     * @param bson either a BsonDocument or a BsonArray
     * @param mode the JsonMode
     * @return the minified string representation of the bson value
     */
    public static String toJson(BsonValue bson, JsonMode mode) {
        if (bson == null) {
            return null;
        }

        var settings = mode != null
            ? JsonWriterSettings.builder()
                .outputMode(mode)
                .indent(false)
                .build()
            : JsonWriterSettings.builder()
                .indent(false)
                .dateTimeConverter((Long t, StrictJsonWriter writer) -> {
                    writer.writeRaw("{\"$date\": " + t + " }");
        }).build();

        if (bson.isDocument()) {
            return minify(bson.asDocument().toJson(settings));
        } else if (bson.isArray()) {
            var wrappedArray = new BsonDocument("w", bson.asArray());
            var json = minify(wrappedArray.toJson(settings));

            return json.substring(5, json.length()-1); // removes {"w": and } closing }
        } else {
            var ret = new BsonDocument("x", bson).toJson(settings);

            ret = ret.replaceFirst("\\{", "");
            ret = ret.replaceFirst("\"x\"", "");
            ret = ret.replaceFirst(":", "");
            int index = ret.lastIndexOf('}');
            ret = ret.substring(0, index);

            return minify(ret);
        }
    }

    /**
     *
     * @param id
     * @param quote
     * @return the String representation of the id
     */
    public static String getIdAsString(BsonValue id, boolean quote) {
        if (id == null) {
            return null;
        } else if (id.isString()) {
            return quote
                    ? "'" + id.asString().getValue() + "'"
                    : id.asString().getValue();
        } else if (id.isObjectId()) {
            return id.asObjectId().getValue().toString();
        } else {
            return minify(BsonUtils.toJson(id).replace("\"", "'"));
        }
    }

    public static final CodecRegistry DEFAULT_CODEC_REGISTRY = MongoClientSettings.getDefaultCodecRegistry();

    /**
     *
     * @param map
     * @return
     */
    public static BsonDocument toBsonDocument(Map<String, ? super Object> map) {
        if (map == null) {
            return null;
        }

        return new Document(map).toBsonDocument(BsonDocument.class, DEFAULT_CODEC_REGISTRY);
    }

    private static final String _UPDATE_OPERATORS[] = {
        "$inc", "$mul", "$rename", // Field Update Operators
        "$setOnInsert", "$set", "$unset",
        "$min", "$max", "$currentDate",
        "$", "$[]", "$addToSet", "$pop", "$pullAll", // Array Update Operators
        "$pull", "$pushAll", "$push",
        "$each", "$position", "$slice", "$sort",
        "$bit", // Bitwise Update Operator
        "$isolated" // Isolation Update Operator
    };

    private static final List<String> UPDATE_OPERATORS = Collections.unmodifiableList(Arrays.asList(_UPDATE_OPERATORS));

    /**
     * Seehttps://docs.mongodb.com/manual/reference/operator/update/
     * @param key
     * @return true if key is an update operator
     */
    public static boolean isUpdateOperator(String key) {
        return UPDATE_OPERATORS.contains(key);
    }

    /**
     * Seehttps://docs.mongodb.com/manual/reference/operator/update/
     *
     * @param json
     * @return true if json contains update operators
     */
    public static boolean containsUpdateOperators(BsonValue json) {
        return containsUpdateOperators(json, false);
    }

    /**
     * @see https://docs.mongodb.com/manual/reference/operator/update/
     *
     * @param ignoreCurrentDate true to ignore $currentDate
     * @param json
     * @return true if json contains update operators
     */
    public static boolean containsUpdateOperators(BsonValue json,
            boolean ignoreCurrentDate) {
        if (json == null) {
            return false;
        } else if (json.isDocument()) {
            return _containsUpdateOperators(json.asDocument(), ignoreCurrentDate);
        } else if (json.isArray()) {
            return json.asArray()
                    .stream()
                    .filter(el -> el.isDocument())
                    .anyMatch(element -> _containsUpdateOperators(element.asDocument(), ignoreCurrentDate));
        } else {
            return false;
        }
    }

    private static boolean _containsUpdateOperators(BsonDocument json,
            boolean ignoreCurrentDate) {
        if (json == null) {
            return false;
        }

        return json.asDocument().keySet().stream()
                .filter(key -> !ignoreCurrentDate || !key.equals("$currentDate"))
                .anyMatch(key -> isUpdateOperator(key));
    }

    /**
     * @param json
     * @return the unflatten json replacing dot notatation keys with nested
     * objects: from {"a.b":2} to {"a":{"b":2}}
     */
    public static BsonValue unflatten(BsonValue json) throws IllegalArgumentException {
        return new JsonUnflattener(json).unflatten();
    }

    /**
     *
     * @param json
     * @param ignoreUpdateOperators true to not flatten update operators
     *
     * @return the flatten json objects using dot notation from {"a":{"b":1},
     * {"$currentDate": {"my.field": true}} to {"a.b":1, {"$currentDate":
     * {"my.field": true}}
     */
    public static BsonDocument flatten(BsonDocument json, boolean ignoreUpdateOperators) {
        var keys = json.keySet().stream()
            .filter(key -> !ignoreUpdateOperators || !isUpdateOperator(key))
            .collect(Collectors.toList());

        if (keys != null && !keys.isEmpty()) {
            var ret = new BsonDocument();

            // add update operators
            json.keySet().stream()
                .filter(key -> BsonUtils.isUpdateOperator(key))
                .forEach(key -> ret.put(key, json.get(key)));

            // add properties to $set update operator
            keys.stream().forEach(key -> flatten(null, key, json, ret));

            return ret;
        } else {
            return json;
        }
    }

    private static void flatten(String prefix, String key, BsonDocument data, BsonDocument set) {
        final var newPrefix = prefix == null ? key : prefix + "." + key;
        final var value = data.get(key);

        if (value.isDocument()) {
            value.asDocument().keySet()
                .forEach(childKey -> flatten(newPrefix, childKey, value.asDocument(), set));
        } else {
            set.append(newPrefix, value);
        }
    }

    /**
     * Verifies if the bson contains the given keys.
     * It also takes into account the cases where the bson cotains keys using the dot notation
     * or update operators.
     *
     * @param docOrArray the bson to check
     * @param keys the keys
     * @param all true to check the bson to contain all given keys, false for any of the given keys
     * @return true if the bson cotains the given keys
     */
    public static boolean containsKeys(BsonValue docOrArray, Set<String> keys, boolean all) {
        if (docOrArray == null) {
            return false;
        } else if (docOrArray.isArray()) {
            var array = docOrArray.asArray();

            if (array.isEmpty()) {
                return false;
            } else {
                return all
                    ? array.stream().allMatch(doc -> containsKeys(doc, keys, all))
                    : array.stream().anyMatch(doc -> containsKeys(doc, keys, all));
            }
        } else if (docOrArray.isDocument()) {
            return _containsKeys(docOrArray.asDocument(), keys, all);
        } else {
            return false;
        }
    }

    private static boolean _containsKeys(BsonDocument doc, Set<String> keys, boolean all) {
        var ufdoc = BsonUtils.unflatten(doc).asDocument();

        return all
            ? keys.stream().allMatch(key -> _containsKeys(ufdoc, key, all))
            : keys.stream().anyMatch(key -> _containsKeys(ufdoc, key, all));
    }

    private static boolean _containsKeys(BsonDocument doc, String key, boolean all) {
        // let's check update operators first, since doc can look like:
        // {
        // <operator1>: { <field1>: <value1>, ... },
        // <operator2>: { <field2>: <value2>, ... },
        // ...
        // }

        if (BsonUtils.containsUpdateOperators(doc)) {
            // here we check if the doc contains the key in a update operator
            var updateOperators = doc.keySet().stream().filter(k -> k.startsWith("$")).collect(Collectors.toList());

            var checkInUO = updateOperators.stream().anyMatch(uo -> _containsKeys(BsonUtils.unflatten(doc.get(uo)).asDocument(), key, all));

            if (checkInUO) {
                return true;
            }
        }

        if (key.contains(".")) {
            var first = key.substring(0, key.indexOf("."));
            if (first.length() > 0 && doc.containsKey(first) && doc.get(first).isDocument()) {
                var remaining = key.substring(key.indexOf(".") + 1);

                return _containsKeys(doc.get(first).asDocument(), remaining, all);
            } else if (first.length() > 0 && doc.containsKey(first) && doc.get(first).isArray()) {
                var remaining = key.substring(key.indexOf(".") + 1);

                return containsKeys(doc.get(first).asArray(), Sets.newHashSet(remaining), all);
            } {
                return false;
            }
        } else {
            return key.length() > 0 && doc.containsKey(key);
        }
    }

    private final static DocumentCodec codec = new DocumentCodec();

    /**
     * convert BsonDocument to Document
     * @param bsonDocument
     * @return
     */
    public static Document bsonToDocument(BsonDocument bsonDocument) {
        DecoderContext decoderContext = DecoderContext.builder().build();
        return codec.decode(new BsonDocumentReader(bsonDocument), decoderContext);
    }

    /**
     *
     * @return a DocumentBuilder to help building BsonDocument
     */
    public static DocumentBuilder document() {
        return DocumentBuilder.builder();
    }

    /**
     * @param doc the BsonDocument to wrap
     *
     * @return a DocumentBuilder to help building BsonDocument
     */
    public static DocumentBuilder document(BsonDocument doc) {
        return DocumentBuilder.builder(doc);
    }

    /**
     *
     * @return a ArrayBuilder to help building BsonArray
     */
    public static ArrayBuilder array() {
        return ArrayBuilder.builder();
    }

    /**
     * @param array the BsonArray to wrap
     *
     * @return a ArrayBuilder to help building BsonArray
     */
    public static ArrayBuilder array(BsonArray array) {
        return ArrayBuilder.builder(array);
    }

    /**
     * Builder to help creating BsonDocument
     */
    public static class DocumentBuilder {
        private final BsonDocument doc;

        public static DocumentBuilder builder() {
            return new DocumentBuilder();
        }

        private DocumentBuilder() {
            this.doc = new BsonDocument();
        }

        public static DocumentBuilder builder(BsonDocument doc) {
            return new DocumentBuilder(doc);
        }

        private DocumentBuilder(BsonDocument doc) {
            this.doc = doc;
        }

        @Override
        public String toString() {
            return toJson();
        }

        public String toJson() {
            return BsonUtils.toJson(this.get());
        }

        public String toJson(JsonMode jsonMode) {
            return BsonUtils.toJson(this.get(), jsonMode);
        }

        public String toJson(String jsonMode) {
            return BsonUtils.toJson(this.get(), JsonMode.valueOf(jsonMode));
        }

        public DocumentBuilder put(String key, BsonValue value) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            Objects.nonNull(value);

            if (value == null) {
                putNull(key);
            } else {
                doc.put(key, value);
            }

            return this;
        }

        public DocumentBuilder putAll(BsonDocument other) {
            if (other != null) {
                doc.putAll(other);
            }
            return this;
        }

        public DocumentBuilder put(String key, Integer value) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (value == null) {
                putNull(key);
            } else {
                doc.put(key, new BsonInt32(value));
            }

            return this;
        }

        public DocumentBuilder put(String key, Long value) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (value == null) {
                putNull(key);
            } else {
                doc.put(key, new BsonInt64(value));
            }

            return this;
        }

        public DocumentBuilder put(String key, Float value) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (value == null) {
                putNull(key);
            } else {
                doc.put(key, new BsonDouble(value));
            }

            return this;
        }

        public DocumentBuilder put(String key, Decimal128 value) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (value == null) {
                putNull(key);
            } else {
                doc.put(key, new BsonDecimal128(value));
            }


            return this;
        }

        public DocumentBuilder put(String key, Boolean value) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (value == null) {
                putNull(key);
            } else {
                doc.put(key, new BsonBoolean(value));
            }

            return this;
        }

        public DocumentBuilder put(String key, String value) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (value == null) {
                putNull(key);
            } else {
                doc.put(key, new BsonString(value));
            }

            return this;
        }

        public DocumentBuilder put(String key, Instant value) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (value == null) {
                putNull(key);
            } else {
                doc.put(key, new BsonDateTime(value.getEpochSecond()*1000));
            }

            return this;
        }

        public DocumentBuilder put(String key, Date value) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (value == null) {
                putNull(key);
            } else {
                doc.put(key, new BsonDateTime(value.getTime()));
            }

            return this;
        }

        public DocumentBuilder put(String key, ObjectId value) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (value == null) {
                putNull(key);
            } else {
                doc.put(key, new BsonObjectId(value));
            }

            return this;
        }

        public DocumentBuilder putNull(String key) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            doc.put(key, BsonNull.VALUE);
            return this;
        }

        public DocumentBuilder put(String key, DocumentBuilder builder) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (builder != null) {
                doc.put(key, builder.get());
            }

            return this;
        }

        public DocumentBuilder put(String key, ArrayBuilder builder) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (builder != null) {
                doc.put(key, builder.get());
            }

            return this;
        }

        public BsonDocument get() {
            return doc;
        }
    }

    /**
     * Builder to help creating BsonArray
     */
    public static class ArrayBuilder {
        private final BsonArray array;

        public static ArrayBuilder builder() {
            return new ArrayBuilder();
        }

        public static ArrayBuilder builder(BsonArray array) {
            return new ArrayBuilder(array);
        }

        private ArrayBuilder() {
            this.array = new BsonArray();
        }

        private ArrayBuilder(BsonArray array) {
            this.array = array;
        }

        @Override
        public String toString() {
            return toJson();
        }

        public String toJson() {
            return BsonUtils.toJson(this.get());
        }

        public String toJson(JsonMode jsonMode) {
            return BsonUtils.toJson(this.get(), jsonMode);
        }

        public String toJson(String jsonMode) {
            return BsonUtils.toJson(this.get(), JsonMode.valueOf(jsonMode));
        }

        public ArrayBuilder add(BsonValue... values) {
            Arrays.stream(values).map(value -> value == null ? BsonNull.VALUE : value).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(String... values) {
            Arrays.stream(values).map(v -> v == null ? BsonNull.VALUE : new BsonString(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(Integer... values) {
            Arrays.stream(values).map(v -> v == null ? BsonNull.VALUE : new BsonInt32(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(Long... values) {
            Arrays.stream(values).map(v -> v == null ? BsonNull.VALUE : new BsonInt64(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(Float... values) {
            Arrays.stream(values).map(v -> v == null ? BsonNull.VALUE : new BsonDouble(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(Decimal128... values) {
            Arrays.stream(values).map(v -> v == null ? BsonNull.VALUE : new BsonDecimal128(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(Boolean... values) {
            Arrays.stream(values).map(v -> v == null ? BsonNull.VALUE : new BsonBoolean(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(Instant... values) {
            Arrays.stream(values).map(v -> v == null ? BsonNull.VALUE : new BsonDateTime(v.getEpochSecond()*1000)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(Date... values) {
            Arrays.stream(values).map(v -> v == null ? BsonNull.VALUE : new BsonDateTime(v.getTime())).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(ObjectId... values) {
            Arrays.stream(values).map(v -> v == null ? BsonNull.VALUE : new BsonObjectId(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder addNull() {
            array.add(BsonNull.VALUE);
            return this;
        }

        public ArrayBuilder add(DocumentBuilder... builders) {
            Arrays.stream(builders).filter(b -> b != null).map(DocumentBuilder::get).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(ArrayBuilder... builders) {
            Arrays.stream(builders).filter(b -> b != null).map(ArrayBuilder::get).forEach(array::add);
            return this;
        }

        public BsonArray get() {
            return array;
        }
    }
}