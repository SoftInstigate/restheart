package org.restheart.graphql.BSONScalars;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonDocument;
import org.bson.BsonValue;

import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

public class GraphQLBsonDocumentCoercing implements Coercing<BsonDocument, BsonDocument> {

    private BsonDocument convertImpl(Object obj){
        if (obj instanceof String){
            return BsonDocument.parse((String) obj);
        }
        else if(obj instanceof BsonValue){
            BsonValue value = (BsonValue) obj;
            return value.isDocument() ? value.asDocument() : null;
        }
        else return null;
    }

    @Override
    public BsonDocument serialize(Object dataFetcherResult) throws CoercingSerializeException {
        BsonDocument possibleBsonDoc = convertImpl(dataFetcherResult);
        if (possibleBsonDoc == null){
            throw new CoercingSerializeException(
                    "Expected type 'BsonDocument' but was '" + typeName(dataFetcherResult) +"'."
            );
        }
        return possibleBsonDoc;
    }

    @Override
    public BsonDocument parseValue(Object input) throws CoercingParseValueException {
        BsonDocument possibleBsonDoc = convertImpl(input);
        if (possibleBsonDoc == null){
            throw new CoercingParseValueException(
                    "Expected type 'BsonDocument' but was '" + typeName(input) +"'."
            );
        }
        return possibleBsonDoc;
    }

    @Override
    public BsonDocument parseLiteral(Object AST) throws CoercingParseLiteralException {
        if (AST instanceof StringValue){
            return BsonDocument.parse(((StringValue) AST).getValue());
        }
        throw new CoercingParseLiteralException(
                "Expected AST type 'StringValue' but was '" + typeName(AST) +"'."
        );
    }
}
