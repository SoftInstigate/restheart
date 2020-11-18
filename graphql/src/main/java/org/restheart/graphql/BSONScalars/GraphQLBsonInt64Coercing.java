package org.restheart.graphql.BSONScalars;

import graphql.GraphQL;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonValue;

import java.math.BigInteger;

import static graphql.Scalars.GraphQLInt;
import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

public class GraphQLBsonInt64Coercing implements Coercing<Long, Long> {

    private static final BigInteger LONG_MAX = BigInteger.valueOf(9223372036854775807L);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(-9223372036854775808L);

    private Long convertImpl(Object input){
        if(input instanceof Long){
            return (Long) input;
        }
        else if(input instanceof BsonValue){
            return ((BsonValue) input).isInt64() ? ((BsonValue) input).asInt64().getValue() : null;
        }
        return null;
    }

    @Override
    public Long serialize(Object dataFetcherResult) throws CoercingSerializeException {
        Long possibleLong = convertImpl(dataFetcherResult);
        if(possibleLong == null){
            throw new CoercingSerializeException(
                    "Expected type 'BsonInt64 but was '" + typeName(dataFetcherResult) +"."
            );
        }
        return possibleLong;
    }

    @Override
    public Long parseValue(Object input) throws CoercingParseValueException {
        Long possibleLong = convertImpl(input);
        if(possibleLong == null){
            throw new CoercingParseValueException(
                    "Expected type 'BsonInt64 but was '" + typeName(input) +"."
            );
        }
        return possibleLong;
    }

    @Override
    public Long parseLiteral(Object input) throws CoercingParseLiteralException {
        if(input instanceof StringValue){
            try {
                return Long.parseLong(((StringValue) input).getValue());
            }catch (NumberFormatException e){
                throw new CoercingParseLiteralException(
                        "Expected value to be a Long but it was '" + String.valueOf(input) + "'"
                );
            }
        }
        else if (input instanceof IntValue){
            BigInteger i = ((IntValue) input).getValue();
            if(i.compareTo(LONG_MIN) >=0 && i.compareTo(LONG_MAX) <=0){
                return i.longValue();
            }
            else {
                throw new CoercingParseLiteralException("Expected value to be in the Long range but it was '" + i.toString() + "'");
            }
        }
        else{
            throw new CoercingParseLiteralException(
                    "Expected AST type 'StringValue' or 'IntValue' but was '" + typeName(input) + "'."
            );
        }

    }
}
