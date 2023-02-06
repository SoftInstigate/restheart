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
import graphql.schema.*;

import org.bson.BsonNull;
import org.bson.BsonString;
import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

@SuppressWarnings("deprecation")
public class GraphQLBsonStringCoercing implements Coercing<String, String> {
    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult == null || dataFetcherResult instanceof BsonNull) {
            return null;
        } else if(dataFetcherResult instanceof BsonString bsonString) {
            return bsonString.getValue();
        } else if (dataFetcherResult instanceof String string) {
            return string;
        } else {
            throw new CoercingSerializeException("Expected types 'String' or 'BsonString' but was '" + typeName(dataFetcherResult) +"'.");
        }
    }

    @Override
    public String parseValue(Object input) throws CoercingParseValueException {
        return (String) CoercingUtils.builtInCoercing.get("String").parseValue(input);
    }

    @Override
    public String parseLiteral(Object AST) throws CoercingParseLiteralException {
        return (String) CoercingUtils.builtInCoercing.get("String").parseLiteral(AST);
    }
}
