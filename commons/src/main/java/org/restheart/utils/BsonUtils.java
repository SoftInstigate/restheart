/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2019 - 2026 SoftInstigate
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

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.jxpath.JXPathContext;
import org.bson.*;
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
import org.restheart.cache.Cache.EXPIRE_POLICY;
import org.restheart.cache.CacheFactory;
import org.restheart.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.mongodb.MongoClientSettings;

/**
 * Utility class providing comprehensive BSON document manipulation operations.
 * This class contains methods for parsing, converting, escaping, and manipulating
 * BSON documents and values. It supports operations like key escaping/unescaping,
 * path-based property access, document flattening/unflattening, and JSON conversion.
 *
 * <p>
 * Key features include:
 * </p>
 * <ul>
 * <li>Key escaping for MongoDB compatibility (handles $ and . characters)</li>
 * <li>XPath-style property access using dot notation</li>
 * <li>Document flattening and unflattening operations</li>
 * <li>JSON parsing and serialization</li>
 * <li>Update operator detection and validation</li>
 * <li>Builder patterns for document and array construction</li>
 * </ul>
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 * @author Maurizio Turatti {@literal <maurizio@softinstigate.com>}
 */
public class BsonUtils {

    /**
     * Builder to help creating BsonDocument
     */
    public static class DocumentBuilder {
        public static DocumentBuilder builder() {
            return new DocumentBuilder();
        }

        public static DocumentBuilder builder(final BsonDocument doc) {
            return new DocumentBuilder(doc);
        }

        private final BsonDocument doc;

        private DocumentBuilder() {
            this.doc = new BsonDocument();
        }

        private DocumentBuilder(final BsonDocument doc) {
            this.doc = doc;
        }

        @Override
        public String toString() {
            return toJson();
        }

        public String toJson() {
            return BsonUtils.toJson(this.get());
        }

        public String toJson(final JsonMode jsonMode) {
            return BsonUtils.toJson(this.get(), jsonMode);
        }

        public String toJson(final String jsonMode) {
            return BsonUtils.toJson(this.get(), JsonMode.valueOf(jsonMode));
        }

