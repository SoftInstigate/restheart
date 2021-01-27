package org.restheart.graphql.scalars.bsonCoercing;

import graphql.Assert;
import graphql.schema.*;
import java.lang.reflect.Field;
import java.util.*;
import static graphql.Scalars.*;
public class CoercingUtils {

    private static final Map<String, GraphQLScalarType> builtInScalars = Map.ofEntries(
            Map.entry("String", GraphQLString),
            Map.entry("Int", GraphQLInt),
            Map.entry("Float", GraphQLFloat),
            Map.entry("Boolean", GraphQLBoolean),
            Map.entry("Long", GraphQLLong)
    );

    private static final Map<String, Coercing> replacements = Map.ofEntries(
            Map.entry("String", new GraphQLBsonStringCoercing()),
            Map.entry("Int", new GraphQLBsonInt32Coercing()),
            Map.entry("Float", new GraphQLBsonDoubleCoerching()),
            Map.entry("Boolean", new GraphQLBsonBooleanCoercing()),
            Map.entry("Long", new GraphQLBsonInt64Coercing())
    );

    public static final Map<String, Coercing> builtInCoercing = new HashMap<>();


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
                Assert.assertShouldNeverHappen();
            }
        }));
        coercingField.setAccessible(false);
    }

    public static Boolean isANumber(Object input){
        return input instanceof Number || input instanceof String;
    }
}
