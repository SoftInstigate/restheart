package org.restheart.graphql;
import org.restheart.graphql.BSONScalars.*;
import graphql.schema.GraphQLScalarType;


public class BSONScalar {


    public static final GraphQLScalarType GraphQLBsonObjectId = GraphQLScalarType.newScalar()
            .name("ObjectId").description("BSON ObjectId scalar").coercing(new GraphQLBsonObjectIdCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonInt32 = GraphQLScalarType.newScalar()
            .name("BsonInt32").description("BSON Int32 scalar").coercing(new GraphQLBsonInt32Coercing()).build();

    public static final GraphQLScalarType GraphQLBsonInt64 = GraphQLScalarType.newScalar()
            .name("BsonInt64").description("BSON Int64 scalar").coercing(new GraphQLBsonInt64Coercing()).build();

    public static final GraphQLScalarType GraphQLBsonDecimal128 = GraphQLScalarType.newScalar()
            .name("BsonDecimal128").description("BSON Decimal128 scalar").coercing(new GraphQLBsonDecimal128Coercing()).build();

    public static final GraphQLScalarType GraphQLBsonDouble = GraphQLScalarType.newScalar()
            .name("BsonDouble").description("BSON Double scalar").coercing(new GraphQLBsonDoubleCoerching()).build();

    public static final GraphQLScalarType GraphQLBsonString = GraphQLScalarType.newScalar()
           .name("BsonString").description("BSON String scalar").coercing(new GraphQLBsonStringCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonBoolean = GraphQLScalarType.newScalar()
            .name("BsonBoolean").description("BSON Boolean scalar").coercing(new GraphQLBsonBooleanCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonDate = GraphQLScalarType.newScalar()
            .name("BsonDate").description("BSON Date scalar").coercing(new GraphQLBsonDateCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonTimestamp = GraphQLScalarType.newScalar()
            .name("BsonTimestamp").description("BSON Timestamp scalar").coercing(new GraphQLBsonTimestampCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonObject = GraphQLScalarType.newScalar()
            .name("BsonObject").description("BSON Object scalar").coercing(new GraphQLBsonObjectCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonArray = GraphQLScalarType.newScalar()
            .name("BsonArray").description("BSON Array scalar").coercing(new GraphQLBsonArrayCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonDocument = GraphQLScalarType.newScalar()
            .name("BsonDocument").description("BSON Document scalar").coercing(new GraphQLBsonDocumentCoercing()).build();


}