        public DocumentBuilder put(final String key, final BsonValue value) {
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

        public DocumentBuilder putAll(final BsonDocument other) {
            if (other != null) {
                doc.putAll(other);
            }
            return this;
        }

        public DocumentBuilder put(final String key, final Integer value) {
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

        public DocumentBuilder put(final String key, final Long value) {
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

        public DocumentBuilder put(final String key, final Float value) {
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

        public DocumentBuilder put(final String key, final Decimal128 value) {
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

        public DocumentBuilder put(final String key, final Boolean value) {
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

        public DocumentBuilder put(final String key, final String value) {
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

        public DocumentBuilder put(final String key, final Instant value) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (value == null) {
                putNull(key);
            } else {
                doc.put(key, new BsonDateTime(value.getEpochSecond() * 1000));
            }

            return this;
        }

        public DocumentBuilder put(final String key, final Date value) {
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

        public DocumentBuilder put(final String key, final ObjectId value) {
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

        public DocumentBuilder putNull(final String key) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            doc.put(key, BsonNull.VALUE);
            return this;
        }

        public DocumentBuilder put(final String key, final DocumentBuilder builder) {
            if (key == null) {
                throw new IllegalArgumentException("argument key cannot be null");
            }

            if (builder != null) {
                doc.put(key, builder.get());
            }

            return this;
        }

        public DocumentBuilder put(final String key, final ArrayBuilder builder) {
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
        public static ArrayBuilder builder() {
            return new ArrayBuilder();
        }

        public static ArrayBuilder builder(final BsonArray array) {
            return new ArrayBuilder(array);
        }

        private final BsonArray array;

        private ArrayBuilder() {
            this.array = new BsonArray();
        }

        private ArrayBuilder(final BsonArray array) {
            this.array = array;
        }

        @Override
        public String toString() {
            return toJson();
        }

        public String toJson() {
            return BsonUtils.toJson(this.get());
        }

        public String toJson(final JsonMode jsonMode) {
            return BsonUtils.toJson(this.get(), jsonMode);
        }

        public String toJson(final String jsonMode) {
            return BsonUtils.toJson(this.get(), JsonMode.valueOf(jsonMode));
        }

        public ArrayBuilder add(final BsonValue... values) {
            Arrays.stream(values).map(value -> value == null
                ? BsonNull.VALUE
                : value).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(final String... values) {
            Arrays.stream(values).map(v -> v == null
                ? BsonNull.VALUE
                : new BsonString(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(final Integer... values) {
            Arrays.stream(values).map(v -> v == null
                ? BsonNull.VALUE
                : new BsonInt32(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(final Long... values) {
            Arrays.stream(values).map(v -> v == null
                ? BsonNull.VALUE
                : new BsonInt64(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(final Float... values) {
            Arrays.stream(values).map(v -> v == null
                ? BsonNull.VALUE
                : new BsonDouble(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(final Decimal128... values) {
            Arrays.stream(values).map(v -> v == null
                ? BsonNull.VALUE
                : new BsonDecimal128(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(final Boolean... values) {
            Arrays.stream(values).map(v -> v == null
                ? BsonNull.VALUE
                : new BsonBoolean(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(final Instant... values) {
            Arrays.stream(values).map(v -> v == null
                ? BsonNull.VALUE
                : new BsonDateTime(v.getEpochSecond() * 1000)).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(final Date... values) {
            Arrays.stream(values).map(v -> v == null
                ? BsonNull.VALUE
                : new BsonDateTime(v.getTime())).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(final ObjectId... values) {
            Arrays.stream(values).map(v -> v == null
                ? BsonNull.VALUE
                : new BsonObjectId(v)).forEach(array::add);
            return this;
        }

        public ArrayBuilder addNull() {
            array.add(BsonNull.VALUE);
            return this;
        }

        public ArrayBuilder add(final DocumentBuilder... builders) {
            Arrays.stream(builders).filter(b -> b != null).map(DocumentBuilder::get).forEach(array::add);
            return this;
        }

        public ArrayBuilder add(final ArrayBuilder... builders) {
            Arrays.stream(builders).filter(b -> b != null).map(ArrayBuilder::get).forEach(array::add);
            return this;
        }

        public BsonArray get() {
            return array;
        }
    }

    private interface PathToken {
        static PathToken key(final String key) {
            return current -> {
                if (!current.isDocument()) {
                    return null;
                }

                return current.asDocument().containsKey(key)
                    ? current.asDocument().get(key)
                    : null;
            };
        }

        static PathToken index(final int index) {
            return current -> {
                if (!current.isArray()) {
                    return null;
                }

                final var array = current.asArray();

                return index >= 0 && index < array.size()
                    ? array.get(index)
                    : null;
            };
        }

        BsonValue resolve(BsonValue current);
    }

    /** Logger instance for this class. */
    static final Logger LOGGER = LoggerFactory.getLogger(BsonUtils.class);

    /** Codec for encoding/decoding BSON arrays. */
    private static final BsonArrayCodec BSON_ARRAY_CODEC = new BsonArrayCodec(
            CodecRegistries.fromProviders(new BsonValueCodecProvider()));

    /** Codec registry for document encoding/decoding operations. */
    private static final CodecRegistry REGISTRY = CodecRegistries.fromCodecs(new DocumentCodec());

    /** Escaped representation of the dollar sign character. */
    private static final String ESCAPED_DOLLAR = "_$";

    /** Escaped representation of the dot character. */
    private static final String ESCAPED_DOT = "::";

    /** The dollar sign character. */
    private static final String DOLLAR = "$";

    private static final LoadingCache<String, String> xPathCache = CacheFactory.createLocalLoadingCache(1_000,
            EXPIRE_POLICY.AFTER_READ, 1_000, dotn -> dotNotationToXPath(dotn));

    private static final LoadingCache<String, List<PathToken>> pathTokensCache = CacheFactory
            .createLocalLoadingCache(1_000, EXPIRE_POLICY.AFTER_READ, 1_000, BsonUtils::parsePathTokens);

    /** Default codec registry from MongoDB client settings for BSON encoding/decoding operations. */
    public static final CodecRegistry DEFAULT_CODEC_REGISTRY = MongoClientSettings.getDefaultCodecRegistry();

    private static final String[] _UPDATE_OPERATORS = {
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

    private static final DocumentCodec codec = new DocumentCodec();

    /**
     * Replaces the underscore prefixed keys (e.g., _$exists) with the
     * corresponding key (e.g., $exists) and replaces escaped dots (::) with
     * actual dots (.) in property names. This operation reverses the escaping
     * applied by {@link #escapeKeys(BsonValue)} method.
     *
     * <p>
     * This is needed because MongoDB does not allow storing keys that start with $
     * and contain dots in them.
     * </p>
     *
     * @param json the BSON value to process (can be document, array, or primitive)
     * @return the BSON value where the underscore prefixed keys are replaced
     * with the corresponding unescaped keys, or null if input is null
     * @see <a href="https://docs.mongodb.org/manual/reference/limits/#Restrictions-on-Field-Names">MongoDB Field Name
     * Restrictions</a>
     */
    public static BsonValue unescapeKeys(final BsonValue json) {
        if (json == null) {
            return null;
        }

        if (json.isDocument()) {
            return transformBsonKeys(json);
        } else if (json.isArray()) {
            return transformArrayElements(json);
        } else if (json.isString()) {
            return json.asString().getValue().startsWith(ESCAPED_DOLLAR)
                ? new BsonString(json.asString().getValue().substring(1))
                : json;
        } else {
            return json;
        }
    }

    /**
     * Replaces the dollar prefixed keys (e.g., $exists) with the corresponding
     * underscore prefixed key (e.g., _$exists). Also replaces dots with escaped
     * dots (::) if escapeDots is true. This is needed because MongoDB does not
     * allow storing keys that start with $ and contain dots.
     *
     * @param json the BSON value to process (can be document, array, or primitive)
     * @param escapeDots if true, dots in keys will be replaced with "::"
     * @return the BSON value where the keys are escaped for MongoDB compatibility,
     * or null if input is null
     */
    public static BsonValue escapeKeys(final BsonValue json, final boolean escapeDots) {
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
     * @param dontEscapeDotsInRootKeys specify if dots in root level keys should not be escaped when escapeDots=true.
     * root level
     * @return the json object where the keys are escaped
     * with the corresponding keys
     */
    public static BsonValue escapeKeys(final BsonValue json, final boolean escapeDots,
            final boolean dontEscapeDotsInRootKeys) {
        if (json == null) {
            return null;
        }

        if (json.isDocument()) {
            final var ret = new BsonDocument();

            final boolean[] root = { true };

            json.asDocument().keySet().stream().forEach(k -> {
                var newKey = k.startsWith(DOLLAR)
                    ? "_" + k
                    : k;

                if (escapeDots && !(dontEscapeDotsInRootKeys && root[0])) {
                    newKey = newKey.replaceAll("\\.", ESCAPED_DOT);
                }

                root[0] = false;

                final var value = json.asDocument().get(k);

                storeBsonValue(escapeDots, ret, newKey, value);

            });

            return ret;
        } else if (json.isArray()) {
            return processArrayElements(json, escapeDots, dontEscapeDotsInRootKeys);
        } else if (json.isString()) {
            return json.asString().getValue().startsWith(DOLLAR)
                ? new BsonString("_" + json.asString().getValue())
                : json;
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
    public static List<Optional<BsonValue>> getPropsFromPath(final BsonValue root, final String path)
            throws IllegalArgumentException {
        final String[] pathTokens = path.split(Pattern.quote("."));

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
    public static Optional<BsonValue> get(final BsonDocument doc, final String path) {
        if (doc == null || path == null || path.isBlank()) {
            return Optional.empty();
        } else if (doc.containsKey(path)) {
            return Optional.of(doc.get(path));
        }

        try {
            final var tokens$ = pathTokensCache.getLoading(path);
            var current = (BsonValue) doc;

            for (final var token : tokens$.get()) {
                current = token.resolve(current);

                if (current == null) {
                    return Optional.empty();
                }
            }

            return Optional.of(current);
        } catch (final Throwable t) {
            return Optional.empty();
        }
    }

    /**
     *
     * @param ctx the JxPathContext build from a BsonDocument
     * @param path the path of the field, can use the dot notation
     * @return
     */
    public static Optional<BsonValue> get(final JXPathContext ctx, final String path) {
        try {
            final var xpath$ = xPathCache.getLoading(path);
            return Optional.of((BsonValue) ctx.getValue(xpath$.get()));
        } catch (final Throwable t) {
            return Optional.empty();
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
            final String[] leftPathTokens = left.split(Pattern.quote("."));
            final String[] rightPathTokens = right.split(Pattern.quote("."));

            if (leftPathTokens.length > rightPathTokens.length) {
                ret = false;
            } else {
                ret = comparePathTokens(ret, leftPathTokens, rightPathTokens);
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
    public static Integer countPropsFromPath(final BsonValue root, final String path) throws IllegalArgumentException {
        final var items = getPropsFromPath(root, path);

        if (items == null) {
            return null;
        } else {
            return items.size();
        }
    }

    /**
     *
     * @param o - the optional BsonValue to check
     * @param type - the type to check against
     * @return true if the BsonValue matches the specified type, false otherwise
     */
    public static boolean checkType(final Optional<BsonValue> o, final String type) {
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
     * @param jsonString the JSON string to minify
     * @return minified json string
     */
    public static StringBuilder minify(final String jsonString) {
        // Minify is not thread safe. don to declare as static object
        // see https://softinstigate.atlassian.net/browse/RH-233
        return new Minify().minify(jsonString);
    }

    /**
     * @param json the JSON string to parse
     * @return either a BsonDocument or a BsonArray from the json string orr null if argument is an blank String
     * @throws JsonParseException if the json string is not a valid JSON or if it is a valid JSON but does not represent
     * a document or an array
     */
    public static BsonValue parse(final String json) throws JsonParseException {
        if (json == null) {
            return null;
        }

        final var fnws = firstNonWhitespace(json);

        return switch (fnws) {
            case Character.MIN_VALUE -> null;
            case '{' -> {
                try {
                    yield BsonDocument.parse(json);
                } catch (final BsonInvalidOperationException ex) {
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
     * @param bson either a BsonDocument or a BsonArray
     * @return the minified string representation of the bson value
     * @throws IllegalArgumentException if bson is not a BsonDocument or a
     * BsonArray
     */
    public static String toJson(final BsonValue bson) {
        return toJson(bson, null);
    }

    /**
     * @param bson either a BsonDocument or a BsonArray
     * @param mode the JsonMode
     * @return the minified string representation of the bson value
     */
    public static String toJson(final BsonValue bson, final JsonMode mode) {
        if (bson == null) {
            return null;
        }

        final var settings = mode != null
            ? JsonWriterSettings.builder()
                    .outputMode(mode)
                    .indent(false)
                    .build()
            : JsonWriterSettings.builder()
                    .indent(false)
                    .dateTimeConverter(
                            (final Long t, final StrictJsonWriter writer) -> writer.writeRaw("{\"$date\": " + t + " }"))
                    .build();

        if (bson.isDocument()) {
            return bson.asDocument().toJson(settings);
        } else if (bson.isArray()) {
            final var wrappedArray = new BsonDocument("w", bson.asArray());
            final var json = wrappedArray.toJson(settings);

            // removes {"w" : and } closing }
            return json.substring(6, json.length() - 1);
        } else {
            var ret = new BsonDocument("x", bson).toJson(settings);

            ret = ret.replaceFirst("\\{", "");
            ret = ret.replaceFirst("\"x\"", "");
            ret = ret.replaceFirst(":", "");
            final int index = ret.lastIndexOf('}');
            ret = ret.substring(0, index);

            return ret.strip();
        }
    }

    /**
     * Converts a BSON value representing an ID to its string representation.
     * Handles different BSON types appropriately and can optionally quote string values.
     *
     * @param id the BSON value to convert to string
     * @param quote if true, string values will be wrapped in single quotes
     * @return the string representation of the ID, or null if id is null
     */
    public static String getIdAsString(final BsonValue id, final boolean quote) {
        if (id == null) {
            return null;
        } else if (id.isString()) {
            return quote
                ? "'" + id.asString().getValue() + "'"
                : id.asString().getValue();
        } else if (id.isObjectId()) {
            return id.asObjectId().getValue().toString();
        } else {
            return minify(BsonUtils.toJson(id).replace("\"", "'")).toString();
        }
    }

    /**
     * Converts a Map to a BsonDocument using the default codec registry.
     *
     * @param map the map to convert to BSON document
     * @return the BsonDocument representation of the map, or null if map is null
     */
    public static BsonDocument toBsonDocument(final Map<String, ? super Object> map) {
        if (map == null) {
            return null;
        }

        return new Document(map).toBsonDocument(BsonDocument.class, DEFAULT_CODEC_REGISTRY);
    }

    /**
     * Seehttps://docs.mongodb.com/manual/reference/operator/update/
     * 
     * @param key
     * @return true if key is an update operator
     */
    public static boolean isUpdateOperator(final String key) {
        return UPDATE_OPERATORS.contains(key);
    }

    /**
     * Seehttps://docs.mongodb.com/manual/reference/operator/update/
     *
     * @param json
     * @return true if json contains update operators
     */
    public static boolean containsUpdateOperators(final BsonValue json) {
        return containsUpdateOperators(json, false);
    }

    /**
     * @see https://docs.mongodb.com/manual/reference/operator/update/
     *
     * @param ignoreCurrentDate true to ignore $currentDate
     * @param json
     * @return true if json contains update operators
     */
    public static boolean containsUpdateOperators(final BsonValue json,
            final boolean ignoreCurrentDate) {
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

    /**
     * @param json
     * @return the unflatten json replacing dot notatation keys with nested
     * objects: from {"a.b":2} to {"a":{"b":2}}
     */
    public static BsonValue unflatten(final BsonValue json) throws IllegalArgumentException {
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
    public static BsonDocument flatten(final BsonDocument json, final boolean ignoreUpdateOperators) {
        final var keys = json.keySet().stream()
                .filter(key -> !ignoreUpdateOperators || !isUpdateOperator(key))
                .collect(Collectors.toList());

        if (keys != null && !keys.isEmpty()) {
            final var ret = new BsonDocument();

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
    public static boolean containsKeys(final BsonValue docOrArray, final Set<String> keys, final boolean all) {
        if (docOrArray == null) {
            return false;
        } else if (docOrArray.isArray()) {
            final var array = docOrArray.asArray();

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

    /**
     * convert BsonDocument to Document
     * 
     * @param bsonDocument
     * @return
     */
    public static Document bsonToDocument(final BsonDocument bsonDocument) {
        final DecoderContext decoderContext = DecoderContext.builder().build();
        return codec.decode(new BsonDocumentReader(bsonDocument), decoderContext);
    }

    /**
     * convert Document to BsonDocument
     * 
     * @param document
     * @return
     */
    public static BsonValue documentToBson(final Document document) {
        return document == null
            ? BsonNull.VALUE
            : document.toBsonDocument(BsonDocument.class, REGISTRY);
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
    public static DocumentBuilder document(final BsonDocument doc) {
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
    public static ArrayBuilder array(final BsonArray array) {
        return ArrayBuilder.builder(array);
    }

    /**
     * @return the first not whitespace character in the string or Character.MIN_VALUE
     * if the string is empty or contains only whitespaces
     */
    static char firstNonWhitespace(final String s) {
        if (s == null || s.isBlank()) {
            return Character.MIN_VALUE;
        }

        final var f = s.chars()
                .filter(c -> !Character.isWhitespace(c))
                .mapToObj(c -> (char) c)
                .findFirst();

        return f.isPresent()
            ? f.get()
            : Character.MIN_VALUE;
    }

    private static BsonValue transformArrayElements(final BsonValue json) {
        final var ret = new BsonArray();

        json.asArray().stream().forEach(value -> {
            if (value.isDocument()) {
                ret.add(unescapeKeys(value));
            } else if (value.isArray()) {
                final var newList = new BsonArray();
                value.asArray().stream().forEach(v -> newList.add(unescapeKeys(v)));
                ret.add(newList);
            } else {
                ret.add(unescapeKeys(value));
            }

        });

        return ret;
    }

    private static BsonValue transformBsonKeys(final BsonValue json) {
        final var ret = new BsonDocument();

        json.asDocument().keySet().stream().forEach(k -> {
            var newKey = k.startsWith(ESCAPED_DOLLAR)
                ? k.substring(1)
                : k;
            newKey = newKey.replaceAll(ESCAPED_DOT, ".");

            final var value = json.asDocument().get(k);

            if (value.isDocument()) {
                ret.put(newKey, unescapeKeys(value));
            } else if (value.isArray()) {
                final var newList = new BsonArray();

                value.asArray().stream().forEach(v -> newList.add(unescapeKeys(v)));

                ret.put(newKey, newList);
            } else {
                ret.put(newKey, unescapeKeys(value));
            }

        });

        return ret;
    }

    private static void storeBsonValue(final boolean escapeDots, final BsonDocument ret, final String newKey,
            final BsonValue value) {
        if (value.isDocument()) {
            ret.put(newKey, escapeKeys(value, escapeDots, false));
        } else if (value.isArray()) {
            final var newList = new BsonArray();
            value.asArray().stream().forEach(v -> newList.add(escapeKeys(v, escapeDots, false)));
            ret.put(newKey, newList);
        } else {
            ret.put(newKey, value);
        }
    }

    private static BsonValue processArrayElements(final BsonValue json, final boolean escapeDots,
            final boolean dontEscapeDotsInRootKeys) {
        final var ret = array();

        json.asArray().stream().forEach(value -> {
            if (value.isDocument()) {
                ret.add(escapeKeys(value, escapeDots, dontEscapeDotsInRootKeys));
            } else if (value.isArray()) {
                final var newList = new BsonArray();
                value.asArray().stream().forEach(v -> newList.add(escapeKeys(v, escapeDots, false)));
                ret.add(newList);
            } else {
                ret.add(value);
            }
        });

        return ret.get();
    }

    private static List<PathToken> parsePathTokens(final String path) {
        final var tokens = new ArrayList<PathToken>();

        var i = 0;
        while (i < path.length()) {
            var c = path.charAt(i);

            if (c == '.') {
                i++;
                continue;
            }

            if (c == '[') {
                i = parseBracketToken(path, i, tokens);
                continue;
            }

            final var start = i;
            while (i < path.length()) {
                c = path.charAt(i);
                if (c == '.' || c == '[') {
                    break;
                }
                i++;
            }

            if (start == i) {
                throw new IllegalArgumentException("invalid path: " + path);
            }

            tokens.add(PathToken.key(path.substring(start, i)));
        }

        return tokens;
    }

    private static int parseBracketToken(final String path, final int start, final List<PathToken> tokens) {
        if (start + 1 >= path.length()) {
            throw new IllegalArgumentException("invalid path: " + path);
        }

        final var quote = path.charAt(start + 1);
        if (quote == '\'' || quote == '"') {
            final var keyStart = start + 2;
            final var keyEnd = path.indexOf(quote + "]", keyStart);

            if (keyEnd < 0) {
                throw new IllegalArgumentException("invalid path: " + path);
            }

            tokens.add(PathToken.key(path.substring(keyStart, keyEnd)));
            return keyEnd + 2;
        }

        final var indexEnd = path.indexOf(']', start + 1);

        if (indexEnd < 0) {
            throw new IllegalArgumentException("invalid path: " + path);
        }

        final var idx = Integer.parseInt(path.substring(start + 1, indexEnd));
        tokens.add(PathToken.index(idx));

        return indexEnd + 1;
    }

    /**
     * converts the dot notation syntax to xpath
     * a.b.c -> /a/b/c
     * a[1].c -> /a[2]/c (xpath indexes are 1-based!)
     * 
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
        dn = dn.replace("\\['", "/");
        dn = dn.replace("'\\]", "/");
        // remove tailing /
        if (dn.endsWith("/")) {
            dn = dn.substring(0, dn.length() - 1);
        }

        // replace all array indexes [n] with[n+1]
        dn = Pattern.compile("\\[(?<idx>\\d*)\\]").matcher(dn)
                .replaceAll(mr -> inc(mr.group()));

        // // add leading /
        return "/".concat(dn);
    }

    private static String inc(final String n) {
        final var _n = n.replace("\\[", "").replace("\\]", "");
        try {
            return "[" + (Integer.parseInt(_n) + 1) + "]";
        } catch (final NumberFormatException nfe) {
            return n;
        }
    }

    private static List<Optional<BsonValue>> _getPropsFromPath(final BsonValue json, final String[] pathTokens,
            final int totalTokensLength) throws IllegalArgumentException {
        if (pathTokens == null) {
            throw new IllegalArgumentException("pathTokens argument cannot be null");
        }

        String pathToken;

        if (pathTokens.length > 0) {
            if (json == null) {
                return null;
            } else {
                pathToken = extractPrimaryPathToken(pathTokens);
            }
        } else if (json.isNull()) {
            // if value is null return an empty optional
            return createEmptyOptionalList();
        } else {
            return createOptionalListFromJson(json);
        }

        switch (pathToken) {
            case DOLLAR -> {
                return retrievePropertiesFromDocument(json, pathTokens, totalTokensLength, pathToken);
            }
            case "*" -> {
                if (!(json.isDocument())) {
                    return null;
                } else {
                    return retrieveNestedProperties(json, pathTokens, totalTokensLength);
                }
            }
            case "[*]" -> {
                if (!(json.isArray())) {
                    return retrieveNestedDocumentProperties(json, pathTokens, totalTokensLength);
                } else {
                    return retrievePropertiesFromArray(json, pathTokens, totalTokensLength);
                }
            }
            default -> {
                return retrievePropertyFromDocument(json, pathTokens, totalTokensLength, pathToken);
            }
        }
    }

    private static List<Optional<BsonValue>> retrieveNestedDocumentProperties(final BsonValue json,
            final String[] pathTokens,
            final int totalTokensLength) {
        if (json.isDocument()) {
            // this might be the case of PATCHING an element array using the dot notation
            // e.g. object.array.2
            // if so, the array comes as an BsonDocument with all numberic keys
            // in any case, it might also be the object { "object": { "array": {"2": xxx }}}

            final boolean allNumbericKeys = checkAllKeysAreNumeric(json);

            if (allNumbericKeys) {
                return retrieveNestedProperties(json, pathTokens, totalTokensLength);
            }
        }

        return null;
    }

    private static boolean checkAllKeysAreNumeric(final BsonValue json) {
        return json.asDocument().keySet()
                .stream().allMatch(k -> {
                    try {
                        Integer.valueOf(k);
                        return true;
                    } catch (final NumberFormatException nfe) {
                        return false;
                    }
                });
    }

    private static List<Optional<BsonValue>> createOptionalListFromJson(final BsonValue json) {
        final var ret = new ArrayList<Optional<BsonValue>>();
        ret.add(Optional.ofNullable(json));
        return ret;
    }

    private static List<Optional<BsonValue>> createEmptyOptionalList() {
        final var ret = new ArrayList<Optional<BsonValue>>();
        ret.add(Optional.empty());
        return ret;
    }

    private static String extractPrimaryPathToken(final String[] pathTokens) {
        String pathToken;
        pathToken = pathTokens[0];

        if ("".equals(pathToken)) {
            throw new IllegalArgumentException(
                    "wrong path " + Arrays.toString(pathTokens) + " path tokens cannot be empty strings");
        }
        return pathToken;
    }

    private static List<Optional<BsonValue>> retrievePropertyFromDocument(final BsonValue json,
            final String[] pathTokens,
            final int totalTokensLength, final String pathToken) {
        if (json.isArray()) {
            throw new IllegalArgumentException("wrong path " + pathFromTokens(pathTokens) + " at token "
                    + pathToken + "; it should be '[*]'");
        } else if (json.isDocument()) {
            if (json.asDocument().containsKey(pathToken)) {
                return _getPropsFromPath(json.asDocument().get(pathToken), subpath(pathTokens),
                        totalTokensLength);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static List<Optional<BsonValue>> retrievePropertiesFromArray(final BsonValue json,
            final String[] pathTokens, final int totalTokensLength) {
        List<Optional<BsonValue>> nested;
        final var ret = new ArrayList<Optional<BsonValue>>();

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

    private static List<Optional<BsonValue>> retrieveNestedProperties(final BsonValue json, final String[] pathTokens,
            final int totalTokensLength) {
        List<Optional<BsonValue>> nested;
        final var ret = new ArrayList<Optional<BsonValue>>();

        for (final String key : json.asDocument().keySet()) {
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

    private static List<Optional<BsonValue>> retrievePropertiesFromDocument(final BsonValue json,
            final String[] pathTokens, final int totalTokensLength,
            final String pathToken) {
        if (!(json.isDocument())) {
            throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " at token " + pathToken
                    + "; it should be an object but found " + json.toString());
        }

        if (pathTokens.length != totalTokensLength) {
            throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " at token " + pathToken
                    + "; $ can only start the expression");
        }

        return _getPropsFromPath(json, subpath(pathTokens), totalTokensLength);
    }

    private static boolean comparePathTokens(boolean ret, final String[] leftPathTokens,
            final String[] rightPathTokens) {
        outerloop: for (int cont = 0; cont < leftPathTokens.length; cont++) {
            final var lt = leftPathTokens[cont];
            final var rt = rightPathTokens[cont];

            switch (lt) {
                case "*" -> {
                    // matches any key but not array indexes
                }
                case "[*]" -> {
                    try {
                        Integer.valueOf(rt);
                    } catch (final NumberFormatException nfe) {
                        ret = false;
                        break outerloop;
                    }
                }
                default -> {
                    ret = rt.equals(lt);
                    if (!ret) {
                        break outerloop;
                    }
                }
            }
        }
        return ret;
    }

    private static String pathFromTokens(final String[] pathTokens) {
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

    private static String[] subpath(final String[] pathTokens) {
        return Arrays.copyOfRange(pathTokens, 1, pathTokens.length, String[].class);
    }

    private static BsonValue getBsonValue(final String json) {
        return BsonDocument.parse("{'x':".concat(json).concat("}")).get("x");
    }

    private static boolean _containsUpdateOperators(final BsonDocument json,
            final boolean ignoreCurrentDate) {
        if (json == null) {
            return false;
        }

        return json.asDocument().keySet().stream()
                .filter(key -> !ignoreCurrentDate || !key.equals("$currentDate"))
                .anyMatch(key -> isUpdateOperator(key));
    }

    private static void flatten(final String prefix, final String key, final BsonDocument data,
            final BsonDocument set) {
        final var newPrefix = prefix == null
            ? key
            : prefix + "." + key;
        final var value = data.get(key);

        if (value.isDocument()) {
            value.asDocument().keySet()
                    .forEach(childKey -> flatten(newPrefix, childKey, value.asDocument(), set));
        } else {
            set.append(newPrefix, value);
        }
    }

    private static boolean _containsKeys(final BsonDocument doc, final Set<String> keys, final boolean all) {
        final var ufdoc = BsonUtils.unflatten(doc).asDocument();

        return all
            ? keys.stream().allMatch(key -> _containsKeys(ufdoc, key, all))
            : keys.stream().anyMatch(key -> _containsKeys(ufdoc, key, all));
    }

    private static boolean _containsKeys(final BsonDocument doc, final String key, final boolean all) {
        // let's check update operators first, since doc can look like:
        // {
        // <operator1>: { <field1>: <value1>, ... },
        // <operator2>: { <field2>: <value2>, ... },
        // ...
        // }

        if (BsonUtils.containsUpdateOperators(doc)) {
            // here we check if the doc contains the key in a update operator
            final var updateOperators = doc.keySet().stream().filter(k -> k.startsWith("$"))
                    .collect(Collectors.toList());

            final var checkInUO = updateOperators.stream()
                    .anyMatch(uo -> _containsKeys(BsonUtils.unflatten(doc.get(uo)).asDocument(), key, all));

            if (checkInUO) {
                return true;
            }
        }

        if (key.contains(".")) {
            final var first = key.substring(0, key.indexOf("."));
            if (!first.isEmpty() && doc.containsKey(first) && doc.get(first).isDocument()) {
                final var remaining = key.substring(key.indexOf(".") + 1);

                return _containsKeys(doc.get(first).asDocument(), remaining, all);
            } else if (!first.isEmpty() && doc.containsKey(first) && doc.get(first).isArray()) {
                final var remaining = key.substring(key.indexOf(".") + 1);

                return containsKeys(doc.get(first).asArray(), Sets.newHashSet(remaining), all);
            }

            return false;

        } else {
            return !key.isEmpty() && doc.containsKey(key);
        }
    }
}