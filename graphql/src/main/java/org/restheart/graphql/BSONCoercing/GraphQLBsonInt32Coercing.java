package org.restheart.graphql.BSONCoercing;

import graphql.schema.*;
import org.bson.BsonInt32;

import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;


public class GraphQLBsonInt32Coercing implements Coercing<BsonInt32, BsonInt32> {

    @Override
    public BsonInt32 serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult instanceof BsonInt32) {
            return (BsonInt32) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonInt32' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonInt32 parseValue(Object input) {
        return new BsonInt32((Integer) CoercingUtils.builtInCoercing.get("Int").parseValue(input));
    }

    @Override
    public BsonInt32 parseLiteral(Object AST) {
        return new BsonInt32((Integer) CoercingUtils.builtInCoercing.get("Int").parseLiteral(AST));
    }
}
