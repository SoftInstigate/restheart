package org.restheart.graphql.BSONCoercing;

import graphql.schema.*;
import org.bson.BsonInt32;
import org.bson.BsonValue;

import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;


public class GraphQLBsonInt32Coercing implements Coercing<BsonInt32, Integer> {

    private Integer convertImpl(Object input) {
        if (input instanceof BsonValue){
            BsonValue value = ((BsonValue) input);
            return value.isInt32() ? value.asInt32().getValue() : null;
        }
        return null;
    }

    @Override
    public Integer serialize(Object dataFetcherResult) throws CoercingSerializeException {
        Integer possibleInt = convertImpl(dataFetcherResult);
        if(possibleInt == null){
            throw new CoercingSerializeException(
                    "Expected type 'Integer' but was '" + typeName(dataFetcherResult) +"'."
            );
        }
        else return possibleInt;
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
