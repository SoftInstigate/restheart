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
package org.restheart.graphql.scalars.bsonCoercing;

import java.util.Locale;

import org.bson.BsonInt32;
import org.bson.BsonNull;
import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

public class GraphQLBsonInt32Coercing implements Coercing<Integer, Integer> {
    @Override
    public Integer serialize(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingSerializeException {
        if(input == null || input instanceof BsonNull) {
            return null;
        } else if(input instanceof BsonInt32 bsonInt32) {
            return bsonInt32.getValue();
        } else if (input instanceof Integer integer) {
            return integer;
        } else {
            throw new CoercingSerializeException("Expected types 'Integer' or 'BsonInt32' but was '" + typeName(input) +"'.");
        }
    }

    @Override
    public Integer parseValue(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingParseValueException {
        return (Integer) CoercingUtils.builtInCoercing.get("Int").parseValue(input, graphQLContext, locale);
    }

    @Override
    public Integer parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
        return (Integer) CoercingUtils.builtInCoercing.get("Int").parseLiteral(input, variables, graphQLContext, locale);
    }

    @Override
    public Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
        return CoercingUtils.builtInCoercing.get("Int").valueToLiteral(input, graphQLContext, locale);
    }
}
