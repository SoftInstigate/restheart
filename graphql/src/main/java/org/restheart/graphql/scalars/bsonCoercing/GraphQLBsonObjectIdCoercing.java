/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2021 SoftInstigate
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
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonValue;
import org.bson.types.ObjectId;

import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonObjectIdCoercing implements Coercing<ObjectId, ObjectId> {


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
    public ObjectId serialize(Object dataFetcherResult) throws CoercingSerializeException {
        ObjectId possibleObjID = convertImpl(dataFetcherResult);
        if(possibleObjID == null){
            throw new CoercingSerializeException(
                    "Expected type 'ObjectId' but was '" + typeName(dataFetcherResult) +"'."
            );
        }
        return possibleObjID;
    }

    @Override
    public ObjectId parseValue(Object input) throws CoercingParseValueException {
        ObjectId possibleObjID = convertImpl(input);
        if (possibleObjID == null) {
            throw new CoercingParseValueException(
                    "Expected type 'ObjectId' or a valid 'String' but was '" + typeName(input) + "."
            );
        }
        return possibleObjID;
    }

    @Override
    public ObjectId parseLiteral(Object AST) throws CoercingParseLiteralException {
        if (!(AST instanceof StringValue)){
            throw new CoercingParseLiteralException(
                    "Expected AST type 'StringValue' but was '" + typeName(AST) + "'."
            );
        }
        if(!ObjectId.isValid((((StringValue) AST).getValue()))){
            throw new CoercingParseLiteralException(
                    "Input string is not a valid ObjectId"
            );
        }
        return new ObjectId(((StringValue) AST).getValue());
    }
}
