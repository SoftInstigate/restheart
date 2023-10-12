/*-
 * ========================LICENSE_START=================================
 * restheart-commons
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
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
 * such as <code>{ <operator>: "name"}</code>, and replacing placeholders with provided values.
 * The class facilitates the dynamic substitution of placeholders in BSON documents and arrays.
 */
public class VarOperatorsInterpolator {
    public enum OPERATOR { $var, $arg };
    /**
     * @param bson
     * @param bson the BsonDocument or BsonArray containing variables
     * @param values the BsonDocument containing the values of the variables, as RequestContext.getAggregationVars()
     * @return the BsonValue where the variables <code>{"$var": "name" }</code>
     * or <code>{"$var": [ "name", "defaultValue" ] }</code> are
     * replaced with the values defined in the avars BsonDocument
     *
     * <br><br>Example: if <code>bson = {"$var": "name" }</code> and <code>avars = { "name": "Andrea"}</code>
     * then it returns  <code>{"$var": "Andrea" }</code>
     * @throws org.restheart.exchange.InvalidMetadataException
     * @throws org.restheart.exchange.QueryVariableNotBoundException
     */
    public static BsonValue interpolate(OPERATOR operator, BsonValue bson, BsonDocument values) throws InvalidMetadataException, QueryVariableNotBoundException {
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
