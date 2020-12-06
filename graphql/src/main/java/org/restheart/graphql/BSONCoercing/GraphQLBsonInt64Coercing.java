package org.restheart.graphql.BSONCoercing;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonInt64;
import org.bson.BsonValue;

import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;

public class GraphQLBsonInt64Coercing implements Coercing<BsonInt64, Long> {

    private Long convertImpl(Object input) {
        if (input instanceof BsonValue){
            BsonValue value = ((BsonValue) input);
            return value.isInt64() ? value.asInt64().getValue() : null;
        }
        return null;
    }

    @Override
    public Long serialize(Object dataFetcherResult) throws CoercingSerializeException {
        Long possibleLong = convertImpl(dataFetcherResult);
        if(possibleLong == null){
            throw new CoercingParseValueException(
                    "Expected type 'Long' but was '" + typeName(dataFetcherResult) + "."
            );
        }
        return possibleLong;
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
