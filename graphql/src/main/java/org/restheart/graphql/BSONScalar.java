package org.restheart.graphql;

//import org.restheart.graphql.BSONScalars.GraphQLDateCoercing;
import graphql.GraphQL;
import org.restheart.graphql.BSONScalars.*;
import graphql.schema.GraphQLScalarType;

public class BSONScalar {


    public static final GraphQLScalarType GraphQLBsonObjectId = GraphQLScalarType.newScalar()
            .name("BsonObjectId").description("BSON ObjectId scalar").coercing(new GraphQLBsonObjectIdCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonInt32 = GraphQLScalarType.newScalar()
            .name("BsonInt32").description("BSON Int32 scalar").coercing(new GraphQLBsonInt32Coercing()).build();

    public static final GraphQLScalarType GraphQLBsonInt64 = GraphQLScalarType.newScalar()
            .name("BsonInt64").description("BSON Int64 scalar").coercing(new GraphQLBsonInt64Coercing()).build();

    public static final GraphQLScalarType GraphQLBsonString = GraphQLScalarType.newScalar()
            .name("BsonString").description("BSON String scalar").coercing(new GraphQLBsonStringCoercing()).build();

    public static final GraphQLScalarType GraphQLBsonDocument = GraphQLScalarType.newScalar()
            .name("BsonDocument").description("BSON Document scalar").coercing(new GraphQLBsonDocumentCoercing()).build();

    /**
    public static final GraphQLScalarType GraphQLDate = GraphQLScalarType.newScalar()
            .name("Date").description("Date Scalar").coercing(new GraphQLDateCoercing()).build();
     **/
}
