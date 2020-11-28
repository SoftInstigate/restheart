package org.restheart.graphql.BSONScalars;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonBoolean;
import org.bson.BsonValue;
import static graphql.Scalars.GraphQLBoolean;
import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

public class GraphQLBsonBooleanCoercing implements Coercing<BsonBoolean, Boolean> {

    @Override
    public Boolean serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult instanceof BsonValue){
            BsonValue value = (BsonValue) dataFetcherResult;
            if(value.isBoolean()){
                return value.asBoolean().getValue();
            }
            throw new CoercingSerializeException(
                    "Expected type 'Boolean' but was '" + typeName(dataFetcherResult) + "'."
            );
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonValue' but was '" + typeName(dataFetcherResult) + "'."
        );
    }

    @Override
    public BsonBoolean parseValue(Object input) throws CoercingParseValueException {
        Boolean possibleBoolean = (Boolean) GraphQLBoolean.getCoercing().parseValue(input);
        return new BsonBoolean(possibleBoolean);
    }

    @Override
    public BsonBoolean parseLiteral(Object AST) throws CoercingParseLiteralException {
        Boolean possibleBoolean = (Boolean) GraphQLBoolean.getCoercing().parseLiteral(AST);
        return new BsonBoolean(possibleBoolean);
    }
}
