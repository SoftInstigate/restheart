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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
 * <br>PATHEXPR the path expression. use the . notation to identify the property
 * <br>COUNT is the number of expected values
 * <br>TYPE can be any BSON type: null, object, array, string, number, boolean *
 * objectid, date,timestamp, maxkey, minkey, symbol, code, objectid
 * <br>REGEX regular expression. note that string values to match come enclosed
 * in quotation marks, i.e. the regex will need to match "the value", included
 * the quotation marks
 *
 * <br>examples for path expressions:
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
 * <br>example regex condition that matches email addresses:
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

                    Set<Integer> counts = new HashSet<>();
                    Object _count = condition.get("count");

                    if (_count != null) {
                        if (_count instanceof Integer) {
                            counts.add((Integer) _count);
                        } else if (_count instanceof BasicDBList) {
                            BasicDBList countsArray = (BasicDBList) _count;

                            countsArray.forEach(countElement -> {
                                if (countElement instanceof Integer) {
                                    counts.add((Integer) countElement);
                                }
                            });
                        }
                    }

                    Set<String> mandatoryFields;
                    Object _mandatoryFields = condition.get("mandatoryFields");

                    if (_mandatoryFields != null) {
                        mandatoryFields = new HashSet<>();

                        if (_mandatoryFields instanceof BasicDBList) {
                            BasicDBList mandatoryFieldsArray = (BasicDBList) _mandatoryFields;

                            mandatoryFieldsArray.forEach(element -> {
                                if (element instanceof String) {
                                    mandatoryFields.add((String) element);
                                }
                            });
                        }
                    } else {
                        mandatoryFields = null;
                    }

                    Set<String> optionalFields;
                    Object _optionalFields = condition.get("optionalFields");

                    if (_optionalFields != null) {
                        optionalFields = new HashSet<>();

                        if (_optionalFields instanceof BasicDBList) {
                            BasicDBList optionalFieldsArray = (BasicDBList) _optionalFields;

                            optionalFieldsArray.forEach(element -> {
                                if (element instanceof String) {
                                    optionalFields.add((String) element);
                                }
                            });
                        }
                    } else {
                        optionalFields = null;
                    }

                    String regex = null;
                    Object _regex = condition.get("regex");

                    if (_regex != null && _regex instanceof String) {
                        regex = (String) _regex;
                    }

                    Boolean optional = false;
                    Object _optional = condition.get("optional");

                    if (_optional != null && _optional instanceof Boolean) {
                        optional = (Boolean) _optional;
                    }

                    Boolean nullable = false;
                    Object _nullable = condition.get("nullable");

                    if (_nullable != null && _nullable instanceof Boolean) {
                        nullable = (Boolean) _nullable;
                    }

                    if (counts.isEmpty() && type == null && regex == null) {
                        context.addWarning("condition does not have any of 'count', 'type' and 'regex' properties, specify at least one: " + _condition);
                        return true;
                    }

                    if (path == null) {
                        context.addWarning("condition in the args list does not have the 'path' property: " + _condition);
                        return true;
                    }

                    if (type != null && !counts.isEmpty() && regex != null) {
                        return checkCount(context.getContent(), path, counts, context) && checkType(context.getContent(), path, type, mandatoryFields, optionalFields, optional, nullable, context) && checkRegex(context.getContent(), path, regex, optional, nullable, context);
                    } else if (type != null && !counts.isEmpty()) {
                        return checkCount(context.getContent(), path, counts, context) && checkType(context.getContent(), path, type, mandatoryFields, optionalFields, optional, nullable, context);
                    } else if (type != null && regex != null) {
                        return checkType(context.getContent(), path, type, mandatoryFields, optionalFields, optional, nullable, context) && checkRegex(context.getContent(), path, regex, optional, nullable, context);
                    } else if (!counts.isEmpty() && regex != null) {
                        return checkCount(context.getContent(), path, counts, context) && checkRegex(context.getContent(), path, regex, optional, nullable, context);
                    } else if (type != null) {
                        return checkType(context.getContent(), path, type, mandatoryFields, optionalFields, optional, nullable, context);
                    } else if (!counts.isEmpty()) {
                        return checkCount(context.getContent(), path, counts, context);
                    } else if (regex != null) {
                        return checkRegex(context.getContent(), path, regex, optional, nullable, context);
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

    private BasicDBList filterNullableAndOptionalNullConditions(BasicDBList conditions, DBObject content) {
        // nullPaths contains all paths that result to null and condition is nullable or optional
        Set<String> nullPaths = new HashSet<>();

        BasicDBList ret = new BasicDBList();

        conditions.stream().forEach(condition -> {
            if (condition instanceof BasicDBObject) {
                Boolean nullable = false;
                Object _nullable = ((BasicDBObject) condition).get("nullable");

                if (_nullable != null && _nullable instanceof Boolean) {
                    nullable = (Boolean) _nullable;
                }

                Boolean optional = false;
                Object _optional = ((BasicDBObject) condition).get("optional");

                if (_optional != null && _optional instanceof Boolean) {
                    optional = (Boolean) _optional;
                }

                if (nullable || optional) {
                    Object _path = ((BasicDBObject) condition).get("path");

                    if (_path != null && _path instanceof String) {
                        String path = (String) _path;

                        List<Optional<Object>> props;
                        try {
                            props = JsonUtils.getPropsFromPath(content, path);

                            if (props == null) {
                                nullPaths.add(path);
                            }
                        } catch (IllegalArgumentException ex) {
                            nullPaths.add(path);
                        }
                    }
                }
            }
        });

        conditions.stream().forEach(condition -> {
            if (condition instanceof BasicDBObject) {
                Object _path = ((BasicDBObject) condition).get("path");

                if (_path != null && _path instanceof String) {
                    String path = (String) _path;

                    boolean hasNullParent = nullPaths.stream().anyMatch(nullPath -> {
                        LOGGER.debug("does {} implies {}? {}", nullPath, path, path.startsWith(nullPath));
                        return path.startsWith(nullPath);
                    });

                    if (!hasNullParent) {
                        ret.add(condition);
                    }
                }
            }
        });

        return ret;
    }

    private BasicDBList getApplicableConditions(BasicDBList conditions, RequestContext.METHOD method, DBObject content) {
        if (method == RequestContext.METHOD.POST || method == RequestContext.METHOD.PUT) {
            return filterNullableAndOptionalNullConditions(conditions, content);
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

                if (_count != null) {
                    if (path.equals("$") || path.equals("$.*")) {
                        return false;
                    } else {
                        try {
                            List<Optional<Object>> matches = JsonUtils.getPropsFromPath(content, path);

                            if (matches == null || matches.isEmpty()) {
                                return false;
                            }

                            return !(matches.size() == 1 && matches.get(0) == null);
                        } catch (IllegalArgumentException ex) {
                            return false;
                        }
                    }
                } else {
                    try {
                        List<Optional<Object>> matches = JsonUtils.getPropsFromPath(content, path);

                        if (matches == null || matches.isEmpty()) {
                            return false;
                        }

                        return !(matches.size() == 1 && matches.get(0) == null);
                    } catch (IllegalArgumentException ex) {
                        return false;
                    }
                }
            }).collect(Collectors.toList());

            BasicDBList ret = new BasicDBList();

            filtered.stream().forEach((fc) -> {
                ret.add(fc);
            });

            return filterNullableAndOptionalNullConditions(ret, content);

        } else {
            return new BasicDBList();
        }
    }

    private boolean checkCount(DBObject json, String path, Set<Integer> expectedCounts, RequestContext context) {
        Integer count;
        try {
            count = JsonUtils.countPropsFromPath(json, path);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        // props is null when path does not exist. count is false
        if (count == null) {
            return false;
        }

        boolean ret = expectedCounts.contains(count);

        LOGGER.debug("checkCount({}, {}) -> {}", path, expectedCounts, ret);

        if (ret == false) {
            context.addWarning("checkCount condition failed: path: " + path + ", expected: " + expectedCounts + ", got: " + count);
        }

        return ret;
    }

    private boolean checkType(DBObject json, String path, String type, Set<String> mandatoryFields, Set<String> optionalFields,
            boolean optional, boolean nullable, RequestContext context) {
        BasicDBObject _json = (BasicDBObject) json;

        List<Optional<Object>> props;

        try {
            props = JsonUtils.getPropsFromPath(_json, path);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        // props is null when path does not exist.
        if (props == null) {
            return optional;
        }

        boolean ret;

        ret = props.stream().allMatch((Optional<Object> prop) -> {
            if (prop == null) {
                return optional;
            }

            if (prop.isPresent()) {
                return JsonUtils.checkType(prop, type);
            } else {
                return nullable;
            }
        });

        boolean failedFieldsCheck = false;

        // check object fields
        if (ret && "object".equals(type) && (mandatoryFields != null || optionalFields != null)) {
            Set<String> allFields = new HashSet<>();

            if (mandatoryFields != null) {
                allFields.addAll(mandatoryFields);
            }

            if (optionalFields != null) {
                allFields.addAll(optionalFields);
            }

            ret = props.stream().allMatch((Optional<Object> prop) -> {
                if (prop == null) {
                    return optional;
                }

                if (prop.isPresent()) {
                    BasicDBObject obj = (BasicDBObject) prop.get();

                    if (mandatoryFields != null) {
                        return obj.keySet().containsAll(mandatoryFields) && allFields.containsAll(obj.keySet());
                    } else {
                        return allFields.containsAll(obj.keySet());
                    }
                } else {
                    return nullable;
                }
            });

            if (ret == false) {
                failedFieldsCheck = true;
            }
        }

        LOGGER.debug("checkType({}, {}, {}, {}) -> {} -> {}", path, type, mandatoryFields, optionalFields, props, ret);

        if (ret == false) {
            if (!failedFieldsCheck) {
                context.addWarning("checkType condition failed: path: " + path + ", expected type: " + type + ", got: " + props);
            } else {
                context.addWarning("checkType condition failed: path: " + path + ", mandatory fields: " + mandatoryFields + ", optional fields: " + optionalFields + ", got: " + props);
            }
        }

        return ret;
    }

    private boolean checkRegex(DBObject json, String path, String regex, boolean optional, boolean nullable, RequestContext context) {
        BasicDBObject _json = (BasicDBObject) json;

        List<Optional<Object>> props;
        try {
            props = JsonUtils.getPropsFromPath(_json, path);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        // props is null when path does not exist.
        if (props == null) {
            return optional;
        }

        boolean ret;

        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

        ret = props.stream().allMatch((Optional<Object> prop) -> {
            if (prop == null) {
                return optional;
            }

            if (prop.isPresent()) {
                return p.matcher(JsonUtils.serialize(prop.get())).find();
            } else {
                return nullable;
            }
        });

        LOGGER.debug("checkRegex({}, {}) -> {} -> {}", path, regex, props, ret);

        if (ret == false) {
            context.addWarning("checkRegex condition failed: path: " + path + ", regex: " + regex + ", got: " + props);
        }

        return ret;
    }
}
