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
import graphql.schema.CoercingSerializeException;
import org.bson.BsonBoolean;
import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonBooleanCoercing implements Coercing<BsonBoolean, BsonBoolean> {

    @Override
    public BsonBoolean serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof BsonBoolean bsonBoolean) {
            return bsonBoolean;
        } else {
            throw new CoercingSerializeException("Expected type 'BsonBoolean' but was '" + typeName(dataFetcherResult) + "'.");
        }
    }

    @Override
    public BsonBoolean parseValue(Object input) {
        return new BsonBoolean((Boolean) CoercingUtils.builtInCoercing.get("Boolean").parseValue(input));
    }

    @Override
    public BsonBoolean parseLiteral(Object AST) {
        return new BsonBoolean((Boolean) CoercingUtils.builtInCoercing.get("Boolean").parseLiteral(AST));
    }
}
