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
package org.restheart.graphql.scalars.bsonCoercing;

import java.util.Locale;

import org.bson.BsonNull;
import org.bson.BsonTimestamp;
import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;
import org.restheart.utils.BsonUtils;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

public class GraphQLBsonTimestampCoercing implements Coercing<BsonTimestamp, BsonTimestamp> {
    @Override
    public BsonTimestamp serialize(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingSerializeException {
        if(input == null || input instanceof BsonNull) {
            return null;
        } else if (input instanceof BsonTimestamp bsonTimestamp){
            return bsonTimestamp;
        } else {
            throw new CoercingSerializeException("Expected type 'BsonTimestamp' but was '" + typeName(input) +"'.");
        }
    }

    @Override
    public BsonTimestamp parseValue(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingParseValueException {
        var timestamp =  (Long) CoercingUtils.builtInCoercing.get("Long").parseValue(input, graphQLContext, locale);
        return new BsonTimestamp(timestamp);
    }

    @Override
    public BsonTimestamp parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
        var timestamp =  (Long) CoercingUtils.builtInCoercing.get("Long").parseLiteral(input, variables, graphQLContext, locale);
        return new BsonTimestamp(timestamp);
    }

    @Override
    public Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
        var value = serialize(input, graphQLContext, locale);
        var s = BsonUtils.toJson(value);
        return StringValue.newStringValue(s).build();
    }
}
