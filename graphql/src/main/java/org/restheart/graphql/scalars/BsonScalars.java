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
package org.restheart.graphql.scalars;
import graphql.Assert;
import graphql.schema.GraphQLScalarType;
import org.restheart.graphql.scalars.bsonCoercing.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;


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

    public static Map<String, GraphQLScalarType> getBsonScalars(){
        try{
            Field[] scalarFields =  BsonScalars.class.getDeclaredFields();
            Map<String, GraphQLScalarType> bsonScalars = new HashMap<>();
            for(Field scalar: scalarFields){
                bsonScalars.put(scalar.getName(), (GraphQLScalarType) scalar.get(null));
            }
            return bsonScalars;
        } catch (IllegalAccessException e){
            return Assert.assertShouldNeverHappen();
        }

    }

    public static String getBsonScalarHeader(){
        try{
            Field[] scalarFields = BsonScalars.class.getDeclaredFields();
            String header = "";
            for (Field scalar: scalarFields){
                header += "scalar " + ((GraphQLScalarType) scalar.get(null)).getName() +" ";
            }
            return header;
        } catch (IllegalAccessException e){
            return Assert.assertShouldNeverHappen();
        }
    }




}
