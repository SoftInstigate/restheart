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

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSONSerializers;
import com.mongodb.util.ObjectSerializer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bson.types.BSONTimestamp;
import org.bson.types.Code;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.ObjectId;
import org.bson.types.Symbol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonUtils {

    static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);

    private static final ObjectSerializer serializer = JSONSerializers.getStrict();

    /**
     *
     * @param bson the BSON object to serialize
     * @return the JSON strict mode representation of the BSON object
     *
     */
    public static String serialize(Object bson) {
        return serializer.serialize(bson);
    }

    /**
     * replaces the underscore prefixed keys (eg _$exists) with the
     * corresponding key (eg $exists). This is needed because MongoDB does not
     * allow to store keys that starts with $.
     *
     * @param obj
     * @return the json object where the underscore prefixed keys are replaced
     * with the corresponding keys
     */
    public static Object unescapeKeys(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof BasicDBObject) {
            BasicDBObject ret = new BasicDBObject();

            ((BasicDBObject) obj).keySet().stream().forEach(k -> {
                String newKey = k.startsWith("_$") ? k.substring(1) : k;
                Object value = ((BasicDBObject) obj).get(k);

                if (value instanceof BasicDBObject) {
                    ret.put(newKey,
                            unescapeKeys((BasicDBObject) value));
                } else if (value instanceof BasicDBList) {
                    BasicDBList newList = new BasicDBList();

                    ((BasicDBList) value).stream().forEach(v -> {
                        newList.add(unescapeKeys(v));
                    });

                    ret.put(newKey, newList);
                } else {
                    ret.put(newKey, unescapeKeys(value));
                }

            });

            return ret;
        } else if (obj instanceof BasicDBList) {
            BasicDBList ret = new BasicDBList();

            ((BasicDBList) obj).stream().forEach(value -> {
                if (value instanceof BasicDBObject) {
                    ret.add(unescapeKeys((BasicDBObject) value));
                } else if (value instanceof BasicDBList) {
                    BasicDBList newList = new BasicDBList();

                    ((BasicDBList) value).stream().forEach(v -> {
                        newList.add(unescapeKeys(v));
                    });

                    ret.add(newList);
                } else {
                    ret.add(unescapeKeys(value));
                }

            });

            return ret;
        } else if (obj instanceof String) {
            return ((String) obj)
                    .startsWith("_$") ? ((String) obj).substring(1) : obj;
        } else {
            return obj;
        }
    }

    /**
     * replaces the dollar prefixed keys (eg $exists) with the corresponding
     * underscore prefixed key (eg _$exists). This is needed because MongoDB
     * does not allow to store keys that starts with $.
     *
     * @param obj
     * @return the json object where the underscore prefixed keys are replaced
     * with the corresponding keys
     */
    public static Object escapeKeys(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof BasicDBObject) {
            BasicDBObject ret = new BasicDBObject();

            ((BasicDBObject) obj).keySet().stream().forEach(k -> {
                String newKey = k.startsWith("$") ? "_" + k : k;
                Object value = ((BasicDBObject) obj).get(k);

                if (value instanceof BasicDBObject) {
                    ret.put(newKey,
                            escapeKeys((BasicDBObject) value));
                } else if (value instanceof BasicDBList) {
                    BasicDBList newList = new BasicDBList();

                    ((BasicDBList) value).stream().forEach(v -> {
                        newList.add(escapeKeys(v));
                    });

                    ret.put(newKey, newList);
                } else {
                    ret.put(newKey, escapeKeys(value));
                }

            });

            return ret;
        } else if (obj instanceof BasicDBList) {
            BasicDBList ret = new BasicDBList();

            ((BasicDBList) obj).stream().forEach(value -> {
                if (value instanceof BasicDBObject) {
                    ret.add(escapeKeys((BasicDBObject) value));
                } else if (value instanceof BasicDBList) {
                    BasicDBList newList = new BasicDBList();

                    ((BasicDBList) value).stream().forEach(v -> {
                        newList.add(escapeKeys(v));
                    });

                    ret.add(newList);
                } else {
                    ret.add(escapeKeys(value));
                }

            });

            return ret;
        } else if (obj instanceof String) {
            return ((String) obj)
                    .startsWith("$") ? "_" + ((String) obj) : obj;
        } else {
            return obj;
        }
    }

    /**
     *
     * @param root the DBOject to extract properties from
     * @param path the path of the properties to extract
     * @return the List of Optional&lt;Object&gt;s extracted from root ojbect and
     * identified by the path or null if path does not exist
     *
     * @see org.restheart.test.unit.JsonUtilsTest form code examples
     *
     */
    public static List<Optional<Object>> getPropsFromPath(Object root, String path) throws IllegalArgumentException {
        String pathTokens[] = path.split(Pattern.quote("."));

        if (pathTokens == null || pathTokens.length == 0 || !pathTokens[0].equals("$")) {
            throw new IllegalArgumentException("wrong path. it must use the . notation and start with $");
        } else if (!(root instanceof BasicDBObject)) {
            throw new IllegalArgumentException("wrong json. it must be an object");
        } else {
            return _getPropsFromPath(root, pathTokens, pathTokens.length);
        }
    }

    private static List<Optional<Object>> _getPropsFromPath(Object json, String[] pathTokens, int totalTokensLength) throws IllegalArgumentException {
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
        } else {
            ArrayList<Optional<Object>> ret = new ArrayList<>();
            ret.add(Optional.ofNullable(json));
            return ret;
        }

        List<Optional<Object>> nested;

        switch (pathToken) {
            case "$":
                if (!(json instanceof BasicDBObject)) {
                    throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " at token " + pathToken + "; it should be an object but found " + serializer.serialize(json));
                }

                if (pathTokens.length != totalTokensLength) {
                    throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " at token " + pathToken + "; $ can only start the expression");
                }

                return _getPropsFromPath(json, subpath(pathTokens), totalTokensLength);
            case "*":
                if (!(json instanceof BasicDBObject)) {
                    return null;
                } else {
                    ArrayList<Optional<Object>> ret = new ArrayList<>();

                    for (String key : ((BasicDBObject) json).keySet()) {
                        nested = _getPropsFromPath(((BasicDBObject) json).get(key), subpath(pathTokens), totalTokensLength);

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
                if (!(json instanceof BasicDBList)) {
                    if (json instanceof BasicDBObject) {
                        // this might be the case of PATCHING an element array using the dot notation
                        // e.g. object.array.2
                        // if so, the array comes as an BasicDBObject with all numberic keys
                        // in any case, it might also be the object { "object": { "array": {"2": xxx }}}

                        boolean allNumbericKeys = ((BasicDBObject) json).keySet().stream().allMatch(k -> {
                            try {
                                Integer.parseInt(k);
                                return true;
                            } catch (NumberFormatException nfe) {
                                return false;
                            }
                        });

                        if (allNumbericKeys) {
                            ArrayList<Optional<Object>> ret = new ArrayList<>();

                            for (String key : ((BasicDBObject) json).keySet()) {
                                nested = _getPropsFromPath(((BasicDBObject) json).get(key), subpath(pathTokens), totalTokensLength);

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
                    ArrayList<Optional<Object>> ret = new ArrayList<>();

                    if (!((BasicDBList) json).isEmpty()) {
                        for (String key : ((BasicDBList) json).keySet()) {
                            nested = _getPropsFromPath(((BasicDBList) json).get(key), subpath(pathTokens), totalTokensLength);

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
                if (json instanceof BasicDBList) {
                    throw new IllegalArgumentException("wrong path " + pathFromTokens(pathTokens) + " at token " + pathToken + "; it should be '[*]'");
                } else if (json instanceof BasicDBObject) {
                    if (((DBObject) json).containsField(pathToken)) {
                        return _getPropsFromPath(((DBObject) json).get(pathToken), subpath(pathTokens), totalTokensLength);
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
    public static Integer countPropsFromPath(Object root, String path) throws IllegalArgumentException {
        List<Optional<Object>> items = getPropsFromPath(root, path);

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

    public static boolean checkType(Optional<Object> o, String type) {
        if (!o.isPresent() && !"null".equals(type) && !"notnull".equals(type)) {
            return false;
        }

        switch (type.toLowerCase().trim()) {
            case "null":
                return !o.isPresent();
            case "notnull":
                return o.isPresent();
            case "object":
                return o.get() instanceof BasicDBObject;
            case "array":
                return o.get() instanceof BasicDBList;
            case "string":
                return o.get() instanceof String;
            case "number":
                return o.get() instanceof Number;
            case "boolean":
                return o.get() instanceof Boolean;
            case "objectid":
                return o.get() instanceof ObjectId;
            case "objectidstring":
                return o.get() instanceof String && ObjectId.isValid((String) o.get());
            case "date":
                return o.get() instanceof Date;
            case "timestamp":
                return o.get() instanceof BSONTimestamp;
            case "maxkey":
                return o.get() instanceof MaxKey;
            case "minkey":
                return o.get() instanceof MinKey;
            case "symbol":
                return o.get() instanceof Symbol;
            case "code":
                return o.get() instanceof Code;
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
            String cc = jsonString.substring(i, Math.min(i + 2, jsonString.length()));

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

    private JsonUtils() {
    }

}
