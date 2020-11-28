package org.restheart.graphql.BSONScalars;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonDecimal128;
import org.bson.BsonValue;
import org.bson.types.Decimal128;

import java.math.BigDecimal;

import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

public class GraphQLBsonDecimal128Coercing implements Coercing<BsonDecimal128, Decimal128> {


    private Decimal128 convertImpl(Object obj){
        if (CoercingUtils.IsANumber(obj)){
            Decimal128 value = Decimal128.parse(obj.toString());
            return value.isNaN() ? value : null;
        }
        else if(obj instanceof BsonValue){
            BsonValue value = (BsonValue) obj;
            return value.isDecimal128() ? value.asDecimal128().getValue() : null;
        }
        else return null;
    }

    @Override
    public Decimal128 serialize(Object dataFetcherResult) throws CoercingSerializeException {
        Decimal128 possibleDecimal = convertImpl(dataFetcherResult);
        if(possibleDecimal == null){
            throw new CoercingSerializeException(
                    "Expected type 'Decimal128' but was '" + typeName(dataFetcherResult) +"'."
            );
        }
        return possibleDecimal;
    }

    @Override
    public BsonDecimal128 parseValue(Object input) throws CoercingParseValueException {
        Decimal128 possibleDecimal = convertImpl(input);
        if(possibleDecimal == null){
            throw new CoercingParseValueException(
                    "Expected type 'Decimal128' but was '" + typeName(input) +"'."
            );
        }
        return new BsonDecimal128(possibleDecimal);
    }

    @Override
    public BsonDecimal128 parseLiteral(Object input) throws CoercingParseLiteralException {
        if(!(input instanceof StringValue)){
            Decimal128 value = Decimal128.parse(((StringValue) input).getValue());
            if(value.isNaN()){
                throw new CoercingParseLiteralException(
                        "Expected value to be a number but it was '" + value.toString() + "'"
                );
            }
            return new BsonDecimal128(value);
        }
        throw  new CoercingParseLiteralException(
                "Expected AST type 'StringValue' but was '" + typeName(input) + "'."
        );
    }
}
