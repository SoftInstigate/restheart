/*
 * RESTHeart - the data REST API server
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
     *
     * @param root the DBOject to extract properties from
     * @param path the path of the properties to extract
     * @return the List of Optional<Object>s extracted from root ojbect and
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

                        if (nested == null) {
                            ret.add(null);
                        } else {
                            ret.addAll(nested);
                        }
                    }

                    return ret;
                }
            case "[*]":
                if (!(json instanceof BasicDBList)) {
                    return null;
                } else {
                    ArrayList<Optional<Object>> ret = new ArrayList<>();

                    if (((BasicDBList) json).isEmpty()) {
                        if (pathTokens.length > 1) {
                            return null;
                        } else {
                            ret.add(Optional.of(json));
                        }
                    } else {
                        for (String key : ((BasicDBList) json).keySet()) {
                            nested = _getPropsFromPath(((BasicDBList) json).get(key), subpath(pathTokens), totalTokensLength);

                            if (nested == null) {
                                ret.add(null);
                            } else {
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

        return subpath.toArray(new String[0]);
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

}
