package org.restheart.graphql.BSONCoercing;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonDouble;
import org.bson.BsonValue;
import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;

public class GraphQLBsonDoubleCoerching implements Coercing<BsonDouble, Double> {


    private Double convertImpl(Object input){
        if(input instanceof BsonValue){
            BsonValue value = (BsonValue) input;
            return value.isDouble() ? value.asDouble().getValue() : null;
        }
        return null;
    }

    @Override
    public Double serialize(Object dataFetcherResult) throws CoercingSerializeException {

        Double possibleDouble = convertImpl(dataFetcherResult);
        if(possibleDouble == null) {
            throw new CoercingSerializeException(
                    "Expected type 'Double' but was '" + typeName(dataFetcherResult) + "'."
            );
        }
        return possibleDouble;
    }

    @Override
    public BsonDouble parseValue(Object input) throws CoercingParseValueException {
        return new BsonDouble((Double) CoercingUtils.builtInCoercing.get("Float").parseValue(input));
    }

    @Override
    public BsonDouble parseLiteral(Object AST) throws CoercingParseLiteralException {
        return new BsonDouble((Double) CoercingUtils.builtInCoercing.get("Float").parseLiteral(AST));
    }
}
