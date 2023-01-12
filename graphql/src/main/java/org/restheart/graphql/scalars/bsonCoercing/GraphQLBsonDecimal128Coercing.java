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

import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonDecimal128;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.bson.types.Decimal128;

import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

@SuppressWarnings("deprecation")
public class GraphQLBsonDecimal128Coercing implements Coercing<BsonDecimal128, Decimal128> {
    @Override
    public Decimal128 serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult == null || dataFetcherResult instanceof BsonNull) {
            return null;
        }

        var possibleDecimal = convertImpl(dataFetcherResult);
        if (possibleDecimal == null){
            throw new CoercingSerializeException("Expected type 'Decimal128' but was '" + typeName(dataFetcherResult) +"'.");
        } else {
            return possibleDecimal;
        }
    }

    @Override
    public BsonDecimal128 parseValue(Object input) throws CoercingParseValueException {
        var possibleDecimal = convertImpl(input);
        if (possibleDecimal == null){
            throw new CoercingParseValueException("Expected type 'Decimal128' but was '" + typeName(input) +"'.");
        } else {
            return new BsonDecimal128(possibleDecimal);
        }
    }

    @Override
    public BsonDecimal128 parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof StringValue || input instanceof IntValue || input instanceof FloatValue){
            String value = null;
            if (input instanceof IntValue intValue){
                value = intValue.getValue().toString();
            } else if (input instanceof FloatValue floatValue){
                value = floatValue.getValue().toString();
            } else if (input instanceof StringValue stringValue){
                value = stringValue.getValue();
            }

            var dec = Decimal128.parse(value);

            if(dec.isNaN()){
                throw new CoercingParseLiteralException("Expected value to be a number but it was '" + dec.toString() + "'");
            } else {
                return new BsonDecimal128(dec);
            }
        } else {
            throw  new CoercingParseLiteralException("Expected AST type 'StringValue' but was '" + typeName(input) + "'.");
        }
    }

    private Decimal128 convertImpl(Object obj){
        if (isANumber(obj)){
            var value = Decimal128.parse(obj.toString());
            return value.isNaN() ? value : null;
        } else if (obj instanceof BsonValue bsonValue){
            return bsonValue.isDecimal128() ? bsonValue.asDecimal128().getValue() : null;
        } else {
            return null;
        }
    }

    private boolean isANumber(Object input) {
        return input instanceof Number || input instanceof String;
    }
}
