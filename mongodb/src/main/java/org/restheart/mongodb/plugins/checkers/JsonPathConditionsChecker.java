/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.mongodb.plugins.checkers;

import io.undertow.server.HttpServerExchange;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.RequestContext;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.mongodb.Checker;
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
@RegisterPlugin(
        name = "checkContent",
        description = "Checks the request content by using conditions "
        + " based on json path expressions")
@SuppressWarnings("deprecation")
public class JsonPathConditionsChecker implements Checker {
    static final Logger LOGGER = LoggerFactory.getLogger(JsonPathConditionsChecker.class);

    /**
     *
     * @param s
     * @return
     */
    protected static String avoidEscapedChars(String s) {
        return s.replaceAll("\"", "'").replaceAll("\t", "  ");
    }

    /**
     *
     * @param exchange
     * @param context
     * @param contentToCheck
     * @param args
     * @return
     */
    @Override
    public boolean check(
            HttpServerExchange exchange,
            RequestContext context,
            BsonDocument contentToCheck,
            BsonValue args) {
        if (args.isArray()) {
            BsonArray conditions
                    = filterMissingOptionalAndNullNullableConditions(
                            args.asArray(),
                            contentToCheck);

            return applyConditions(conditions, contentToCheck, context);
        } else {
            context.addWarning(
                    "checker wrong definition: args property must be "
                    + "an arrary of string property names.");
            return true;
        }
    }

    @Override
    public PHASE getPhase(RequestContext context) {
        if (context.isPatch()
                || CheckersUtils
                        .doesRequestUsesDotNotation(context.getContent())
                || CheckersUtils
                        .doesRequestUsesUpdateOperators(context.getContent())) {
            return PHASE.AFTER_WRITE;
        } else {
            return PHASE.BEFORE_WRITE;
        }
    }

    /**
     *
     * @param context
     * @return
     */
    @Override
    public boolean doesSupportRequests(RequestContext context) {
        return !(CheckersUtils.isBulkRequest(context)
                && getPhase(context) == PHASE.AFTER_WRITE);
    }

