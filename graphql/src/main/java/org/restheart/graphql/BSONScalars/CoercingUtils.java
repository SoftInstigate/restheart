package org.restheart.graphql.BSONScalars;

public class CoercingUtils {

    static String typeName(Object input) {
        if (input == null) {
            return "null";
        }

        return input.getClass().getSimpleName();
    }
}
