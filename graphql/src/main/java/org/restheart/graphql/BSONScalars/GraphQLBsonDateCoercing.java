package org.restheart.graphql.BSONScalars;
import graphql.language.StringValue;
import graphql.schema.*;
import org.bson.BsonDateTime;
import org.bson.BsonValue;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import static graphql.Scalars.GraphQLLong;
import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;


public class GraphQLBsonDateCoercing implements Coercing<BsonDateTime, Long>{

    private static final String DATE_FORMAT =  "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter f = DateTimeFormat.forPattern(DATE_FORMAT);


    private Long convertImpl(Object obj){
        if (obj instanceof String){
            String value = (String) obj;
            try{
                DateTime date = f.parseDateTime(value);
                return date.getMillis();
            } catch (IllegalArgumentException e){
                return null;
            }
        }
        else if (obj instanceof DateTime){
            return ((DateTime) obj).getMillis();
        }
        else if (obj instanceof Long){
            return (Long) obj;
        }
        else if (obj instanceof BsonValue){
            BsonValue value = (BsonValue) obj;
            return value.isDateTime() ? value.asDateTime().getValue() : null;
        }
        else return null;
    }


    @Override
    public Long serialize(Object dataFetcherResult) throws CoercingSerializeException {
        Long possibleDate = convertImpl(dataFetcherResult);
        if(possibleDate == null){
            throw new CoercingSerializeException(
                    "Expected type 'Long (DateTime)' but was '" + typeName(dataFetcherResult) + "'."
            );
        }
        return possibleDate;
    }

    @Override
    public BsonDateTime parseValue(Object input) throws CoercingParseValueException {
        Long possibleDate = convertImpl(input);
        if(possibleDate == null){
            throw new CoercingParseValueException(
                    "Expected type 'Long (DateTime)' but was '" + typeName(input) + "'."
            );
        }
        return new BsonDateTime(possibleDate);
    }

    @Override
    public BsonDateTime parseLiteral(Object AST) throws CoercingParseLiteralException {
        String errorMessage = "Expected AST types 'StringValue' (Long or DateTime with format '"+ DATE_FORMAT + "') or 'IntValue' but was '" + AST + "'.";
        try{
            Long dateMillis = (Long) GraphQLLong.getCoercing().parseLiteral(AST);
            return new BsonDateTime(dateMillis);
        } catch (CoercingParseLiteralException e){
            if(AST instanceof StringValue){
                try {
                    DateTime possibleDate = f.parseDateTime(((StringValue) AST).getValue());
                    return new BsonDateTime(possibleDate.getMillis());
                } catch (IllegalArgumentException err){
                    throw new CoercingParseLiteralException(errorMessage);
                }
            }
            throw new CoercingParseLiteralException(errorMessage);
        }
    }
}