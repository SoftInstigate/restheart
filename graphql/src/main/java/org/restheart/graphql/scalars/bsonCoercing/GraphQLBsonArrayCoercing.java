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

import graphql.language.ArrayValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonArray;
import org.bson.BsonValue;
import org.restheart.graphql.scalars.BsonScalars;

import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonArrayCoercing implements Coercing<BsonArray, BsonArray> {

    @Override
    public BsonArray serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult instanceof BsonArray){

            return (BsonArray) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonArray' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonArray parseValue(Object input) throws CoercingParseValueException {
        BsonArray parsedValues = new BsonArray();
        if(input.getClass().isArray()){
            for (Object element : ((Object[]) input)){
                parsedValues.add((BsonValue) BsonScalars.GraphQLBsonDocument.getCoercing().parseValue(element));
            }
            return parsedValues;
        }
        throw new CoercingParseValueException(
                "Expected type 'BsonArray' but was '" + typeName(input) +"'."
        );
    }

    @Override
    public BsonArray parseLiteral(Object AST) throws CoercingParseLiteralException {
        if(!(AST instanceof ArrayValue)){
            throw new CoercingParseValueException(
                    "Expected AST type 'ArrayValue' but was '" + typeName(AST) + "'."
            );
        }
        BsonArray parsedValues = new BsonArray();
        ((ArrayValue) AST).getValues().forEach(value -> {
            parsedValues.add((BsonValue) BsonScalars.GraphQLBsonDocument.getCoercing().parseLiteral(value));
        });
        return parsedValues;
    }
}
