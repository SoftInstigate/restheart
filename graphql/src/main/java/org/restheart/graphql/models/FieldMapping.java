/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.graphql.models;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.exchange.Request;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.mongodb.utils.StagesInterpolator;
import org.restheart.utils.BsonUtils;
import org.restheart.utils.LambdaUtils;
import graphql.schema.DataFetchingEnvironment;

public abstract class FieldMapping {
    /**
     * Where {@code GraphQLService} puts the request it is serving, so a mapping can resolve the
     * {@code @} variables that depend on it (restheart#727). graphql-java's {@code GraphQLContext}
     * holds arbitrary objects; {@code localContext} is a {@code BsonDocument} and cannot.
     */
    public static final String REQUEST_CONTEXT_KEY = "restheart-request";

    protected final String fieldName;

    /** The check-operators option of the graphql configuration. */
    private static boolean checkOperators = true;

    /**
     * @param _checkOperators true to refuse arguments with operators and to bind string arguments
     *        starting with $ in $literal where MongoDB would read them as field paths or variables
     */
    public static void setCheckOperators(boolean _checkOperators) {
        checkOperators = _checkOperators;
    }

    protected static boolean checkOperators() {
        return checkOperators;
    }

    /** The key that, next to $arg, declares an argument that may carry operators. */
    private static final String OPERATORS_KEY = "operators";

    /**
     * The arguments the mapping declares with {@code {"$arg": "name", "operators": true}}: they may
     * carry operators even with check-operators, e.g. a filter the client writes on purpose.
     */
    private Set<String> operatorArgs = Set.of();

    /**
     * With check-operators, refuses arguments that contain MongoDB operators, i.e. keys starting
     * with $. GraphQL names cannot start with $, so they can only come with a BsonDocument argument
     * passed as a GraphQL variable. The arguments the mapping declares with
     * {@code "operators": true} are not checked.
     *
     * @throws QueryVariableNotBoundException if an argument contains an operator
     */
    protected void checkArguments(DataFetchingEnvironment env) throws QueryVariableNotBoundException {
        if (!checkOperators || env.getArguments() == null) {
            return;
        }

        var args = BsonUtils.toBsonDocument(env.getArguments());
        operatorArgs.forEach(args::remove);

        try {
            StagesInterpolator.shouldNotContainOperators(args);
        } catch (SecurityException se) {
            throw new QueryVariableNotBoundException("arguments cannot include operators, see check-operators in the graphql configuration");
        }
    }

    /**
     * Reads the arguments a mapping declares with {@code {"$arg": "name", "operators": true}} (or
     * {@code {"$arg": ["name", default], "operators": true}}) in its definition, and returns the
     * definition with {@code "operators"} removed, so that the interpolator finds plain
     * {@code $arg} documents.
     *
     * <p>An argument declared with operators in one place must be declared so wherever the mapping
     * uses it: otherwise a value meant elsewhere as a plain value would skip the check. Such a
     * mapping, or one whose {@code "operators"} is not a boolean, is an invalid app definition.</p>
     *
     * @param definitions the parts of the mapping that hold $arg references, e.g. its stages, or its find and sort
     * @return the definitions without the {@code "operators"} key, in the same order
     */
    protected BsonValue[] declareOperatorArgs(BsonValue... definitions) {
        var marked = new HashSet<String>();
        var unmarked = new HashSet<String>();

        for (var definition : definitions) {
            collectArgs(definition, marked, unmarked);
        }

        var both = new HashSet<>(marked);
        both.retainAll(unmarked);
        if (!both.isEmpty()) {
            LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("The mapping for " + fieldName
                    + " declares the arguments " + both + " with \"operators\": true in one place and without it in another: declare them so everywhere"));
        }

        this.operatorArgs = Set.copyOf(marked);

        var ret = new BsonValue[definitions.length];
        for (var i = 0;i < definitions.length;i++) {
            ret[i] = withoutOperatorsKey(definitions[i]);
        }

