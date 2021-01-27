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
import org.bson.BsonRegularExpression;

import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonRegexCoercing implements Coercing<BsonRegularExpression, BsonRegularExpression> {
    @Override
    public BsonRegularExpression serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult instanceof BsonRegularExpression){
            return (BsonRegularExpression) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonRegularExpression' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonRegularExpression parseValue(Object input) throws CoercingParseValueException {

        if(input instanceof String){
            return new BsonRegularExpression((String) input);
        }
        throw new CoercingParseValueException(
                "Expected type 'BsonRegularExpression' but was '" + typeName(input) +"'."
        );
    }

    @Override
    public BsonRegularExpression parseLiteral(Object AST) throws CoercingParseLiteralException {
        if(!(AST instanceof StringValue)){
            throw new CoercingParseLiteralException(
                    "Expected AST type 'StringValue' but was '" + typeName(AST) + "'."
            );
        }
        return new BsonRegularExpression(((StringValue) AST).getValue());
    }
}
