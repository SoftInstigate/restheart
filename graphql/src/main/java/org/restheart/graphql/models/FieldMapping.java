/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2025 SoftInstigate
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
import java.util.regex.Pattern;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import graphql.schema.DataFetchingEnvironment;

public abstract class FieldMapping {
    protected final String fieldName;

    public FieldMapping(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }

    public abstract GraphQLDataFetcher getDataFetcher();

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

        for (int i = 0; i < splitPath.length; i++) {
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
