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

import graphql.Assert;
import graphql.schema.*;
import java.lang.reflect.Field;
import java.util.*;
import static graphql.Scalars.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoercingUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoercingUtils.class);

    private static final Map<String, GraphQLScalarType> builtInScalars = Map.ofEntries(
            Map.entry("String", GraphQLString),
            Map.entry("Int", GraphQLInt),
            Map.entry("Float", GraphQLFloat),
            Map.entry("Boolean", GraphQLBoolean),
            Map.entry("Long", GraphQLLong)
    );

    private static final Map<String, Coercing<?,?>> replacements = Map.ofEntries(
            Map.entry("String", new GraphQLBsonStringCoercing()),
            Map.entry("Int", new GraphQLBsonInt32Coercing()),
            Map.entry("Float", new GraphQLBsonDoubleCoerching()),
            Map.entry("Boolean", new GraphQLBsonBooleanCoercing()),
            Map.entry("Long", new GraphQLBsonInt64Coercing())
    );


    public static final Map<String, Coercing<?,?>> builtInCoercing = new HashMap<>();


    static String typeName(Object input) {
        if (input == null) {
            return "null";
        }
        return input.getClass().getSimpleName();
    }

    static void saveBuiltInCoercing(){
        builtInScalars.forEach(((s, graphQLScalarType) -> {
            builtInCoercing.put(s, graphQLScalarType.getCoercing());
        }));
    }

    public static void replaceBuiltInCoercing() throws NoSuchFieldException, IllegalAccessException {
        Field coercingField =  GraphQLScalarType.class.getDeclaredField("coercing");
        coercingField.setAccessible(true);
        saveBuiltInCoercing();
        replacements.forEach(((s, coercing) -> {
            try {
                coercingField.set(builtInScalars.get(s), coercing);
            } catch (IllegalAccessException e) {
                LOGGER.error("Error replacing built-in scalars", e);
                Assert.assertShouldNeverHappen();
            }
        }));
        coercingField.setAccessible(false);
    }

    public static Boolean isANumber(Object input){
        return input instanceof Number || input instanceof String;
    }
}
