/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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
package org.restheart.graphql.scalars;

import graphql.schema.GraphQLScalarType;
import org.restheart.graphql.scalars.bsonCoercing.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;


public class BsonScalars {

    public static final GraphQLScalarType GraphQLBsonObjectId = GraphQLScalarType.newScalar()
            .name("ObjectId").description("BSON ObjectId scalar").coercing(new GraphQLBsonObjectIdCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonDecimal128 = GraphQLScalarType.newScalar()
            .name("Decimal128").description("BSON Decimal128 scalar").coercing(new GraphQLBsonDecimal128Coercing()).build();

    public static final GraphQLScalarType GraphQLBsonTimestamp = GraphQLScalarType.newScalar()
            .name("Timestamp").description("BSON Timestamp scalar").coercing(new GraphQLBsonTimestampCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonDocument = GraphQLScalarType.newScalar()
            .name("BsonDocument").description("BSON Document scalar").coercing(new GraphQLBsonDocumentCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonDate = GraphQLScalarType.newScalar()
            .name("DateTime").description("BSON DateTime scalar").coercing(new GraphQLBsonDateCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonRegularExpression = GraphQLScalarType.newScalar()
            .name("Regex").description("Bson regular expression scalar").coercing(new GraphQLBsonRegexCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonInt64 = GraphQLScalarType.newScalar()
            .name("Long").description("BSON Int64 scalar (Long)").coercing(new GraphQLBsonInt64Coercing()).build();

    public static final Set<GraphQLScalarType> BSON_SCALARS = Sets.newHashSet(
            GraphQLBsonDocument,
            GraphQLBsonObjectId,
            GraphQLBsonDecimal128,
            GraphQLBsonTimestamp,
            GraphQLBsonDate,
            GraphQLBsonRegularExpression,
            GraphQLBsonInt64
    );

    public static Map<String, GraphQLScalarType> getBsonScalars() {
        var bsonScalars = new HashMap<String, GraphQLScalarType>();
        for (var scalar : BSON_SCALARS) {
            bsonScalars.put(scalar.getName(), scalar);
        }
        return bsonScalars;
    }

    /**
     * Everything an app's SDL may use without declaring it: RESTHeart's BSON scalars, and the
     * {@code @visible} directive that controls which roles see a field (restheart#478).
     *
     * <p>Prepended to every app schema wherever it is parsed, so an app author writes
     * {@code @visible(roles: ["admin"])} without a {@code directive} declaration of their own —
     * and cannot declare a conflicting one.
     */
    public static String getSchemaHeader() {
        var header = new StringBuilder();

        for (var scalar : BSON_SCALARS) {
            header.append("scalar ").append(scalar.getName()).append(" ");
        }

        header.append(VISIBLE_DIRECTIVE_DECLARATION).append(" ");

        return header.toString();
    }

    /** The name of the field-visibility directive, without the {@code @}. */
    public static final String VISIBLE_DIRECTIVE = "visible";

    private static final String VISIBLE_DIRECTIVE_DECLARATION =
            "directive @" + VISIBLE_DIRECTIVE + "(roles: [String!]!) on FIELD_DEFINITION";
}
