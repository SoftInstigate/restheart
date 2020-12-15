package org.restheart.graphql.BSONCoercing;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonInt64;

import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;

public class GraphQLBsonInt64Coercing implements Coercing<BsonInt64, BsonInt64> {


    @Override
    public BsonInt64 serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof BsonInt64){
            return (BsonInt64) dataFetcherResult;
        }
        throw new CoercingParseValueException(
                "Expected type 'Long' but was '" + typeName(dataFetcherResult) + "."
        );
    }

    @Override
    public BsonInt64 parseValue(Object input) {
        return new BsonInt64((Long) CoercingUtils.builtInCoercing.get("Long").parseValue(input));
    }

    @Override
    public BsonInt64 parseLiteral(Object AST) {
        return new BsonInt64((Long) CoercingUtils.builtInCoercing.get("Long").parseLiteral(AST));
    }
}
