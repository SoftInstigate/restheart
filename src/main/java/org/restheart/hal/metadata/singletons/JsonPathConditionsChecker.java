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
import org.restheart.handlers.RequestContext;
import org.restheart.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 *
 * JsonPathConditionsChecker allows to check request content by using conditions
 * based on json path expression
 *
 * JsonPathConditionsChecker does not support dot notation and update operators
 * on bulk requests. For instance PATCH /db/coll/* { $currentDate: { "a.b": true
 * }}
 *
 * the args arguments is an array of condition. a condition is json object as
 * follows: { "path": "PATHEXPR", [ "type": "APPLY]"] ["count": COUNT ]
 * ["regex": "REGEX"] ["nullable": BOOLEAN]}
 *
 * where
 *
 * <br>PATHEXPR the path expression. use the . notation to identify the property
 * <br>COUNT is the number of expected values
 * <br>APPLY can be any BSON type: null, object, array, string, number, boolean
 * * objectid, date,timestamp, maxkey, minkey, symbol, code, objectid
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
public class JsonPathConditionsChecker implements Checker {
    static final Logger LOGGER = LoggerFactory.getLogger(JsonPathConditionsChecker.class);

    protected static String avoidEscapedChars(String s) {
        return s.replaceAll("\"", "'").replaceAll("\t", "  ");
    }

    @Override
    public boolean check(HttpServerExchange exchange, RequestContext context, BasicDBObject contentToCheck, DBObject args) {
        if (args instanceof BasicDBList) {
            BasicDBList conditions = filterMissingOptionalAndNullNullableConditions((BasicDBList) args, contentToCheck);
            return applyConditions(conditions, contentToCheck, context);
        } else {
            context.addWarning("checker wrong definition: args property must be an arrary of string property names.");
            return true;
        }
    }

    @Override
    public PHASE getPhase(RequestContext context) {
        if (context.getMethod() == RequestContext.METHOD.PATCH
                || CheckersUtils.doesRequestUsesDotNotation(context.getContent())
                || CheckersUtils.doesRequestUsesUpdateOperators(context.getContent())) {
            return PHASE.AFTER_WRITE;
        } else {
            return PHASE.BEFORE_WRITE;
        }
    }

    @Override
    public boolean doesSupportRequests(RequestContext context) {
        return !(CheckersUtils.isBulkRequest(context)
                && getPhase(context) == PHASE.AFTER_WRITE);
    }

    protected boolean applyConditions(BasicDBList conditions, DBObject json, final RequestContext context) {
        return conditions.stream().allMatch((Object _condition) -> {
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
                        countsArray.forEach((Object countElement) -> {
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
                        mandatoryFieldsArray.forEach((Object element) -> {
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
                        optionalFieldsArray.forEach((Object element) -> {
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
                    return checkCount(json, path, counts, context) && checkType(json, path, type, mandatoryFields, optionalFields, optional, nullable, context) && checkRegex(json, path, regex, optional, nullable, context);
                } else if (type != null && !counts.isEmpty()) {
                    return checkCount(json, path, counts, context) && checkType(json, path, type, mandatoryFields, optionalFields, optional, nullable, context);
                } else if (type != null && regex != null) {
                    return checkType(json, path, type, mandatoryFields, optionalFields, optional, nullable, context) && checkRegex(json, path, regex, optional, nullable, context);
                } else if (!counts.isEmpty() && regex != null) {
                    return checkCount(json, path, counts, context) && checkRegex(json, path, regex, optional, nullable, context);
                } else if (type != null) {
                    return checkType(json, path, type, mandatoryFields, optionalFields, optional, nullable, context);
                } else if (!counts.isEmpty()) {
                    return checkCount(json, path, counts, context);
                } else if (regex != null) {
                    return checkRegex(json, path, regex, optional, nullable, context);
                }
                return true;
            } else {
                context.addWarning("property in the args list is not an object: " + _condition);
                return true;
            }
        });
    }

    /**
     * this filters out the nullable and optional conditions where the path
     * resolves to null
     *
     * @param conditions
     * @param content
     * @return
     */
    protected BasicDBList filterMissingOptionalAndNullNullableConditions(BasicDBList conditions, DBObject content) {
        Set<String> nullPaths = new HashSet<>();
        BasicDBList ret = new BasicDBList();
        conditions.stream().forEach((Object condition) -> {
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
                if (nullable) {
                    Object _path = ((BasicDBObject) condition).get("path");
                    if (_path != null && _path instanceof String) {
                        String path = (String) _path;
                        List<Optional<Object>> props;
                        try {
                            props = JsonUtils.getPropsFromPath(content, path);
                            if (props != null && props.stream().allMatch((Optional<Object> prop) -> {
                                return prop != null && !prop.isPresent();
                            })) {
                                LOGGER.debug("ignoring null path {}", path);
                                nullPaths.add(path);
                            }
                        } catch (IllegalArgumentException ex) {
                            nullPaths.add(path);
                        }
                    }
                }
                if (optional) {
                    Object _path = ((BasicDBObject) condition).get("path");
                    if (_path != null && _path instanceof String) {
                        String path = (String) _path;
                        List<Optional<Object>> props;
                        try {
                            props = JsonUtils.getPropsFromPath(content, path);
                            if (props == null || props.stream().allMatch((Optional<Object> prop) -> {
                                return prop == null;
                            })) {
                                nullPaths.add(path);
                            }
                        } catch (IllegalArgumentException ex) {
                            nullPaths.add(path);
                        }
                    }
                }
            }
        });
        conditions.stream().forEach((Object condition) -> {
            if (condition instanceof BasicDBObject) {
                Object _path = ((BasicDBObject) condition).get("path");
                if (_path != null && _path instanceof String) {
                    String path = (String) _path;
                    boolean hasNullParent = nullPaths.stream().anyMatch((String nullPath) -> {
                        return JsonUtils.isAncestorPath(nullPath, path);
                    });
                    if (!hasNullParent) {
                        ret.add(condition);
                    }
                }
            }
        });
        return ret;
    }

    protected boolean checkCount(DBObject json,
            String path, Set<Integer> expectedCounts,
            RequestContext context) {
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

    protected boolean checkType(DBObject json,
            String path, String type,
            Set<String> mandatoryFields,
            Set<String> optionalFields,
            boolean optional,
            boolean nullable,
            RequestContext context) {
        BasicDBObject _json = (BasicDBObject) json;
        List<Optional<Object>> props;
        boolean ret;
        boolean failedFieldsCheck = false;
        try {
            props = JsonUtils.getPropsFromPath(_json, path);
        } catch (IllegalArgumentException ex) {
            LOGGER.debug("checkType({}, {}, {}, {}) -> {} -> false", path, type, mandatoryFields, optionalFields, ex.getMessage());
            context.addWarning("checkType condition failed: path: " + path + ", expected type: " + type + ", error: " + ex.getMessage());
            return false;
        }
        // props is null when path does not exist.
        if (props == null) {
            ret = optional;
        } else {
            ret = props.stream().allMatch((Optional<Object> prop) -> {
                if (prop == null) {
                    return optional;
                }
                if (prop.isPresent()) {
                    if ("array".equals(type) && prop.get() instanceof DBObject) {
                        // this might be the case of PATCHING an element array using the dot notation
                        // e.g. object.array.2
                        // if so, the array comes as an BasicDBObject with all numberic keys
                        // in any case, it might also be the object { "object": { "array": {"2": xxx }}}
                        return ((DBObject) prop.get()).keySet().stream().allMatch((String k) -> {
                            try {
                                Integer.parseInt(k);
                                return true;
                            } catch (NumberFormatException nfe) {
                                return false;
                            }
                        }) || JsonUtils.checkType(prop, type);
                    } else {
                        return JsonUtils.checkType(prop, type);
                    }
                } else {
                    return nullable;
                }
            });
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
        }
        LOGGER.debug("checkType({}, {}, {}, {}) -> {} -> {}", path, type, mandatoryFields, optionalFields, props, ret);
        if (ret == false) {
            if (!failedFieldsCheck) {
                context.addWarning("checkType condition failed: path: " + path + ", expected type: " + type + ", got: " + (props == null ? "null" : avoidEscapedChars(props.toString())));
            } else {
                context.addWarning("checkType condition failed: path: " + path + ", mandatory fields: " + mandatoryFields + ", optional fields: " + optionalFields + ", got: " + (props == null ? "null" : avoidEscapedChars(props.toString())));
            }
        }
        return ret;
    }

    protected boolean checkRegex(DBObject json,
            String path, String regex,
            boolean optional,
            boolean nullable,
            RequestContext context) {
        BasicDBObject _json = (BasicDBObject) json;
        List<Optional<Object>> props;
        try {
            props = JsonUtils.getPropsFromPath(_json, path);
        } catch (IllegalArgumentException ex) {
            LOGGER.debug("checkRegex({}, {}) -> {}", path, regex, ex.getMessage());
            context.addWarning("checkRegex condition failed: path: " + path + ", regex: " + regex + ", got: " + ex.getMessage());
            return false;
        }
        boolean ret;
        // props is null when path does not exist.
        if (props == null) {
            ret = optional;
        } else {
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
        }
        LOGGER.debug("checkRegex({}, {}) -> {} -> {}", path, regex, props, ret);
        if (ret == false) {
            context.addWarning("checkRegex condition failed: path: " + path + ", regex: " + regex + ", got: " + (props == null ? "null" : avoidEscapedChars(props.toString())));
        }
        return ret;
    }
}
