/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2024 SoftInstigate
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
import java.util.Map;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;
import org.restheart.utils.BsonUtils;

import graphql.Assert;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

public class GraphQLBsonDocumentCoercing implements Coercing<BsonDocument, BsonDocument> {
    @Override
    public BsonDocument serialize(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingSerializeException {
        if(input == null || input instanceof BsonNull) {
            return null;
        } else if(input instanceof BsonDocument bsonDocument){
            return bsonDocument;
        } else {
            throw new CoercingSerializeException("Expected type 'BsonDocument' but was '" + typeName(input) +"'.");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public BsonDocument parseValue(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingParseValueException {
        if (input instanceof Map<?,?> map) {
            return BsonUtils.toBsonDocument((Map<String,Object>) map);
        } else {
            throw new CoercingParseValueException("Expected type 'Json Object' but was '" + typeName(input) +"'.");
        }
    }

    @Override
    public BsonDocument parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
        if (input instanceof ObjectValue objectValue) {
            var fields = objectValue.getObjectFields();
            var parsedValues = new BsonDocument();
            fields.forEach(field ->{
                var parsedValue = parseObjectField(field.getValue(), variables, graphQLContext, locale);
                parsedValues.put(field.getName(), parsedValue);
            });
            return parsedValues;
        } else {
            throw new CoercingParseLiteralException("Expected input type 'Value' but was '" + typeName(input) + "'.");
        }
    }

    public BsonValue parseObjectField(Object input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
        if(!(input instanceof Value)) {
            throw new CoercingParseLiteralException("Expected input type 'Value' but was '" + typeName(input) + "'.");
        } else if (input instanceof StringValue stringValue) {
            return new BsonString(stringValue.getValue());
        } else if (input instanceof IntValue intValue) {
            return new BsonInt32(intValue.getValue().intValue());
        } else if (input instanceof FloatValue floatValue) {
            return new BsonDouble(floatValue.getValue().doubleValue());
        } else if (input instanceof BooleanValue booleanValue) {
            return new BsonBoolean(booleanValue.isValue());
        } else if (input instanceof NullValue) {
            return BsonNull.VALUE;
        } else if (input instanceof EnumValue enumValue) {
            return new BsonString(enumValue.getName()); // maybe?
        } else if (input instanceof VariableReference variableReference){
            var varName = variableReference.getName();
            return (BsonValue) variables.get(varName);
        } else if (input instanceof ArrayValue arrayValue) {
            var values = arrayValue.getValues();
            var bsonValues = new BsonArray();
            values.forEach(value -> bsonValues.add(parseLiteral(value, variables, graphQLContext, locale)));
            return bsonValues;
        } else if (input instanceof ObjectValue objectValue) {
            var fields = objectValue.getObjectFields();
            var parsedValues = new BsonDocument();
            fields.forEach(field ->{
                var parsedValue = parseObjectField(field.getValue(), variables, graphQLContext, locale);
                parsedValues.put(field.getName(), parsedValue);
            });
            return parsedValues;
        } else {
            return Assert.assertShouldNeverHappen("All types have been covered");
        }
    }

    @Override
    public Value<?> valueToLiteral(Object input, GraphQLContext graphQLContext, Locale locale) {
        var value = parseValue(input, graphQLContext, locale);
        var s = BsonUtils.toJson(value);
        return StringValue.newStringValue(s).build();
    }
}
