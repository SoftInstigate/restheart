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

import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonInt64;

import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonInt64Coercing implements Coercing<BsonInt64, BsonInt64> {


    @Override
    public BsonInt64 serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof BsonInt64){
            return (BsonInt64) dataFetcherResult;
        }
        throw new CoercingParseValueException(
                "Expected type 'Long' but was '" + typeName(dataFetcherResult) + "."
        );
    }

    @Override
    public BsonInt64 parseValue(Object input) {
        return new BsonInt64((Long) CoercingUtils.builtInCoercing.get("Long").parseValue(input));
    }

    @Override
    public BsonInt64 parseLiteral(Object AST) {
        return new BsonInt64((Long) CoercingUtils.builtInCoercing.get("Long").parseLiteral(AST));
    }
}
