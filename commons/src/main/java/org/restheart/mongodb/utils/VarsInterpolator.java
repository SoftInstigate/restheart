/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;
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
    public enum VAR_OPERATOR { $var, $arg };
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

                    if (values == null) {
                        return defaultValue;
                    } else {
                        var value = BsonUtils.get(values, name);

                        return value.isPresent() ? value.get() : defaultValue;
                    }
                } else if (v.isString()) { // case { "$var": "name" }, i.e. var without defaul value
                    if (values == null || BsonUtils.get(values, v.asString().getValue()).isEmpty()) {
                        throw new QueryVariableNotBoundException("variable " + v.asString().getValue() + " not bound");
                    }

                    var value = BsonUtils.get(values, v.asString().getValue());

                    return value.isPresent() ? value.get() : null;
                } else {
                    throw new InvalidMetadataException("wrong variable name " + v.toString());
                }
            } else {
                var ret = new BsonDocument();

                for (var key : _obj.keySet()) {
                    ret.put(key, interpolate(operator, _obj.get(key), values));
                }

                return ret;
            }
        } else if (bson.isArray()) {
            var ret = new BsonArray();

            for (var el : bson.asArray().getValues()) {
                if (el.isDocument()) {
                    ret.add(interpolate(operator, el, values));
                } else if (el.isArray()) {
                    ret.add(interpolate(operator, el, values));
                } else {
                    ret.add(el);
                }
            }

            return ret;
        } else {
            return bson;
        }
    }
}
