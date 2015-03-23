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
package org.restheart.hal.metadata.singletons;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSONSerializers;
import com.mongodb.util.ObjectSerializer;
import io.undertow.server.HttpServerExchange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.types.BSONTimestamp;
import org.bson.types.Code;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.ObjectId;
import org.bson.types.Symbol;
import org.restheart.handlers.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 *
 * SimpleContentChecker allows to check request content by using json path
 * expression
 *
 * the args arguments is an array of condition. a condition is json object as
 * follows: { path: "[pathexpr]", type: "[type]" }
 *
 * where
 *
 * [pathexpr] use the . notation to identity the property, examples
 *
 * {a: {b:1, c: {d:2,e:3}}, f:4} a -> {b:1, c: {d:2,e:3}}, f:4} a.b -> 1 a.c ->
 * {d:2,e:3} a.c.d -> 2
 *
 * {a: [{b:1}, {d:1,e:1}}, true]}
 *
 * a -> [{b:1,c:2}, {b:3,c:4}, {b:true}] a.[*] -> {b:1,c:2}, {b:3,c:4}, true
 * a.[*].c -> 2, 4, null
 *
 * a -> [{b:1,c:2}, {b:3,c:4}, true] a.[*] -> {b:1,c:2}, {b:3,c:4}, true a.[*].c
 * -> exception, third element of array is not an object
 *
 * [type] can be any BSON type: null, object, array, string, number, boolean
 * objectid, date,timestamp, maxkey, minkey, symbol, code, objectid
 *
 */
public class SimpleContentChecker implements Checker {
    static final Logger LOGGER = LoggerFactory.getLogger(SimpleContentChecker.class);

    private static final ObjectSerializer serializer = JSONSerializers.getStrict();

    @Override
    public boolean check(HttpServerExchange exchange, RequestContext context, DBObject args) {
        if (args instanceof BasicDBList) {
            BasicDBList conditions = (BasicDBList) args;

            return conditions.stream().allMatch(_condition -> {
                if (_condition instanceof BasicDBObject) {
                    BasicDBObject condition = (BasicDBObject) _condition;

                    String path = null;
                    Object _path = condition.get("path");

                    if (_path == null || !(_path instanceof String)) {
                        context.addWarning("condition in the args list missing the string path property: " + _condition);
                    } else {
                        path = (String) _path;
                    }

                    String type = null;
                    Object _type = condition.get("type");

                    if (_type == null || !(_type instanceof String)) {
                        context.addWarning("condition in the args list missing the string type property: " + _condition);
                    } else {
                        type = (String) _type;
                    }

                    if (path != null && type != null) {
                        return check(context.getContent(), path, type);
                    } else {
                        return false;
                    }
                } else {
                    context.addWarning("property in the args list is not an object: " + _condition);
                    return false;
                }
            });
        } else {
            context.addWarning("transformer wrong definition: args property must be an arrary of string property names.");
            return false;
        }
    }

    private boolean check(DBObject json, String path, String type) {
        if (json instanceof BasicDBObject) {
            LOGGER.debug("the content is not a json object: " + json.getClass().getSimpleName());
            return false;
        }

        BasicDBObject _json = (BasicDBObject) json;

        List<Object> props = getPropsFromPath(_json, path);

        return props.stream().map((prop) -> checkType(prop, type)).noneMatch((thisCheck) -> (!thisCheck));
    }

    public static List<Object> getPropsFromPath(Object root, String path) throws IllegalArgumentException {
        String pathTokens[] = path.split(Pattern.quote("."));

        if (pathTokens == null || pathTokens.length == 0 || !pathTokens[0].equals("*")) {
            throw new IllegalArgumentException("wrong path. it must use the . notation and start with *");
        } else if (!(root instanceof BasicDBObject)) {
            throw new IllegalArgumentException("wrong json. it must be an object");
        } else {
            return _getPropsFromPath(root, path.split(Pattern.quote(".")));
        }
    }

    public static List<Object> _getPropsFromPath(Object json, String[] pathTokens) throws IllegalArgumentException {
        if (json == null) {
            throw new IllegalArgumentException("json argument cannot be null");
        }

        if (pathTokens == null) {
            throw new IllegalArgumentException("pathTokens argument cannot be null");
        }

        ArrayList<Object> ret = new ArrayList<>();

        String pathToken;

        if (pathTokens.length > 0) {
            pathToken = pathTokens[0];
        } else {
            pathToken = null;
        }

        if (pathToken == null) {
            ret.add(json);
        } else if (pathToken.equals("*")) {
            if (!(json instanceof BasicDBObject)) {
                throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " at token " + pathToken + "; it should be an object but found " + (json == null ? "null" : serializer.serialize(json)));
            }

            ret.addAll(_getPropsFromPath(json, subpath(pathTokens)));
        } else if (pathToken.equals("[*]")) {
            if (!(json instanceof BasicDBList)) {
                throw new IllegalArgumentException("wrong path " + Arrays.toString(pathTokens) + " at token " + pathToken + "; it should be a list " + "but found " + (json == null ? "null" : serializer.serialize(json)));
            }

            for (String key : ((BasicDBList) json).keySet()) {
                ret.addAll(_getPropsFromPath(((BasicDBList) json).get(key), subpath(pathTokens)));
            }
        } else {
            if (json instanceof BasicDBList) {
                throw new IllegalArgumentException("wrong path " + pathFromTokens(pathTokens) + " at token " + pathToken + "; it should be '[*]'");
            } else if (json instanceof BasicDBObject) {
                ret.addAll(_getPropsFromPath(((DBObject) json).get(pathToken), subpath(pathTokens)));
            } else {
                throw new IllegalArgumentException("wrong path " + pathFromTokens(pathTokens) + " at token " + pathToken + "; reached leaf but path tokens remaining" + (json == null ? "null" : serializer.serialize(json)));
            }
        }

        return ret;
    }

    private static String pathFromTokens(String[] pathTokens) {
        if (pathTokens == null)
            return null;
        
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

    private boolean checkType(Object o, String type) {
        switch (type) {
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
