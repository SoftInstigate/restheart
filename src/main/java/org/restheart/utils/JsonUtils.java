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
import java.util.Set;
import java.util.regex.Pattern;
import org.bson.types.BSONTimestamp;
import org.bson.types.Code;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.ObjectId;
import org.bson.types.Symbol;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
public class JsonUtils {
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
     * @return the List of Objects extracted from root ojbect and identifie by
     * the path or null if path does not exist
     *
     * examples
     * <br>root = {a: {b:1, c: {d:2, e:3}}, f:4}
     * <br> a -> {b:1, c: {d:2, e:3}}, f:4}
     * <br> a.b -> 1
     * <br> a.c -> {d:2,e:3}
     * <br> a.c.d -> 2
     *
     * <br>root = {a: [{b:1}, {c:2,d:3}}, true]}
     *
     * <br>a -> [{b:1}, {c:2,d:3}, true]
     * <br>a.[*] -> {b:1}, {c:2,d:3}, true
     * <br>a.[*].c -> null, 2, null
     *
     *
     * <br>root = {a: [{b:1}, {b:2}, {b:3}]}"
     *
     * <br>*.a.[*].b -> [1,2,3]
     *
     * @see org.restheart.test.unit.JsonUtilsTest form code examples
     *
     */
    public static List<Object> getPropsFromPath(Object root, String path) throws IllegalArgumentException {
        String pathTokens[] = path.split(Pattern.quote("."));

        if (pathTokens == null || pathTokens.length == 0 || !pathTokens[0].equals("$")) {
            throw new IllegalArgumentException("wrong path. it must use the . notation and start with $");
        } else if (!(root instanceof BasicDBObject)) {
            throw new IllegalArgumentException("wrong json. it must be an object");
        } else {
            return _getPropsFromPath(root, pathTokens, pathTokens.length);
        }
    }

    private static List<Object> _getPropsFromPath(Object json, String[] pathTokens, int totalTokensLength) throws IllegalArgumentException {
        if (pathTokens == null) {
            throw new IllegalArgumentException("pathTokens argument cannot be null");
        }

        ArrayList<Object> ret = new ArrayList<>();

        String pathToken;

        if (pathTokens.length > 0) {
            pathToken = pathTokens[0];
        } else {
            ret.add(json);
            return ret;
        }

        if (pathToken.equals("$")) {
            if (!(json instanceof BasicDBObject)) {
                throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " at token " + pathToken + "; it should be an object but found " + serializer.serialize(json));
            }

            if (pathTokens.length != totalTokensLength) {
                throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " at token " + pathToken + "; $ can only start the expression");
            }

            List<Object> nested = _getPropsFromPath(json, subpath(pathTokens), totalTokensLength);

            if (nested == null) {
                return null;
            } else {
                ret.addAll(nested);
            }
        } else if (pathToken.equals("*")) {
            if (json instanceof BasicDBObject) {
                for (String key : ((BasicDBObject) json).keySet()) {
                    List<Object> nested = _getPropsFromPath(((BasicDBObject) json).get(key), subpath(pathTokens), totalTokensLength);

                    if (nested != null) {
                        if (nested.size() > 0 && pathTokens.length == 1) {
                            BasicDBObject toadd = new BasicDBObject(key, nested.get(0));

                            ret.add(toadd);
                        } else {
                            ret.addAll(nested);
                        }
                    }
                }
            }
        } else if (pathToken.equals("[*]")) {
            if (!(json instanceof BasicDBList)) {
                throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " at token " + pathToken + "; it should be a list " + "but found " + serializer.serialize(json));
            }

            if (((BasicDBList) json).isEmpty()) {
                if (pathTokens.length > 1) {
                    // the array is empty and pathTokens.length > 1 (still tokens to process)
                    return null;
                } else {
                    // return an emtpy the list
                    return new ArrayList<>();
                }
            } else {
                for (String key : ((BasicDBList) json).keySet()) {
                    List<Object> nested = _getPropsFromPath(((BasicDBList) json).get(key), subpath(pathTokens), totalTokensLength);

                    if (nested != null) {
                        ret.addAll(nested);
                    }
                }
            }
        } else {
            if (json instanceof BasicDBList) {
                throw new IllegalArgumentException("wrong path " + pathFromTokens(pathTokens) + " at token " + pathToken + "; it should be '[*]'");
            } else if (json instanceof BasicDBObject) {
                if (((DBObject) json).containsField(pathToken)) {
                    List<Object> nested = _getPropsFromPath(((DBObject) json).get(pathToken), subpath(pathTokens), totalTokensLength);

                    if (nested == null) {
                        return null;
                    } else {
                        ret.addAll(nested);
                    }
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        return ret;
    }

    /**
     * alias method for rootcountPropsFromPath(Object root, String path, true)
     * 
     * @param root
     * @param path
     * @return then number of properties identitified by the json path expression or null if path does not exist
     * @throws IllegalArgumentException 
     */
    public static Integer countPropsFromPath(Object root, String path) throws IllegalArgumentException {
        return countPropsFromPath(root, path, true);
    }

    /**
     * @param root
     * @param path
     * @param distinct
     * @return then number of properties identitified by the json path expression or null if path does not exist
     * @throws IllegalArgumentException 
     */
    public static Integer countPropsFromPath(Object root, String path, boolean distinct) throws IllegalArgumentException {
        List<Object> items = getPropsFromPath(root, path);
        
        if (items == null) {
            return null;
        }

        if (!distinct) {
            return items.size();
        } else {
            int ret = 0;

            Set<String> propsKeys = new HashSet<>();

            for (Object item : items) {
                if (item instanceof BasicDBList) {
                    ret = ret + 1;
                } else if (item instanceof BasicDBObject) {
                    propsKeys.addAll(((BasicDBObject) item).keySet());
                } else {
                    ret++;
                }
            }

            return ret + propsKeys.size();
        }
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

    public static boolean checkType(Object o, String type) {
        switch (type.toLowerCase().trim()) {
            case "null":
                return o == null;
            case "notnull":
                return o != null;
            case "object":
                return o instanceof BasicDBObject;
            case "array":
                return o instanceof BasicDBList;
            case "string":
                return o instanceof String;
            case "number":
                return o instanceof Number;
            case "boolean":
                return o instanceof Boolean;
            case "objectid":
                return o instanceof ObjectId;
            case "date":
                return o instanceof Date;
            case "timestamp":
                return o instanceof BSONTimestamp;
            case "maxkey":
                return o instanceof MaxKey;
            case "minkey":
                return o instanceof MinKey;
            case "symbol":
                return o instanceof Symbol;
            case "code":
                return o instanceof Code;
            default:
                return false;
        }
    }

}
