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
import java.util.regex.Pattern;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.exchange.Request;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.utils.BsonUtils;
import graphql.schema.DataFetchingEnvironment;

public abstract class FieldMapping {
    /**
     * Where {@code GraphQLService} puts the request it is serving, so a mapping can resolve the
     * {@code @} variables that depend on it (restheart#727). graphql-java's {@code GraphQLContext}
     * holds arbitrary objects; {@code localContext} is a {@code BsonDocument} and cannot.
     */
    public static final String REQUEST_CONTEXT_KEY = "restheart-request";

    protected final String fieldName;

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
