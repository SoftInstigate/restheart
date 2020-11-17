package org.restheart.graphql;

//import org.restheart.graphql.BSONScalars.GraphQLDateCoercing;
import org.restheart.graphql.BSONScalars.GraphQLObjectIdCoercing;
import graphql.schema.GraphQLScalarType;

public class BSONScalar {


    public static final GraphQLScalarType GraphQLObjectId = GraphQLScalarType.newScalar()
            .name("ObjectId").description("ObjectId Scalar").coercing(new GraphQLObjectIdCoercing()).build();

    /**
    public static final GraphQLScalarType GraphQLDate = GraphQLScalarType.newScalar()
            .name("Date").description("Date Scalar").coercing(new GraphQLDateCoercing()).build();
     **/
}
