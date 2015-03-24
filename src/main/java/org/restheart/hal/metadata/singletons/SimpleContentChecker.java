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
import io.undertow.server.HttpServerExchange;
import java.util.List;
import org.restheart.handlers.RequestContext;
import org.restheart.utils.JsonUtils;
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
 * follows: { "path": "PATHEXPR", [ "type": "TYPE]"] ["count": COUNT ] ["regex":
 * "REGEX"]}
 *
 * where
 *
 * <br>PATHEXPR the path expression. use the . notation to identity the property
 * <br>COUNT is the number of expected values
 * <br>TYPE can be any BSON type: null, object, array, string, number, boolean *
 * objectid, date,timestamp, maxkey, minkey, symbol, code, objectid
 * <br>REGEX regular expression
 *
 * <br>examples of path expressions:
 *
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
 */
public class SimpleContentChecker implements Checker {
    static final Logger LOGGER = LoggerFactory.getLogger(SimpleContentChecker.class);

    @Override
    public boolean check(HttpServerExchange exchange, RequestContext context, DBObject args) {
        if (args instanceof BasicDBList) {
            BasicDBList conditions = (BasicDBList) args;

            return conditions.stream().allMatch(_condition -> {
                if (_condition instanceof BasicDBObject) {
                    BasicDBObject condition = (BasicDBObject) _condition;

                    String path = null;
                    Object _path = condition.get("path");

                    if (_path != null && _path instanceof String) {
                        path = (String) _path;
                    }

                    String type = null;
                    Object _type = condition.get("type");

                    int count = -1;
                    Object _count = condition.get("count");

                    if (_count != null && _type instanceof Integer) {
                        count = (Integer) count;
                    }

                    String regex = null;
                    Object _regex = condition.get("regex");

                    if (_regex != null && _regex instanceof String) {
                        regex = (String) _regex;
                    }

                    if (count < 0 && type == null && regex == null) {
                        context.addWarning("condition in the args list does not have any of 'count', 'type' and 'regex' properties, specify at least one: " + _condition);
                        return true;
                    }

                    if (path == null) {
                        context.addWarning("condition in the args list does not have the 'path' property: " + _condition);
                        return true;
                    }

                    if (type != null && count >= 0 && regex != null) {
                        return checkCount(context.getContent(), path, count) && checkType(context.getContent(), path, type) && checkRegex(context.getContent(), path, regex);
                    } else if (type != null && count >= 0) {
                        return checkCount(context.getContent(), path, count) && checkType(context.getContent(), path, type);
                    } else if (type != null && regex != null) {
                        return checkType(context.getContent(), path, type) && checkRegex(context.getContent(), path, regex);
                    }
                    if (count >= 0 && regex != null) {
                        return checkCount(context.getContent(), path, count) && checkRegex(context.getContent(), path, regex);
                    } else if (type != null) {
                        return checkType(context.getContent(), path, type);
                    } else if (count >= 0) {
                        return checkCount(context.getContent(), path, count);
                    } else if (regex != null) {
                        checkRegex(context.getContent(), path, regex);
                    } 
                    
                    return true;
                } else {
                    context.addWarning("property in the args list is not an object: " + _condition);
                    return true;
                }
            });
        } else {
            context.addWarning("checker wrong definition: args property must be an arrary of string property names.");
            return true;
        }
    }

    private boolean checkType(DBObject json, String path, String type) {
        BasicDBObject _json = (BasicDBObject) json;

        List<Object> props = JsonUtils.getPropsFromPath(_json, path);

        return props.stream().map((prop) -> JsonUtils.checkType(prop, type)).noneMatch((thisCheck) -> (!thisCheck));
    }

    private boolean checkCount(DBObject json, String path, int count) {
        return count == JsonUtils.countPropsFromPath(json, path);
    }

    private boolean checkRegex(Object value, String path, String regex) {
        String svalue = JsonUtils.serialize(value);

        return svalue.matches(regex);
    }
}
