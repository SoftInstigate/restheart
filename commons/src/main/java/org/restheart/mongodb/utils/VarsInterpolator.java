/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.mongodb.utils;

import java.util.Optional;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.security.AclVarsInterpolator;
import org.restheart.exchange.Request;
import org.restheart.utils.BsonUtils;

/**
 * Utility class for interpolating variables within a BsonDocument or BsonArray by using a specific format,
 * such as <code>{ [operator]: "name"}</code>, and replacing placeholders with provided values.
 * The class facilitates the dynamic substitution of placeholders in BSON documents and arrays.
 * 
 * <p>This class is a core component of RESTHeart's dynamic query and aggregation system, allowing
 * BSON documents to contain variable placeholders that are replaced with actual values at runtime.
 * It supports both simple variable substitution and variables with default values.</p>
 * 
 * <p>Variable formats supported:</p>
 * <ul>
 *   <li>{@code { "$var": "variableName" }} - Simple variable reference</li>
 *   <li>{@code { "$var": ["variableName", defaultValue] }} - Variable with default value</li>
 *   <li>{@code { "$arg": "argumentName" }} - GraphQL argument reference</li>
 *   <li>{@code { "$arg": ["argumentName", defaultValue] }} - GraphQL argument with default</li>
 * </ul>
 * 
 * <p>The interpolation process:</p>
 * <ol>
 *   <li>Traverses the BSON structure recursively</li>
 *   <li>Identifies variable placeholders by the operator pattern</li>
 *   <li>Looks up values in the provided values document</li>
 *   <li>Replaces placeholders with actual values or defaults</li>
 *   <li>Throws QueryVariableNotBoundException if required variables are missing</li>
 * </ol>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Input document with variables
 * BsonDocument query = new BsonDocument("name", new BsonDocument("$var", new BsonString("userName")))
 *     .append("age", new BsonDocument("$var", 
 *         new BsonArray(Arrays.asList(new BsonString("userAge"), new BsonInt32(18)))));
 * 
 * // Values to interpolate
 * BsonDocument values = new BsonDocument("userName", new BsonString("John"));
 * 
 * // Interpolate
 * BsonValue result = VarsInterpolator.interpolate(VAR_OPERATOR.$var, query, values);
 * // Result: { "name": "John", "age": 18 } (age uses default since userAge not provided)
 * }</pre>
 * 
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class VarsInterpolator {
    /**
     * Enum defining the variable operators supported by the interpolator.
     * <ul>
     *   <li>{@code $var} - Used for standard query and aggregation variable interpolation</li>
     *   <li>{@code $arg} - Used for GraphQL argument interpolation in mappings</li>
     * </ul>
     */
    public enum VAR_OPERATOR {$var, $arg}
    ;

    /**
     * Interpolates variables in a BSON structure by replacing variable placeholders with actual values.
     * <p>
     * This method recursively traverses the BSON structure (document or array) and replaces
     * any variable placeholders with their corresponding values from the provided values document.
     * It supports two formats of variable references:
     * </p>
     * <ol>
     *   <li><b>Simple variable:</b> {@code {"$var": "variableName"}} - Replaced with the value
     *       of "variableName" from the values document. Throws exception if not found.</li>
     *   <li><b>Variable with default:</b> {@code {"$var": ["variableName", defaultValue]}} - 
     *       Replaced with the value of "variableName" if found, otherwise uses defaultValue.</li>
     * </ol>
     * 
     * <p>The method handles nested structures, preserving the original structure while only
     * replacing the variable placeholders. Variable names can use dot notation to access
     * nested values (e.g., "user.name" accesses the "name" field within "user").</p>
     * 
     * <p>Example:</p>
     * <pre>{@code
     * // Input: {"filter": {"status": {"$var": "status"}}, "limit": {"$var": ["limit", 10]}}
     * // Values: {"status": "active"}
     * // Result: {"filter": {"status": "active"}, "limit": 10}
     * }</pre>
     * 
     * @param operator the variable operator to use ($var for queries/aggregations, $arg for GraphQL)
     * @param bson the BsonDocument or BsonArray containing variable placeholders to interpolate
     * @param values the BsonDocument containing the variable values, typically from 
     *               RequestContext.getAggregationVars() or GraphQL arguments
     * @return a new BsonValue with all variable placeholders replaced by their actual values.
     *         The original bson structure is not modified
     * @throws InvalidMetadataException if a variable reference has invalid format (e.g., non-string
     *         variable name, array with wrong number of elements)
     * @throws QueryVariableNotBoundException if a required variable (without default) is not found
     *         in the values document
     */
    public static BsonValue interpolate(VAR_OPERATOR operator, BsonValue bson, BsonDocument values) throws InvalidMetadataException, QueryVariableNotBoundException {
        return interpolate(operator, bson, values, null);
    }

    /**
     * Same as {@link #interpolate(VAR_OPERATOR, BsonValue, BsonDocument)}, additionally
     * dispatching any registered {@link CustomOperator} (e.g. {@code $vectorize}) found
     * while walking the structure, and passing {@code request} through to it — needed
     * for a multi-tenant operator implementation to resolve its own per-request
     * overrides. See {@link CustomOperator} for the resolution order and failure
     * semantics of custom operators.
     *
     * @param request the current request, passed through to any registered
     *        {@link CustomOperator}; may be {@code null} for contexts without one (e.g.
     *        GraphQL mapping interpolation)
     * @since 9.10.0
     */
    /**
     * The value bound to {@code name} (restheart#727).
     *
     * <p>A name a {@link org.restheart.security.VarResolver} claims is answered by that resolver
     * and by nothing else — the supplied values are not even consulted for it. That precedence is
     * the security-relevant part, not a preference: an aggregation's {@code $var}s are bound from
     * {@code avars}, which come from the caller's own query string, so consulting those first
     * would let {@code ?avars={"@user":{"userid":"admin"}}} override the identity a pipeline
     * written as {@code {"$var": "@user.userid"}} is meant to scope itself by. A {@code @} name is
     * resolved by the server or not at all.
     *
     * <p>Which is also why a resolver with no request to work from leaves the variable unbound
     * rather than falling back: the fallback would be exactly the value a caller supplied.
     *
     * <p>Everything else resolves from the supplied values as before, and a {@code @} name nobody
     * registered stays unbound rather than becoming the literal string — a mistyped
     * {@code @usr._id} must fail, not match a document.
     */
    static Optional<BsonValue> lookup(BsonDocument values, String name, Request<?> request) {
        if (name != null && name.startsWith("@")) {
            return AclVarsInterpolator.isRegisteredVar(name)
                    ? AclVarsInterpolator.resolveRegisteredVar(request, name)
                    : Optional.empty();
        }

        return values == null ? Optional.empty() : BsonUtils.get(values, name);
    }

    public static BsonValue interpolate(VAR_OPERATOR operator, BsonValue bson, BsonDocument values, Request<?> request) throws InvalidMetadataException, QueryVariableNotBoundException {
        if (bson == null) {
            return null;
        }

        if (bson.isDocument()) {
            var _obj = bson.asDocument();

            if (_obj.size() == 1 && _obj.get(operator.name()) != null) {
                var v = _obj.get(operator.name());

                if (v.isArray() && v.asArray().size() == 2) { // case {"$var": [ "name", "value" ] }, i.e. var with default value
                    var _name = v.asArray().get(0);
                    var defaultValue = v.asArray().get(1);

                    if (!_name.isString()) {
                        throw new InvalidMetadataException("wrong variable name " + v.toString());
                    }

                    var name = _name.asString().getValue();

                    return lookup(values, name, request).orElse(defaultValue);
                } else if (v.isString()) { // case { "$var": "name" }, i.e. var without defaul value
                    var name = v.asString().getValue();

                    return lookup(values, name, request)
                            .orElseThrow(() -> new QueryVariableNotBoundException("variable " + name + " not bound"));
                } else {
                    throw new InvalidMetadataException("wrong variable name " + v.toString());
                }
            }

            if (_obj.size() == 1) {
                var custom = customOperator(_obj);
                if (custom.isPresent()) {
                    var op = custom.get();
                    var rawArg = _obj.get("$" + op.name());
                    var resolvedArg = interpolate(operator, rawArg, values, request);
                    return op.resolve(request, resolvedArg);
                }
            }

            // multi-key documents, and single-key documents matching neither the
            // $var/$arg operator nor a registered custom operator (e.g. a genuine
            // MongoDB operator document like {"$gt": 5})
            var ret = new BsonDocument();

            for (var key : _obj.keySet()) {
                ret.put(key, interpolate(operator, _obj.get(key), values, request));
            }

            return ret;
        } else if (bson.isArray()) {
            var ret = new BsonArray();

            for (var el : bson.asArray().getValues()) {
                if (el.isDocument()) {
                    ret.add(interpolate(operator, el, values, request));
                } else if (el.isArray()) {
                    ret.add(interpolate(operator, el, values, request));
                } else {
                    ret.add(el);
                }
            }

            return ret;
        } else {
            return bson;
        }
    }

    /**
     * @param obj a single-key document
     * @return the registered {@link CustomOperator} if {@code obj}'s sole key is a
     *         {@code $}-prefixed key matching one, else empty — including when the sole
     *         key isn't {@code $}-prefixed at all, or is but matches no registered
     *         operator (e.g. a genuine single-key MongoDB operator document like
     *         {@code {"$gt": 5}}).
     */
    private static Optional<CustomOperator> customOperator(BsonDocument obj) {
        var soleKey = obj.keySet().iterator().next();
        if (!soleKey.startsWith("$")) {
            return Optional.empty();
        }
        return CustomOperatorRegistryImpl.getInstance().operator(soleKey.substring(1));
    }
}
