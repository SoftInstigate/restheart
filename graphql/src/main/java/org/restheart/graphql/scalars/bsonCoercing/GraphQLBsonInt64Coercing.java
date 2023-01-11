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

import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonInt64;
import org.bson.BsonNull;

import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonInt64Coercing implements Coercing<Long, Long> {


    @Override
    public Long serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult == null || dataFetcherResult instanceof BsonNull) {
            return null;
        } else if (dataFetcherResult instanceof BsonInt64 bsonInt64) {
            return bsonInt64.getValue();
        } else if (dataFetcherResult instanceof Long value) {
            return value;
        }else {
            throw new CoercingParseValueException("Expected types 'Long' or 'BsonInt64' but was '" + typeName(dataFetcherResult) + ".");
        }
    }

    @Override
    public Long parseValue(Object input) {
        return (Long) CoercingUtils.builtInCoercing.get("Long").parseValue(input);
    }

    @Override
    public Long parseLiteral(Object AST) {
        return (Long) CoercingUtils.builtInCoercing.get("Long").parseLiteral(AST);
    }
}
