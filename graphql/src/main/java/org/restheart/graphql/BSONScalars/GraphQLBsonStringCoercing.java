package org.restheart.graphql.BSONScalars;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonValue;

import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

public class GraphQLBsonStringCoercing implements Coercing<String, String> {

    private String convertImpl(Object input){
        if (input instanceof String){
            return (String) input;
        }
        else if(input instanceof BsonValue){
            return ((BsonValue) input).isString() ? ((BsonValue) input).asString().getValue() : null;
        }
        return null;
    }

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        String possibleString = convertImpl(dataFetcherResult);
        if(possibleString == null){
            throw new CoercingSerializeException(
                    "Expected type 'String but was '" + typeName(dataFetcherResult) +"."
            );
        }
        return possibleString;
    }

    @Override
    public String parseValue(Object input) throws CoercingParseValueException {
        String possibleString = convertImpl(input);
        if(possibleString == null){
            throw new CoercingParseValueException(
                    "Expected type 'String but was '" + typeName(input) +"."
            );
        }
        return possibleString;
    }

    @Override
    public String parseLiteral(Object input) throws CoercingParseLiteralException {
        if(!(input instanceof StringValue)){
            throw new CoercingParseLiteralException(
                    "Expected AST type 'StringValue' but was '" + typeName(input) + "'."
            );
        }
        return ((StringValue) input).getValue();
    }
}
