package org.restheart.graphql.BSONScalars;

import graphql.GraphQL;
import graphql.language.IntValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonValue;

import java.math.BigInteger;
import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;



public class GraphQLBsonInt32Coercing implements Coercing<Integer, Integer> {

    private static final BigInteger INT_MAX = BigInteger.valueOf(2147483647L);
    private static final BigInteger INT_MIN = BigInteger.valueOf(-2147483648L);

    private Integer convertImpl(Object input){
        if(input instanceof Integer){
            return (Integer) input;
        }
        else if(input instanceof BsonValue){
            return ((BsonValue) input).isInt32() ? ((BsonValue) input).asInt32().getValue() : null;
        }
        return null;
    }
    @Override
    public Integer serialize(Object dataFetcherResult) throws CoercingSerializeException {
        Integer possibileInteger = convertImpl(dataFetcherResult);
        if(possibileInteger == null){
            throw new CoercingSerializeException(
                    "Expected type 'BsonInt32 but was '" + typeName(dataFetcherResult) +"."
            );
        }
        return possibileInteger;
    }

    @Override
    public Integer parseValue(Object input) throws CoercingParseValueException {
        Integer possibileInteger = convertImpl(input);
        if(possibileInteger == null){
            throw new CoercingParseValueException(
                    "Expected type 'BsonInt32 but was '" + typeName(input) +"."
            );
        }
        return possibileInteger;
    }

    @Override
    public Integer parseLiteral(Object input) throws CoercingParseLiteralException {
        if(!(input instanceof IntValue)){
            throw new CoercingParseLiteralException(
                    "Expected AST type 'IntValue' but was '" + typeName(input) + "'."
            );
        }
        BigInteger i = ((IntValue) input).getValue();
        if(i.compareTo(GraphQLBsonInt32Coercing.INT_MIN) >=0 && i.compareTo(GraphQLBsonInt32Coercing.INT_MAX) <=0){
            return i.intValue();
        }
        else {
            throw new CoercingParseLiteralException("Expected value to be in the Integer range but it was '" + i.toString() + "'");
        }

    }
}
