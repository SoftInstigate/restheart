package org.restheart.graphql.BSONScalars;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonInt64;
import org.bson.BsonValue;

import java.math.BigDecimal;

import static graphql.Scalars.GraphQLLong;
import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

public class GraphQLBsonInt64Coercing implements Coercing<BsonInt64, Long> {


    @Override
    public Long serialize(Object dataFetcherResult) throws CoercingSerializeException {

        if(dataFetcherResult instanceof BsonValue){
            BsonValue value = (BsonValue) dataFetcherResult;
            if(value.isInt64()){
                return value.asInt64().getValue();
            }
            throw new CoercingSerializeException(
                    "Expected type 'Long' but was '" + typeName(dataFetcherResult) +"'."
            );
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonValue' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonInt64 parseValue(Object input) throws CoercingParseValueException {
        Long possibleLong = (Long) GraphQLLong.getCoercing().parseValue(input);
        return new BsonInt64(possibleLong);
    }

    @Override
    public BsonInt64 parseLiteral(Object AST) throws CoercingParseLiteralException {
        Long possibleLong = (Long) GraphQLLong.getCoercing().parseLiteral(AST);
        return new BsonInt64(possibleLong);    }
}
