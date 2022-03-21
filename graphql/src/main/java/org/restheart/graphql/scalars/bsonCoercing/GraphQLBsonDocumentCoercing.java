/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2022 SoftInstigate
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
import graphql.Assert;
import graphql.language.*;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.*;
import org.restheart.utils.BsonUtils;

import java.util.*;

import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonDocumentCoercing implements Coercing<BsonDocument, BsonDocument> {

    @Override
    public BsonDocument serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult instanceof BsonDocument){
            return (BsonDocument) dataFetcherResult;
        } else {
            throw new CoercingSerializeException("Expected type 'BsonDocument' but was '" + typeName(dataFetcherResult) +"'.");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public BsonDocument parseValue(Object input) throws CoercingParseValueException {
        if (input instanceof Map<?,?> map) {
            return BsonUtils.toBsonDocument((Map<String,Object>) map);
        } else {
            throw new CoercingParseValueException("Expected type 'Json Object' but was '" + typeName(input) +"'.");
        }
    }

    @Override
    public BsonDocument parseLiteral(Object AST) throws CoercingParseLiteralException {
        if (AST instanceof ObjectValue objectValue) {
            var fields = objectValue.getObjectFields();
            var parsedValues = new BsonDocument();
            fields.forEach(field ->{
                var parsedValue = parseObjectField(field.getValue(), Collections.emptyMap());
                parsedValues.put(field.getName(), parsedValue);
            });
            return parsedValues;
        } else {
            throw new CoercingParseLiteralException("Expected AST type 'Value' but was '" + typeName(AST) + "'.");
        }
    }

    public BsonValue parseObjectField(Object input, Map<String, Object> variables) throws CoercingParseLiteralException {
        if(!(input instanceof Value)) {
            throw new CoercingParseLiteralException("Expected AST type 'Value' but was '" + typeName(input) + "'.");
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
            values.forEach(value -> bsonValues.add(parseLiteral(value, variables)));
            return bsonValues;
        } else if (input instanceof ObjectValue objectValue) {
            var fields = objectValue.getObjectFields();
            var parsedValues = new BsonDocument();
            fields.forEach(field ->{
                var parsedValue = parseObjectField(field.getValue(), variables);
                parsedValues.put(field.getName(), parsedValue);
            });
            return parsedValues;
        } else {
            return Assert.assertShouldNeverHappen("All types have been covered");
        }
    }
}
