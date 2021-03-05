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
import graphql.schema.*;
import org.bson.BsonString;
import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonStringCoercing implements Coercing<BsonString, BsonString> {

    @Override
    public BsonString serialize(Object dataFetcherResult) throws CoercingSerializeException {

        if(dataFetcherResult instanceof BsonString) {
            return (BsonString) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonString' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonString parseValue(Object input) throws CoercingParseValueException {
        return new BsonString((String) CoercingUtils.builtInCoercing.get("String").parseValue(input));
    }

    @Override
    public BsonString parseLiteral(Object AST) throws CoercingParseLiteralException {
        return new BsonString((String) CoercingUtils.builtInCoercing.get("String").parseLiteral(AST));
    }
}
