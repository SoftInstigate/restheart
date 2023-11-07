/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2023 SoftInstigate
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

import org.bson.BsonDouble;
import org.bson.BsonNull;
import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

public class GraphQLBsonDoubleCoerching implements Coercing<Double, Double> {
    @Override
    public Double serialize(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingSerializeException {
        if(input == null || input instanceof BsonNull) {
            return null;
        } else if(input instanceof BsonDouble bsonString) {
            return bsonString.getValue();
        } else if (input instanceof Double value) {
            return value;
        } else {
            throw new CoercingSerializeException("Expected types 'Double' or 'BsonDouble' but was '" + typeName(input) + "'.");
        }
    }

    @Override
    public Double parseValue(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingParseValueException {
        return (Double) CoercingUtils.builtInCoercing.get("Float").parseValue(input);
    }

    @Override
    public Double parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
        return (Double) CoercingUtils.builtInCoercing.get("Float").parseLiteral(input);
    }

    @Override
    public Value<?> valueToLiteral(Object input) {
        return CoercingUtils.builtInCoercing.get("Float").valueToLiteral(input);
    }
}
