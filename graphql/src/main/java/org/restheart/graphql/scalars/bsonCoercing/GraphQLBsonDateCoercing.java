package org.restheart.graphql.scalars.bsonCoercing;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonDateTime;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonDateCoercing implements Coercing<BsonDateTime, BsonDateTime> {


    private Long convertImpl(Object input){
        if(input instanceof Long){
            return (Long) input;
        }
        else if(input instanceof String){
            try {
                OffsetDateTime ofsDate = OffsetDateTime.parse(input.toString(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                return ofsDate.toInstant().toEpochMilli();
            }catch (DateTimeParseException e1){
                try{
                    return Long.parseLong(input.toString());
                }catch (NumberFormatException e2){
                    return null;
                }
            }
        }
        else if (input instanceof OffsetDateTime){
            return ((OffsetDateTime) input).toInstant().toEpochMilli();
        }
        return null;
    }

    @Override
    public BsonDateTime serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult instanceof BsonDateTime){
            return (BsonDateTime) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonDateTime' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonDateTime parseValue(Object input) throws CoercingParseValueException {
        Long possibleDate = convertImpl(input);
        if (possibleDate == null){
            throw new CoercingParseValueException(
                    "Expected type 'Long' or 'String' (with a valid OffsetDateTime) but was '" + typeName(input) +"'."
            );
        }
        return new BsonDateTime(possibleDate);
    }

    @Override
    public BsonDateTime parseLiteral(Object AST) throws CoercingParseLiteralException {
        if (!(AST instanceof StringValue)) {
            throw new CoercingParseLiteralException(
                    "Expected AST type 'StringValue' but was '" + typeName(AST) + "'."
            );
        }
        String possibleDate = ((StringValue) AST).getValue();
        try {
            OffsetDateTime ofsDate = OffsetDateTime.parse(possibleDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return new BsonDateTime(ofsDate.toInstant().toEpochMilli());
        } catch (DateTimeParseException e1){
            try {
                return new BsonDateTime(Long.parseLong(possibleDate));
            } catch (NumberFormatException e2){
                throw new CoercingParseLiteralException(
                        "Input string is not a valid date."
                );
            }
        }
    }
}
