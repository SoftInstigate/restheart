package org.restheart.graphql.BSONScalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonDouble;
import org.bson.BsonValue;
import static graphql.Scalars.GraphQLFloat;
import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

public class GraphQLBsonDoubleCoerching implements Coercing<BsonDouble, Double> {

    @Override
    public Double serialize(Object dataFetcherResult) throws CoercingSerializeException {

        if(dataFetcherResult instanceof BsonValue){
            BsonValue value = (BsonValue) dataFetcherResult;
            if(value.isDouble()){
                return value.asDouble().getValue();
            }
            throw new CoercingSerializeException(
                    "Expected type 'Double' but was '" + typeName(dataFetcherResult) +"'."
            );
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonValue' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonDouble parseValue(Object input) throws CoercingParseValueException {
        Double possibleDouble = (Double) GraphQLFloat.getCoercing().parseValue(input);
        return new BsonDouble(possibleDouble);
    }

    @Override
    public BsonDouble parseLiteral(Object AST) throws CoercingParseLiteralException {
        Double possibleDouble = (Double) GraphQLFloat.getCoercing().parseLiteral(AST);
        return new BsonDouble(possibleDouble);
    }
}