        return ret;
    }

    /** @return the arguments the mapping declares with {@code "operators": true} */
    public Set<String> getOperatorArgs() {
        return operatorArgs;
    }

    private void collectArgs(BsonValue value, Set<String> marked, Set<String> unmarked) {
        if (value == null) {
            return;
        }

        if (value.isDocument()) {
            var doc = value.asDocument();

            if (doc.containsKey("$arg")) {
                var name = argName(doc.get("$arg"));

                if (doc.size() == 1) {
                    if (name != null) {
                        unmarked.add(name);
                    }
                    return;
                } else if (doc.size() == 2 && doc.containsKey(OPERATORS_KEY)) {
                    var operators = doc.get(OPERATORS_KEY);
                    if (!operators.isBoolean()) {
                        LambdaUtils.throwsSneakyException(new GraphQLIllegalAppDefinitionException("The mapping for " + fieldName
                                + " has \"operators\" that is not true or false: " + BsonUtils.toJson(doc)));
                    }
                    if (name != null) {
                        (operators.asBoolean().getValue() ? marked : unmarked).add(name);
                    }
                    return;
                }
            }

            doc.values().forEach(v -> collectArgs(v, marked, unmarked));
        } else if (value.isArray()) {
            value.asArray().forEach(v -> collectArgs(v, marked, unmarked));
        }
    }

    /** @return the name of an $arg reference, "name" or ["name", default]; null if it is neither */
    private static String argName(BsonValue arg) {
        if (arg.isString()) {
            return arg.asString().getValue();
        } else if (arg.isArray() && arg.asArray().size() == 2 && arg.asArray().get(0).isString()) {
            return arg.asArray().get(0).asString().getValue();
        }

        return null;
    }

    private static BsonValue withoutOperatorsKey(BsonValue value) {
        if (value == null) {
            return null;
        }

        if (value.isDocument()) {
            var doc = value.asDocument();

            if (doc.size() == 2 && doc.containsKey("$arg") && doc.containsKey(OPERATORS_KEY)) {
                return new BsonDocument("$arg", doc.get("$arg"));
            }

            var ret = new BsonDocument();
            doc.forEach((k, v) -> ret.put(k, withoutOperatorsKey(v)));
            return ret;
        } else if (value.isArray()) {
            var ret = new BsonArray();
            value.asArray().forEach(v -> ret.add(withoutOperatorsKey(v)));
            return ret;
        }

        return value;
    }

    public FieldMapping(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public abstract GraphQLDataFetcher getDataFetcher();

    /**
     * The values a mapping's {@code $arg} references can resolve against: the field's own GraphQL
     * arguments, plus the variables RESTHeart makes available to an app definition.
     *
     * <p>Shared by every mapping on purpose. These two lists had drifted — an aggregation mapping
     * could write {@code {"$arg": "@user._id"}} and a query mapping could not, for no reason an
     * app author could have guessed from the documentation. Which variables an app definition may
     * use is one decision, so it is made in one place.
     *
     * <ul>
     *   <li>the field's arguments, by name</li>
     *   <li>{@code rootDoc} — the parent document, only from path level 2 down
     *       (see <a href="https://restheart.org/docs/mongodb-graphql/#the-rootdoc-argument">the docs</a>)</li>
     * </ul>
     *
     * <p>{@code @}-prefixed variables are deliberately <b>not</b> here. They are resolved by their
     * registered {@link org.restheart.security.VarResolver}, from the request — one source, the
     * same one an ACL predicate uses. This used to hold a hand-copied {@code @user}, which is why
     * {@code @user} was the only variable a mapping could name.
     */
    /**
     * The request being served, or {@code null} where there is none — a unit test, or any caller
     * that builds an execution without one. Without it the {@code @} variables simply do not
     * resolve; nothing else changes.
     */
    protected static Request<?> request(DataFetchingEnvironment env) {
        var graphQLContext = env.getGraphQlContext();
        return graphQLContext == null ? null : graphQLContext.get(REQUEST_CONTEXT_KEY);
    }

    protected static BsonDocument contextValues(DataFetchingEnvironment env) {
        var values = BsonUtils.toBsonDocument(env.getArguments());

        BsonDocument localContext = env.getLocalContext();
        if (localContext == null) {
            return values;
        }

        var rootDoc = localContext.get("rootDoc");
        if (rootDoc != null) {
            values.put("rootDoc", rootDoc);
        }

        return values;
    }

    public BsonValue interpolateFkOperator(BsonDocument source, DataFetchingEnvironment env) throws QueryVariableNotBoundException {
        if (source.containsKey("$fk")) {
            var valueToInterpolate = source.getString("$fk").getValue();
            return getForeignValue(env.getSource(), valueToInterpolate);
        }

        var result = new BsonDocument();

        for (var key : source.keySet()) {
            if (source.get(key).isDocument()) {
                var value = interpolateFkOperator(source.get(key).asDocument(), env);
                result.put(key, value);
            } else if (source.get(key).isArray()) {
                var array = new BsonArray();
                for (var bsonValue : source.get(key).asArray()) {
                    if (bsonValue.isDocument()) {
                        var value = interpolateFkOperator(bsonValue.asDocument(), env);
                        array.add(value);

                    } else {
                        array.add(bsonValue);
                    }
                }

                result.put(key, array);
            } else {
                result.put(key, source.get(key));
            }
        }

        return result;
    }

    public BsonValue getForeignValue(BsonValue sourceDocument, String path) {
        var splitPath = path.split(Pattern.quote("."));
        var current = sourceDocument;

        for (int i = 0;i < splitPath.length;i++) {
            if (current.isDocument() && current.asDocument().containsKey(splitPath[i])) {
                current = current.asDocument().get(splitPath[i]);
            } else if (current.isArray()) {
                try {
                    var index = Integer.parseInt(splitPath[i]);
                    current = current.asArray().get(index);
                } catch (NumberFormatException nfe) {
                    var array = new BsonArray();
                    for (var value : current.asArray()) {
                        var copy = Arrays.copyOfRange(splitPath, i, splitPath.length);
                        array.add(getForeignValue(value, String.join(".", copy)));
                        current = array;
                    }

                    break;
                } catch (IndexOutOfBoundsException ibe) {
                    // return null
                    // if the field is non-nullable, an error will be reported
                    return BsonNull.VALUE;
                }
            } else {
                // return null
                // if the field is non-nullable, an error will be reported
                return BsonNull.VALUE;
            }
        }

        return current;
    }
}
