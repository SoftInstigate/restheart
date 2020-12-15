package org.restheart.graphql.BSONCoercing;

import graphql.language.ArrayValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonArray;
import org.bson.BsonValue;
import org.restheart.graphql.BsonScalars;

import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;

public class GraphQLBsonArrayCoercing implements Coercing<BsonArray, BsonArray> {

    @Override
    public BsonArray serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if(dataFetcherResult instanceof BsonArray){

            return (BsonArray) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonArray' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonArray parseValue(Object input) throws CoercingParseValueException {
        BsonArray parsedValues = new BsonArray();
        if(input.getClass().isArray()){
            for (Object element : ((Object[]) input)){
                parsedValues.add((BsonValue) BsonScalars.GraphQLBsonDocument.getCoercing().parseValue(element));
            }
            return parsedValues;
        }
        throw new CoercingParseValueException(
                "Expected type 'BsonArray' but was '" + typeName(input) +"'."
        );
    }

    @Override
    public BsonArray parseLiteral(Object AST) throws CoercingParseLiteralException {
        if(!(AST instanceof ArrayValue)){
            throw new CoercingParseValueException(
                    "Expected AST type 'ArrayValue' but was '" + typeName(AST) + "'."
            );
        }
        BsonArray parsedValues = new BsonArray();
        ((ArrayValue) AST).getValues().forEach(value -> {
            parsedValues.add((BsonValue) BsonScalars.GraphQLBsonDocument.getCoercing().parseLiteral(value));
        });
        return parsedValues;
    }
}
