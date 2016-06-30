/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInvalidOperationException;
import org.bson.BsonJavaScript;
import org.bson.BsonMaxKey;
import org.bson.BsonMinKey;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.codecs.BsonArrayCodec;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.json.JsonParseException;
import org.bson.json.JsonReader;
import org.bson.types.ObjectId;
import org.restheart.hal.UnsupportedDocumentIdException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class JsonUtils {

    static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);

    private static final BsonArrayCodec BSON_ARRAY_CODEC = new BsonArrayCodec(
            CodecRegistries.fromProviders(
                    new BsonValueCodecProvider()));

    private static final String ESCAPED_$ = "_$";
    private static final String ESCAPED_DOT = "::";

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
    public static BsonValue unescapeKeys(BsonValue json) {
        if (json == null) {
            return null;
        }

        if (json.isDocument()) {
            BsonDocument ret = new BsonDocument();

            json.asDocument().keySet().stream().forEach(k -> {
                String newKey = k.startsWith(ESCAPED_$) ? k.substring(1) : k;
                newKey = newKey.replaceAll(ESCAPED_DOT, ".");

                BsonValue value = json.asDocument().get(k);

                if (value.isDocument()) {
                    ret.put(newKey, unescapeKeys(value));
                } else if (value.isArray()) {
                    BsonArray newList = new BsonArray();

                    value.asArray().stream().forEach(v -> {
                        newList.add(unescapeKeys(v));
                    });

                    ret.put(newKey, newList);
                } else {
                    ret.put(newKey, unescapeKeys(value));
                }

            });

            return ret;
        } else if (json.isArray()) {
            BsonArray ret = new BsonArray();

            json.asArray().stream().forEach(value -> {
                if (value.isDocument()) {
                    ret.add(unescapeKeys(value));
                } else if (value.isArray()) {
                    BsonArray newList = new BsonArray();

                    value.asArray().stream().forEach(v -> {
                        newList.add(unescapeKeys(v));
                    });

                    ret.add(newList);
                } else {
                    ret.add(unescapeKeys(value));
                }

            });

            return ret;
        } else if (json.isString()) {
            return json.asString().getValue().startsWith(ESCAPED_$)
                    ? new BsonString(json.asString().getValue().substring(1))
                    : json;
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
     * @return the json object where the underscore prefixed keys are replaced
     * with the corresponding keys
     */
    public static BsonValue escapeKeys(BsonValue json, boolean escapeDots) {
        if (json == null) {
            return null;
        }

        if (json.isDocument()) {
            BsonDocument ret = new BsonDocument();

            json.asDocument().keySet().stream().forEach(k -> {
                String newKey = k.startsWith("$") ? "_" + k : k;

                if (escapeDots) {
                    newKey = newKey.replaceAll("\\.", ESCAPED_DOT);
                }

                BsonValue value = json.asDocument().get(k);

                if (value.isDocument()) {
                    ret.put(newKey, escapeKeys(value, escapeDots));
                } else if (value.isArray()) {
                    BsonArray newList = new BsonArray();

                    value.asArray().stream().forEach(v -> {
                        newList.add(escapeKeys(v, escapeDots));
                    });

                    ret.put(newKey, newList);
                } else {
                    ret.put(newKey, value);
                }

            });

            return ret;
        } else if (json.isArray()) {
            BsonArray ret = new BsonArray();

            json.asArray().stream().forEach(value -> {
                if (value.isDocument()) {
                    ret.add(escapeKeys(value, escapeDots));
                } else if (value.isArray()) {
                    BsonArray newList = new BsonArray();

                    value.asArray().stream().forEach(v -> {
                        newList.add(escapeKeys(v, escapeDots));
                    });

                    ret.add(newList);
                } else {
                    ret.add(value);
                }

            });

            return ret;
        } else if (json.isString()) {
            return json.asString().getValue().startsWith("$")
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
     * @see org.restheart.test.unit.JsonUtilsTest form code examples
     *
     */
    public static List<Optional<BsonValue>> getPropsFromPath(
            BsonValue root,
            String path)
            throws IllegalArgumentException {
        String pathTokens[] = path.split(Pattern.quote("."));

        if (pathTokens == null
                || pathTokens.length == 0
                || !pathTokens[0].equals("$")) {
            throw new IllegalArgumentException(
                    "wrong path. it must use the . notation and start with $");
        } else if (!(root instanceof BsonDocument)) {
            throw new IllegalArgumentException(
                    "wrong json. it must be an object");
        } else {
            return _getPropsFromPath(root, pathTokens, pathTokens.length);
        }
    }

    private static List<Optional<BsonValue>> _getPropsFromPath(
            BsonValue json,
            String[] pathTokens,
            int totalTokensLength)
            throws IllegalArgumentException {
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
                    throw new IllegalArgumentException("wrong path "
                            + Arrays.toString(pathTokens)
                            + " path tokens cannot be empty strings");
                }
            }
        } else if (json.isNull()){
            // if value is null return an empty optional
            ArrayList<Optional<BsonValue>> ret = new ArrayList<>();
            ret.add(Optional.empty());
            return ret;
        } else {
            ArrayList<Optional<BsonValue>> ret = new ArrayList<>();
            ret.add(Optional.ofNullable(json));
            return ret;
        }

        List<Optional<BsonValue>> nested;

        switch (pathToken) {
            case "$":
                if (!(json.isDocument())) {
                    throw new IllegalArgumentException("wrong path "
                            + Arrays.toString(pathTokens)
                            + " at token "
                            + pathToken
                            + "; it should be an object but found "
                            + json.toString());
                }

                if (pathTokens.length != totalTokensLength) {
                    throw new IllegalArgumentException("wrong path "
                            + Arrays.toString(pathTokens)
                            + " at token "
                            + pathToken
                            + "; $ can only start the expression");
                }

                return _getPropsFromPath(
                        json,
                        subpath(pathTokens),
                        totalTokensLength);
            case "*":
                if (!(json.isDocument())) {
                    return null;
                } else {
                    ArrayList<Optional<BsonValue>> ret = new ArrayList<>();

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
            case "[*]":
                if (!(json.isArray())) {
                    if (json.isDocument()) {
                        // this might be the case of PATCHING an element array using the dot notation
                        // e.g. object.array.2
                        // if so, the array comes as an BsonDocument with all numberic keys
                        // in any case, it might also be the object { "object": { "array": {"2": xxx }}}

                        boolean allNumbericKeys = json.asDocument().keySet()
                                .stream().allMatch(k -> {
                                    try {
                                        Integer.parseInt(k);
                                        return true;
                                    } catch (NumberFormatException nfe) {
                                        return false;
                                    }
                                });

                        if (allNumbericKeys) {
                            ArrayList<Optional<BsonValue>> ret = new ArrayList<>();

                            for (String key : json.asDocument().keySet()) {
                                nested = _getPropsFromPath(
                                        json.asDocument().get(key),
                                        subpath(pathTokens),
                                        totalTokensLength);

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
                    ArrayList<Optional<BsonValue>> ret = new ArrayList<>();

                    if (!json.asArray().isEmpty()) {
                        for (int index = 0; index < json.asArray().size(); index++) {
                            nested = _getPropsFromPath(
                                    json.asArray().get(index),
                                    subpath(pathTokens),
                                    totalTokensLength);

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
            default:
                if (json.isArray()) {
                    throw new IllegalArgumentException("wrong path "
                            + pathFromTokens(pathTokens)
                            + " at token "
                            + pathToken
                            + "; it should be '[*]'");
                } else if (json.isDocument()) {
                    if (json.asDocument().containsKey(pathToken)) {
                        return _getPropsFromPath(
                                json.asDocument().get(pathToken),
                                subpath(pathTokens),
                                totalTokensLength);
                    } else {
                        return null;
                    }
                } else {
                    return null;
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
     * examples: ($, $.a) -> true, ($.a, $.b) -> false, ($.*, $.a) -> true,
     * ($.a.[*].c, $.a.0.c) -> true, ($.a.[*], $.a.b) -> false
     *
     */
    public static boolean isAncestorPath(final String left, final String right) {
        if (left == null || !left.startsWith("$")) {
            throw new IllegalArgumentException("wrong left path: " + left);
        }
        if (right == null || !right.startsWith("$")) {
            throw new IllegalArgumentException("wrong right path: " + right);
        }

        boolean ret = true;

        if (!right.startsWith(left)) {
            String leftPathTokens[] = left.split(Pattern.quote("."));
            String rightPathTokens[] = right.split(Pattern.quote("."));

            if (leftPathTokens.length > rightPathTokens.length) {
                ret = false;
            } else {
                outerloop:
                for (int cont = 0; cont < leftPathTokens.length; cont++) {
                    String lt = leftPathTokens[cont];
                    String rt = rightPathTokens[cont];

                    switch (lt) {
                        case "*":
                            break;
                        case "[*]":
                            try {
                                Integer.parseInt(rt);
                                break;
                            } catch (NumberFormatException nfe) {
                                ret = false;
                                break outerloop;
                            }
                        default:
                            ret = rt.equals(lt);

                            if (!ret) {
                                break outerloop;
                            } else {
                                break;
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
    public static Integer countPropsFromPath(BsonValue root, String path)
            throws IllegalArgumentException {
        List<Optional<BsonValue>> items = getPropsFromPath(root, path);

        if (items == null) {
            return null;
        }

        return items.size();
    }

    private static String pathFromTokens(String[] pathTokens) {
        if (pathTokens == null) {
            return null;
        }

        String ret = "";

        for (int cont = 1; cont < pathTokens.length; cont++) {
            ret = ret.concat(pathTokens[cont]);

            if (cont < pathTokens.length - 1) {
                ret = ret.concat(".");
            }
        }

        return ret;
    }

    private static String[] subpath(String[] pathTokens) {
        ArrayList<String> subpath = new ArrayList<>();

        for (int cont = 1; cont < pathTokens.length; cont++) {
            subpath.add(pathTokens[cont]);
        }

        return subpath.toArray(new String[subpath.size()]);
    }

    public static boolean checkType(Optional<BsonValue> o, String type) {
        if (!o.isPresent() && !"null".equals(type) && !"notnull".equals(type)) {
            return false;
        }

        switch (type.toLowerCase().trim()) {
            case "null":
                return !o.isPresent();
            case "notnull":
                return o.isPresent();
            case "object":
                return o.get().isDocument();
            case "array":
                return o.get().isArray();
            case "string":
                return o.get().isString();
            case "number":
                return o.get().isNumber();
            case "boolean":
                return o.get().isBoolean();
            case "objectid":
                return o.get().isObjectId();
            case "objectidstring":
                return o.get().isString()
                        && ObjectId.isValid(o.get().asString().getValue());
            case "date":
                return o.get().isDateTime();
            case "timestamp":
                return o.get().isTimestamp();
            case "maxkey":
                return o.get() instanceof BsonMaxKey;
            case "minkey":
                return o.get() instanceof BsonMinKey;
            case "symbol":
                return o.get().isSymbol();
            case "code":
                return o.get() instanceof BsonJavaScript;
            default:
                return false;
        }
    }

    /**
     * @author Stefan Reich http://tinybrain.de/
     * @see http://tinybrain.de:8080/jsonminify/
     * @param jsonString
     * @return
     */
    public static String minify(String jsonString) {
        boolean in_string = false;
        boolean in_multiline_comment = false;
        boolean in_singleline_comment = false;
        char string_opener = 'x'; // unused value, just something that makes compiler happy

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < jsonString.length(); i++) {
            // get next (c) and next-next character (cc)

            char c = jsonString.charAt(i);
            String cc = jsonString.substring(i, 
                    Math.min(i + 2, jsonString.length()));

            // big switch is by what mode we're in (in_string etc.)
            if (in_string) {
                if (c == string_opener) {
                    in_string = false;
                    out.append(c);
                } else if (c == '\\') { // no special treatment needed for \\u, it just works like this too
                    out.append(cc);
                    ++i;
                } else {
                    out.append(c);
                }
            } else if (in_singleline_comment) {
                if (c == '\r' || c == '\n') {
                    in_singleline_comment = false;
                }
            } else if (in_multiline_comment) {
                if (cc.equals("*/")) {
                    in_multiline_comment = false;
                    ++i;
                }
            } else // we're outside of the special modes, so look for mode openers (comment start, string start)
             if (cc.equals("/*")) {
                    in_multiline_comment = true;
                    ++i;
                } else if (cc.equals("//")) {
                    in_singleline_comment = true;
                    ++i;
                } else if (c == '"' || c == '\'') {
                    in_string = true;
                    string_opener = c;
                    out.append(c);
                } else if (!Character.isWhitespace(c)) {
                    out.append(c);
                }
        }
        return out.toString();
    }

    /**
     * @param json
     * @return either a BsonDocument or a BsonArray from the json string
     * @throws JsonParseException
     */
    public static BsonValue parse(String json)
            throws JsonParseException {
        if (json == null) {
            return null;
        }

        String trimmed = json.trim();

        if (trimmed.startsWith("{")) {
            try {
                return BsonDocument.parse(json);
            } catch (BsonInvalidOperationException ex) {
                // this can happen parsing a bson type, e.g.
                // {"$oid": "xxxxxxxx" }
                // the string starts with { but is not a document
                return getBsonValue(json);
            }
        } else if (trimmed.startsWith("[")) {
            return BSON_ARRAY_CODEC.decode(
                    new JsonReader(json),
                    DecoderContext.builder().build());
        } else {
            return getBsonValue(json);
        }
    }

    private static BsonValue getBsonValue(String json) {
        String _json = "{'x':"
                .concat(json)
                .concat("}");

        return BsonDocument
                .parse(_json)
                .get("x");
    }

    /**
     * @param bson either a BsonDocument or a BsonArray
     * @return the minified string representation of the bson value
     * @throws IllegalArgumentException if bson is not a BsonDocument or a
     * BsonArray
     */
    public static String toJson(BsonValue bson) {
        if (bson == null) {
            return null;
        }

        /**
         * Gets a JSON representation of this document using the given
         * {@code JsonWriterSettings}.
         *
         * @param settings the JSON writer settings
         * @return a JSON representation of this document
         */
        if (bson.isDocument()) {
            return minify(bson.asDocument().toJson());
        } else if (bson.isArray()) {
            BsonArray _array = bson.asArray();

            BsonDocument wrappedArray = new BsonDocument("wrapped", _array);

            String json = wrappedArray.toJson();

            json = minify(json);
            json = json.substring(0, json.length() - 1); // removes closing }
            json = json.replaceFirst("\\{", "");
            json = json.replaceFirst("\"wrapped\"", "");
            json = json.replaceFirst(":", "");

            return json;
        } else {
            BsonDocument doc = new BsonDocument("x", bson);
            
            String ret = doc.toJson();
            
            ret = ret.replaceFirst("\\{", "");
            ret = ret.replaceFirst("\"x\"", "");
            ret = ret.replaceFirst(":", "");
            int index = ret.lastIndexOf("}");
            ret = ret.substring(0, index);
            
            return ret;
        }
    }
    
    public static String getIdAsString(BsonValue id)
            throws UnsupportedDocumentIdException {
        if (id == null) {
            return null;
        } else if (id.isString()) {
            return "'" + id.asString().getValue() + "'";
        }  else if (id.isObjectId()) {
            return id.asObjectId().getValue().toString();
        } else {
            return JsonUtils.minify(JsonUtils.toJson(id)
                    .replace("\"", "'"));
        }
    }
}
