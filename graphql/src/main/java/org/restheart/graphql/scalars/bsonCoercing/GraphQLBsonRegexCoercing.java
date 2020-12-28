package org.restheart.graphql.scalars.bsonCoercing;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonRegularExpression;

import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonRegexCoercing implements Coercing<BsonRegularExpression, BsonRegularExpression> {
    @Override
    public BsonRegularExpression serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult instanceof BsonRegularExpression){
            return (BsonRegularExpression) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonRegularExpression' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonRegularExpression parseValue(Object input) throws CoercingParseValueException {

        if(input instanceof String){
            return new BsonRegularExpression((String) input);
        }
        throw new CoercingParseValueException(
                "Expected type 'BsonRegularExpression' but was '" + typeName(input) +"'."
        );
    }

    @Override
    public BsonRegularExpression parseLiteral(Object AST) throws CoercingParseLiteralException {
        if(!(AST instanceof StringValue)){
            throw new CoercingParseLiteralException(
                    "Expected AST type 'StringValue' but was '" + typeName(AST) + "'."
            );
        }
        return new BsonRegularExpression(((StringValue) AST).getValue());
    }
}
