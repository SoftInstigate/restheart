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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
 * "REGEX"] ["nullable": BOOLEAN]}
 *
 * where
 *
 * <br>PATHEXPR the path expression. use the . notation to identity the property
 * <br>COUNT is the number of expected values
 * <br>TYPE can be any BSON type: null, object, array, string, number, boolean *
 * objectid, date,timestamp, maxkey, minkey, symbol, code, objectid
 * <br>REGEX regular expression. note that string values to match come enclosed
 * in quotation marks, i.e. the regex will need to match "the value", included
 * the quotation marks
 *
 * <br>examples of path expressions:
 *
 * <br>root = {a: {b:1, c: {d:2, e:3}}, f:4}
 * <br> $.a -> {b:1, c: {d:2, e:3}}, f:4}
 * <br> $.* -> {a: {b:1, c: {d:2, e:3}}}, {f:4}
 * <br> $.a.b -> 1
 * <br> $.a.c -> {d:2,e:3}
 * <br> $.a.c.d -> 2
 *
 * <br>root = {a: [{b:1}, {c:2,d:3}}, true]}
 *
 * <br> $.a -> [{b:1}, {c:2,d:3}, true]
 * <br> $.a.[*] -> {b:1}, {c:2,d:3}, true
 * <br> $.a.[*].c -> null, 2, null
 *
 *
 * <br>root = {a: [{b:1}, {b:2}, {b:3}]}"
 *
 * <br> $.*.a.[*].b -> [1,2,3]
 *
 * <br>example rexex condition that matches email addresses:
 *
 * <br>{"path":"$._id", "regex":
 * "^\"[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}\"$"}
 * <br>with unicode escapes (used by httpie): {"path":"$._id", "regex":
 * "^\\u0022[A-Z0-9._%+-]+@[A-Z0-9.-]+\\u005C\\u005C.[A-Z]{2,6}\\u0022$"}
 *
 */
public class SimpleContentChecker implements Checker {
    static final Logger LOGGER = LoggerFactory.getLogger(SimpleContentChecker.class);

    @Override
    public boolean check(HttpServerExchange exchange, RequestContext context, DBObject args) {
        if (args instanceof BasicDBList) {
            BasicDBList conditions = getApplicableConditions((BasicDBList) args, context.getMethod(), context.getContent());

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

                    if (_type != null && _type instanceof String) {
                        type = (String) _type;
                    }

                    int count = -1;
                    Object _count = condition.get("count");

                    if (_count != null && _count instanceof Integer) {
                        count = (Integer) _count;
                    }

                    String regex = null;
                    Object _regex = condition.get("regex");

                    if (_regex != null && _regex instanceof String) {
                        regex = (String) _regex;
                    }

                    Boolean nullable = false;
                    Object _nullable = condition.get("nullable");

                    if (_nullable != null && _nullable instanceof Boolean) {
                        nullable = (Boolean) _nullable;
                    }

                    if (count < 0 && type == null && regex == null) {
                        context.addWarning("condition does not have any of 'count', 'type' and 'regex' properties, specify at least one: " + _condition);
                        return true;
                    }

                    if (path == null) {
                        context.addWarning("condition in the args list does not have the 'path' property: " + _condition);
                        return true;
                    }

                    if (type != null && count >= 0 && regex != null) {
                        return checkCount(context.getContent(), path, count) && checkType(context.getContent(), path, type, nullable) && checkRegex(context.getContent(), path, regex, nullable);
                    } else if (type != null && count >= 0) {
                        return checkCount(context.getContent(), path, count) && checkType(context.getContent(), path, type, nullable);
                    } else if (type != null && regex != null) {
                        return checkType(context.getContent(), path, type, nullable) && checkRegex(context.getContent(), path, regex, nullable);
                    } else if (count >= 0 && regex != null) {
                        return checkCount(context.getContent(), path, count) && checkRegex(context.getContent(), path, regex, nullable);
                    } else if (type != null) {
                        return checkType(context.getContent(), path, type, nullable);
                    } else if (count >= 0) {
                        return checkCount(context.getContent(), path, count);
                    } else if (regex != null) {
                        return checkRegex(context.getContent(), path, regex, nullable);
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

    private BasicDBList getApplicableConditions(BasicDBList conditions, RequestContext.METHOD method, DBObject content) {
        if (method == RequestContext.METHOD.POST || method == RequestContext.METHOD.PUT) {
            return conditions;
        } else if (method == RequestContext.METHOD.PATCH) {
            List filtered = conditions.stream().filter(condition -> {
                if (!(condition instanceof BasicDBObject)) {
                    return false;
                }

                BasicDBObject _condition = (BasicDBObject) condition;

                String path = null;
                Object _path = _condition.get("path");

                if (_path != null && _path instanceof String) {
                    path = (String) _path;
                }

                if (path == null) {
                    return false;
                }

                Object _count = _condition.get("count");

                LOGGER.debug("count ? {}", _count != null);
                LOGGER.debug("path {}", path);
                LOGGER.debug("condition {}", _condition);
                LOGGER.debug(JsonUtils.getPropsFromPath(content, path.concat(".*")).toString());

                if (_count != null) {
                    if (path.equals("$") || path.equals("$.*")) {
                        return false;
                    } else {
                        List<Object> matches = JsonUtils.getPropsFromPath(content, path);

                        if (matches.isEmpty()) {
                            return false;
                        }

                        return !(matches.size() == 1 && matches.get(0) == null);
                    }
                } else {
                    List<Object> matches = JsonUtils.getPropsFromPath(content, path);

                    if (matches.isEmpty()) {
                        return false;
                    }

                    return !(matches.size() == 1 && matches.get(0) == null);
                }
            }).collect(Collectors.toList());

            BasicDBList ret = new BasicDBList();

            filtered.stream().forEach((fc) -> {
                ret.add(fc);
            });

            return ret;

        } else {
            return new BasicDBList();
        }
    }

    private boolean checkType(DBObject json, String path, String type, boolean nullable) {
        BasicDBObject _json = (BasicDBObject) json;

        List<Object> props = JsonUtils.getPropsFromPath(_json, path);

        boolean ret;

        if (nullable) {
            ret = props.stream().allMatch(prop -> {
                return JsonUtils.checkType(prop, type) || JsonUtils.checkType(prop, "null");
            });
        } else {
            ret = props.stream().map((prop) -> JsonUtils.checkType(prop, type)).allMatch((thisCheck) -> (thisCheck));
        }

        LOGGER.debug("checkType({}, {}, {}) -> {} -> {}", json, path, type, props, ret);

        return ret;
    }

    private boolean checkCount(DBObject json, String path, int expectedCount) {
        boolean ret = expectedCount == JsonUtils.countPropsFromPath(json, path);

        LOGGER.debug("checkCount({}, {}, {}) -> {}", json, path, expectedCount, ret);

        return ret;
    }

    private boolean checkRegex(DBObject json, String path, String regex, boolean nullable) {
        BasicDBObject _json = (BasicDBObject) json;

        List<Object> props = JsonUtils.getPropsFromPath(_json, path);

        boolean ret;

        if (nullable) {
            Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

            ret = props.stream().allMatch(prop -> {
                return p.matcher(JsonUtils.serialize(prop)).find() || JsonUtils.checkType(prop, "null");
            });
        } else {
            Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            ret = props.stream().allMatch(prop -> {
                return p.matcher(JsonUtils.serialize(prop)).find();
            });
        }

        LOGGER.debug("checkRegex({}, {}, {}) -> {} -> {}", json, path, regex, props, ret);

        return ret;
    }
}
