package org.restheart.graphql.BSONScalars;

import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.*;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

/**
public class GraphQLDateCoercing implements Coercing<LocalDateTime, LocalDateTime> {

    private LocalDateTime convertImpl(Object input){
        if(input instanceof BigInteger){

        }
        else if( input instanceof LocalDateTime){
            return (LocalDateTime) input;
        }
        else {
            return null;
        }
    }

    @Override
    public LocalDateTime serialize(Object dataFetcherResult) throws CoercingSerializeException {
        LocalDateTime possibleDate = convertImpl(dataFetcherResult);
        if(possibleDate == null){
            throw new CoercingSerializeException(
                    "Expected type 'LocalDateTime but was '" + typeName(dataFetcherResult) +"."
            );
        }
        return possibleDate;
    }

    @Override
    public LocalDateTime parseValue(Object input) throws CoercingParseValueException {
        return null;
    }


    @Override
    public LocalDateTime parseLiteral(Object input) throws CoercingParseLiteralException {

        if (input instanceof IntValue){

        }
        else if(input instanceof StringValue){

        }
         possibleDate = convertImpl(((IntValue) input).getValue());
        if(possibleDate == null){
            throw new CoercingParseLiteralException(
                    "Value is not a LocalDateTime : '" + String.valueOf(input) + "'"
            );
        }
        return possibleDate;
    }
}
**/