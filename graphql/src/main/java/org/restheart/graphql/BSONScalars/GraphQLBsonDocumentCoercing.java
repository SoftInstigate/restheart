package org.restheart.graphql.BSONScalars;

import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.schema.*;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

public class GraphQLBsonDocumentCoercing implements Coercing<BsonDocument, String> {

    private BsonDocument convertImpl(Object input){
        if(input instanceof BsonDocument){
            return (BsonDocument) input;
        }
        else if (input instanceof BsonValue){
            return ((BsonValue) input).isDocument() ? ((BsonValue) input).asDocument() : null;
        }
        else if (input instanceof String){
            return BsonDocument.parse((String) input);
        }
        return null;
    }


    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        BsonDocument possibleBsonDocument = convertImpl(dataFetcherResult);
        if(possibleBsonDocument == null){
            throw new CoercingSerializeException(
                    "Expected type 'BsonDocument but was '" + typeName(dataFetcherResult) +"."
            );
        }
        return possibleBsonDocument.toString();
    }

    @Override
    public BsonDocument parseValue(Object input) throws CoercingParseValueException {
        BsonDocument possibleBsonDocument = convertImpl(input);
        if(possibleBsonDocument == null){
            throw new CoercingParseValueException(
                    "Expected type 'BsonDocument but was '" + typeName(input) +"."
            );
        }
        return possibleBsonDocument;
    }

    @Override
    public BsonDocument parseLiteral(Object input) throws CoercingParseLiteralException {
        if(!(input instanceof StringValue)){
            throw new CoercingParseLiteralException(
                    "Expected AST type 'StringValue' but was '" + typeName(input) + "'."
            );
        }
        return BsonDocument.parse(((StringValue) input).getValue());

    }
}
