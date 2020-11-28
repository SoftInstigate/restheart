package org.restheart.graphql.BSONScalars;

import graphql.schema.Coercing;
import org.bson.BsonType;

import static org.restheart.graphql.BSONScalar.*;

public class CoercingUtils {

    static String typeName(Object input) {
        if (input == null) {
            return "null";
        }

        return input.getClass().getSimpleName();
    }

    static Boolean IsANumber(Object input){
        return (input instanceof Number || input instanceof String);
    }

    static Coercing getTypeCoercing(BsonType type){
        Coercing coercing;
        switch (type){
            case INT32: coercing = GraphQLBsonInt32.getCoercing(); break;
            case INT64: coercing = GraphQLBsonInt64.getCoercing(); break;
            case OBJECT_ID: coercing = GraphQLBsonObjectId.getCoercing(); break;
            case DECIMAL128: coercing = GraphQLBsonDecimal128.getCoercing(); break;
            case DOUBLE: coercing = GraphQLBsonDouble.getCoercing(); break;
            case STRING: coercing = GraphQLBsonString.getCoercing(); break;
            case DATE_TIME: coercing = GraphQLBsonDate.getCoercing(); break;
            case TIMESTAMP: coercing = GraphQLBsonTimestamp.getCoercing(); break;
            case DOCUMENT: coercing = GraphQLBsonDocument.getCoercing(); break;
            case ARRAY: coercing = GraphQLBsonArray.getCoercing(); break;
            case BOOLEAN: coercing = GraphQLBsonBoolean.getCoercing(); break;

            default:
                throw new IllegalStateException("Unexpected type " + type);
        }
        return coercing;
    }
}
