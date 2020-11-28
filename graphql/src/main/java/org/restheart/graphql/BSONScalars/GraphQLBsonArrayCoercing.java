package org.restheart.graphql.BSONScalars;
import graphql.language.*;
import graphql.schema.*;
import org.bson.BsonArray;
import org.bson.BsonValue;
import java.util.*;
import java.util.stream.Collectors;

import static org.restheart.graphql.BSONScalar.GraphQLBsonObject;
import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;


public class GraphQLBsonArrayCoercing implements Coercing<BsonArray, List<Object>> {

    @Override
    public List<Object> serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof BsonValue){
            List<BsonValue> possibleValues = ((BsonValue) dataFetcherResult).isArray() ? ((BsonValue) dataFetcherResult).asArray().getValues() : null;
            if (possibleValues == null){
                throw new CoercingSerializeException(
                        "Expected type 'List<BsonValue>' but was '" + typeName(dataFetcherResult) +"'."
                );
            }
            List<Object> objs = possibleValues.stream()
                    .map(value -> GraphQLBsonObject.getCoercing().serialize(value))
                    .collect(Collectors.toList());

            return objs;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonValue' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonArray parseValue(Object input) throws CoercingParseValueException {
        if(input.getClass().isArray()){
            Object[] array = (Object[]) input;
            List<BsonValue> bsonValues = Arrays.stream(array)
                    .map(value -> (BsonValue) GraphQLBsonObject.getCoercing().parseValue(value))
                    .collect(Collectors.toList());
            return new BsonArray(bsonValues);
        }
        throw new CoercingParseValueException(
                "Expected type 'BsonValue' but was '" + typeName(input) +"'."
        );
    }

    @Override
    public BsonArray parseLiteral(Object AST) throws CoercingParseLiteralException {
        if (AST instanceof ArrayValue){
            BsonArray parsedList = (BsonArray) GraphQLBsonObject.getCoercing().parseLiteral(AST);
            return parsedList;
        }
        throw new CoercingParseLiteralException(
                "Expected AST type 'ArrayValue' but was '" + typeName(AST) + "'."
        );
    }
}