    /**
     *
     * @param conditions
     * @param json
     * @param context
     * @return
     */
    protected boolean applyConditions(BsonArray conditions, BsonDocument json, final RequestContext context) {
        return conditions.stream().allMatch(_condition -> {
            if (_condition.isDocument()) {
                BsonDocument condition = _condition.asDocument();

                String path = null;
                BsonValue _path = condition.get("path");
                if (_path != null && _path.isString()) {
                    path = _path.asString().getValue();
                }

                String type = null;
                BsonValue _type = condition.get("type");
                if (_type != null && _type.isString()) {
                    type = _type.asString().getValue();
                }

                Set<Integer> counts = new HashSet<>();
                BsonValue _count = condition.get("count");
                if (_count != null) {
                    if (_count.isInt32()) {
                        counts.add(_count.asInt32().getValue());
                    } else if (_count.isArray()) {
                        BsonArray countsArray = _count.asArray();

                        countsArray.forEach(countElement -> {
                            if (countElement.isInt32()) {
                                counts.add(countElement.asInt32().getValue());
                            }
                        });
                    }
                }

                Set<String> mandatoryFields;
                BsonValue _mandatoryFields = condition.get("mandatoryFields");
                if (_mandatoryFields != null) {
                    mandatoryFields = new HashSet<>();
                    if (_mandatoryFields.isArray()) {
                        BsonArray mandatoryFieldsArray = _mandatoryFields
                                .asArray();

                        mandatoryFieldsArray.forEach(element -> {
                            if (element.isString()) {
                                mandatoryFields.add(element
                                        .asString().getValue());
                            }
                        });
                    }
                } else {
                    mandatoryFields = null;
                }

                Set<String> optionalFields;
                BsonValue _optionalFields = condition.get("optionalFields");
                if (_optionalFields != null) {
                    optionalFields = new HashSet<>();
                    if (_optionalFields.isArray()) {
                        BsonArray optionalFieldsArray = _optionalFields
                                .asArray();

                        optionalFieldsArray.forEach(element -> {
                            if (element.isString()) {
                                optionalFields.add(
                                        element.asString().getValue());
                            }
                        });
                    }
                } else {
                    optionalFields = null;
                }
                String regex = null;

                BsonValue _regex = condition.get("regex");
                if (_regex != null && _regex.isString()) {
                    regex = _regex.asString().getValue();
                }

                Boolean optional = false;
                BsonValue _optional = condition.get("optional");
                if (_optional != null && _optional.isBoolean()) {
                    optional = _optional.asBoolean().getValue();
                }

                Boolean nullable = false;
                BsonValue _nullable = condition.get("nullable");
                if (_nullable != null && _nullable.isBoolean()) {
                    nullable = _nullable.asBoolean().getValue();
                }
                if (counts.isEmpty() && type == null && regex == null) {
                    context.addWarning("condition does not have any of "
                            + "'count', 'type' and 'regex' properties, "
                            + "specify at least one: " + _condition);
                    return true;
                }
                if (path == null) {
                    context.addWarning(
                            "condition in the args list does "
                            + "not have the 'path' property: " + _condition);
                    return true;
                }
                if (type != null && !counts.isEmpty() && regex != null) {
                    return checkCount(
                            json,
                            path,
                            counts,
                            context)
                            && checkType(
                                    json,
                                    path,
                                    type,
                                    mandatoryFields,
                                    optionalFields,
                                    optional,
                                    nullable,
                                    context)
                            && checkRegex(
                                    json,
                                    path,
                                    regex,
                                    optional,
                                    nullable,
                                    context);
                } else if (type != null && !counts.isEmpty()) {
                    return checkCount(
                            json,
                            path,
                            counts,
                            context)
                            && checkType(
                                    json,
                                    path,
                                    type,
                                    mandatoryFields,
                                    optionalFields,
                                    optional,
                                    nullable,
                                    context);
                } else if (type != null && regex != null) {
                    return checkType(
                            json,
                            path,
                            type,
                            mandatoryFields,
                            optionalFields,
                            optional,
                            nullable,
                            context)
                            && checkRegex(
                                    json,
                                    path,
                                    regex,
                                    optional,
                                    nullable,
                                    context);
                } else if (!counts.isEmpty() && regex != null) {
                    return checkCount(
                            json,
                            path,
                            counts,
                            context)
                            && checkRegex(
                                    json,
                                    path,
                                    regex,
                                    optional,
                                    nullable,
                                    context);
                } else if (type != null) {
                    return checkType(
                            json,
                            path,
                            type,
                            mandatoryFields,
                            optionalFields,
                            optional,
                            nullable,
                            context);
                } else if (!counts.isEmpty()) {
                    return checkCount(
                            json,
                            path,
                            counts,
                            context);
                } else if (regex != null) {
                    return checkRegex(
                            json,
                            path,
                            regex,
                            optional,
                            nullable,
                            context);
                }
                return true;
            } else {
                context.addWarning(
                        "property in the args list is not an object: "
                        + _condition);
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
    protected BsonArray filterMissingOptionalAndNullNullableConditions(BsonArray conditions, BsonValue content) {
        Set<String> nullPaths = new HashSet<>();
        BsonArray ret = new BsonArray();

        conditions.stream().forEach(_condition -> {
            if (_condition.isDocument()) {
                BsonDocument condition = _condition.asDocument();

                Boolean nullable = false;
                BsonValue _nullable = condition.get("nullable");
                if (_nullable != null && _nullable.isBoolean()) {
                    nullable = _nullable.asBoolean().getValue();
                }

                Boolean optional = false;
                BsonValue _optional = condition.get("optional");
                if (_optional != null && _optional.isBoolean()) {
                    optional = _optional.asBoolean().getValue();
                }
                if (nullable) {
                    BsonValue _path = condition.get("path");
                    if (_path != null && _path.isString()) {
                        String path = _path.asString().getValue();
                        List<Optional<BsonValue>> props;
                        try {
                            props = JsonUtils.getPropsFromPath(content, path);
                            if (props != null && props.stream().allMatch(
                                    (Optional<BsonValue> prop) -> {
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
                    BsonValue _path = condition.get("path");
                    if (_path != null && _path.isString()) {
                        String path = _path.asString().getValue();
                        List<Optional<BsonValue>> props;
                        try {
                            props = JsonUtils.getPropsFromPath(content, path);
                            if (props == null || props.stream().allMatch(
                                    (Optional<BsonValue> prop) -> {
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
        conditions.stream().forEach(_condition -> {
            if (_condition.isDocument()) {
                BsonDocument condition = _condition.asDocument();

                BsonValue _path = condition.get("path");
                if (_path != null && _path.isString()) {
                    String path = _path.asString().getValue();
                    boolean hasNullParent = nullPaths.stream().anyMatch(
                            (String nullPath) -> {
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

    /**
     *
     * @param json
     * @param path
     * @param expectedCounts
     * @param context
     * @return
     */
    protected boolean checkCount(BsonValue json,
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
            context.addWarning("checkCount condition failed: path: "
                    + path
                    + ", expected: "
                    + expectedCounts
                    + ", got: "
                    + count);
        }
        return ret;
    }

    /**
     *
     * @param json
     * @param path
     * @param type
     * @param mandatoryFields
     * @param optionalFields
     * @param optional
     * @param nullable
     * @param context
     * @return
     */
    protected boolean checkType(BsonDocument json,
            String path, String type,
            Set<String> mandatoryFields,
            Set<String> optionalFields,
            boolean optional,
            boolean nullable,
            RequestContext context) {
        List<Optional<BsonValue>> props;
        boolean ret;
        boolean failedFieldsCheck = false;
        try {
            props = JsonUtils.getPropsFromPath(json, path);
        } catch (IllegalArgumentException ex) {
            LOGGER.debug("checkType({}, {}, {}, {}) -> {} -> false",
                    path,
                    type,
                    mandatoryFields,
                    optionalFields,
                    ex.getMessage());

            context.addWarning(
                    "checkType condition failed: path: "
                    + path
                    + ", expected type: "
                    + type + ", error: "
                    + ex.getMessage());
            return false;
        }
        // props is null when path does not exist.
        if (props == null) {
            ret = optional;
        } else {
            ret = props.stream().allMatch((Optional<BsonValue> prop) -> {
                if (prop == null) {
                    return optional;
                }
                if (prop.isPresent()) {
                    if ("array".equals(type) && prop.get().isDocument()) {
                        // this might be the case of PATCHING an element array using the dot notation
                        // e.g. object.array.2
                        // if so, the array comes as an BsonDocument with all numberic keys
                        // in any case, it might also be the object { "object": { "array": {"2": xxx }}}
                        return (prop
                                .get())
                                .asDocument()
                                .keySet()
                                .stream()
                                .allMatch((String k) -> {
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
            if (ret && "object".equals(type)
                    && (mandatoryFields != null
                    || optionalFields != null)) {
                Set<String> allFields = new HashSet<>();
                if (mandatoryFields != null) {
                    allFields.addAll(mandatoryFields);
                }
                if (optionalFields != null) {
                    allFields.addAll(optionalFields);
                }
                ret = props.stream().allMatch((Optional<BsonValue> prop) -> {
                    if (prop == null) {
                        return optional;
                    }
                    if (prop.isPresent()) {
                        BsonDocument obj = prop.get().asDocument();
                        if (mandatoryFields != null) {
                            return obj.keySet()
                                    .containsAll(mandatoryFields)
                                    && allFields.containsAll(obj.keySet());
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

        if (ret) {
            LOGGER.trace(
                    "checkType({}, {}, {}, {}) -> {} -> {}",
                    path,
                    type,
                    mandatoryFields,
                    optionalFields,
                    getRootPropsString(props),
                    ret);
        } else {
            LOGGER.debug(
                    "checkType({}, {}, {}, {}) -> {} -> {}",
                    path,
                    type,
                    mandatoryFields,
                    optionalFields,
                    getRootPropsString(props),
                    ret);

            String errorMessage;
            if (!failedFieldsCheck) {
                errorMessage = "checkType condition failed: path: " + path
                        + ", expected type: " + type
                        + ", got: " + (props == null
                                ? "null"
                                : avoidEscapedChars(getRootPropsString(props)));

            } else {
                errorMessage = "checkType condition failed: path: " + path
                        + ", mandatory fields: " + mandatoryFields
                        + ", optional fields: " + optionalFields
                        + ", got: " + (props == null
                                ? "null"
                                : avoidEscapedChars(getRootPropsString(props)));
            }

            context.addWarning(errorMessage);
        }

        return ret;
    }

    /**
     *
     * @param json
     * @param path
     * @param regex
     * @param optional
     * @param nullable
     * @param context
     * @return
     */
    protected boolean checkRegex(BsonDocument json,
            String path, String regex,
            boolean optional,
            boolean nullable,
            RequestContext context) {
        List<Optional<BsonValue>> props;
        try {
            props = JsonUtils.getPropsFromPath(json, path);
        } catch (IllegalArgumentException ex) {
            LOGGER.debug(
                    "checkRegex({}, {}) -> {}",
                    path,
                    regex,
                    ex.getMessage());

            context.addWarning(
                    "checkRegex condition failed: path: "
                    + path
                    + ", regex: "
                    + regex
                    + ", got: "
                    + ex.getMessage());

            return false;
        }
        boolean ret;
        // props is null when path does not exist.
        if (props == null) {
            ret = optional;
        } else {
            Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            ret = props.stream().allMatch((Optional<BsonValue> prop) -> {
                if (prop == null) {
                    return optional;
                }
                if (prop.isPresent()) {
                    if (prop.get().isString()) {
                        return p.matcher(prop.get().asString().getValue())
                                .find();
                    } else {
                        return p.matcher(JsonUtils.toJson(prop.get())).find();
                    }
                } else {
                    return nullable;
                }
            });
        }

        if (ret) {
            LOGGER.trace(
                    "checkRegex({}, {}) -> {} -> {}",
                    path,
                    regex,
                    getRootPropsString(props),
                    ret);
        } else {
            LOGGER.debug(
                    "checkRegex({}, {}) -> {} -> {}",
                    path,
                    regex,
                    getRootPropsString(props),
                    ret);

            String errorMessage = "checkRegex condition failed: path: " + path
                    + ", regex: " + regex
                    + ", got: " + (props == null
                            ? "null"
                            : avoidEscapedChars(getRootPropsString(props)));

            context.addWarning(errorMessage);
        }

        return ret;
    }

    private String getRootPropsString(List<Optional<BsonValue>> props) {
        if (props == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        props.stream().forEach((_prop) -> {
            if (_prop == null) {
                sb.append("<property not existing>");
            } else if (_prop.isPresent()) {
                BsonValue prop = _prop.get();

                if (prop.isArray()) {
                    BsonArray array = prop.asArray();

                    sb.append("[");

                    array.stream().forEach((item) -> {
                        if (item.isDocument()) {
                            sb.append("{obj}");
                        } else if (item.isArray()) {
                            sb.append("[array]");
                        } else if (item.isString()) {
                            sb.append("'");
                            sb.append(item.asString().getValue());
                            sb.append("'");
                        } else {
                            sb.append(JsonUtils.toJson(prop));
                        }

                        sb.append(", ");
                    });

                    // remove last comma
                    if (sb.length() > 1) {
                        sb.deleteCharAt(sb.length() - 1);
                        sb.deleteCharAt(sb.length() - 1);
                    }

                    sb.append("]");
                } else if (prop.isDocument()) {
                    BsonDocument obj = prop.asDocument();

                    sb.append(obj.keySet().toString());
                } else if (prop.isString()) {
                    sb.append("'");
                    sb.append(prop.asString().getValue());
                    sb.append("'");
                } else {
                    sb.append(prop.toString());
                }
            } else {
                sb.append("null");
            }

            sb.append(", ");
        });

        // remove last comma
        if (sb.length() > 1) {
            sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
        }

        return sb.toString();
    }
}
