package org.restheart.graphql.BSONScalars;

import graphql.schema.*;
import org.bson.BsonInt32;
import org.bson.BsonValue;

import java.math.BigDecimal;

import static graphql.Scalars.GraphQLInt;
import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;



public class GraphQLBsonInt32Coercing implements Coercing<BsonInt32, Integer> {


    @Override
    public Integer serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult instanceof BsonValue){
            BsonValue value = (BsonValue) dataFetcherResult;
            if(value.isInt32()){
                return value.asInt32().getValue();
            }
            throw new CoercingSerializeException(
                    "Expected type 'Integer' but was '" + typeName(dataFetcherResult) +"'."
            );
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonValue' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonInt32 parseValue(Object input) throws CoercingParseValueException {
        Integer possibleInt = (Integer) GraphQLInt.getCoercing().parseValue(input);
        return new BsonInt32(possibleInt);
    }

    @Override
    public BsonInt32 parseLiteral(Object AST) throws CoercingParseLiteralException {
        Integer possibleInt = (Integer) GraphQLInt.getCoercing().parseLiteral(AST);
        return new BsonInt32(possibleInt);
    }
}
