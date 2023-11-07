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

import org.bson.BsonNull;
import org.bson.BsonObjectId;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
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

public class GraphQLBsonObjectIdCoercing implements Coercing<ObjectId, ObjectId> {
    @Override
    public ObjectId serialize(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingSerializeException {
        if(input == null || input instanceof BsonNull) {
            return null;
        }

        var possibleObjID = convertImpl(input);
        if (possibleObjID == null){
            throw new CoercingSerializeException("Expected type 'ObjectId' but was '" + typeName(input) +"'.");
        } else {
            return possibleObjID;
        }
    }

    @Override
    public ObjectId parseValue(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingParseValueException {
        var possibleObjID = convertImpl(input);
        if (possibleObjID == null) {
            throw new CoercingParseValueException("Expected type 'ObjectId' or a valid 'String' but was '" + typeName(input) + ".");
        } else {
            return possibleObjID;
        }
    }

    @Override
    public ObjectId parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext graphQLContext, Locale locale) throws CoercingParseLiteralException {
        if (input instanceof StringValue stringValue){
            if (!ObjectId.isValid(stringValue.getValue())){
                throw new CoercingParseLiteralException("Input string is not a valid ObjectId");
            } else {
                return new ObjectId(stringValue.getValue());
            }
        } else {
            throw new CoercingParseLiteralException("Expected input type 'StringValue' but was '" + typeName(input) + "'.");
        }
    }

    private ObjectId convertImpl(Object obj){
        if (obj instanceof String){
            String value = (String) obj;
            return ObjectId.isValid(value) ? new ObjectId(value) : null;
        }
        else if(obj instanceof BsonValue){
            BsonValue value = ((BsonValue) obj);
            return value.isObjectId() ? value.asObjectId().getValue() : null;
        }
        else return null;
    }

    @Override
    public Value<?> valueToLiteral(Object input) {
        var value = serialize(input);
        var s = BsonUtils.toJson(new BsonObjectId(value));
        return StringValue.newStringValue(s).build();
    }
}
