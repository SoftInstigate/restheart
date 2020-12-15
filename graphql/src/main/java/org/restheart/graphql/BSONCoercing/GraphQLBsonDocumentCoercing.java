package org.restheart.graphql.BSONCoercing;
import graphql.Assert;
import graphql.language.*;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.*;
import org.restheart.utils.JsonUtils;

import java.util.*;

import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;

public class GraphQLBsonDocumentCoercing implements Coercing<BsonDocument, BsonDocument> {


    @Override
    public BsonDocument serialize(Object dataFetcherResult) throws CoercingSerializeException {

        if(dataFetcherResult instanceof BsonDocument){
            return (BsonDocument) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonDocument' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonDocument parseValue(Object input) throws CoercingParseValueException {

        if(input instanceof Map){
            return JsonUtils.toBsonDocument((Map<String, Object>) input);
        }
        throw new CoercingParseValueException(
                "Expected type 'Json Object' but was '" + typeName(input) +"'."
        );
    }

    @Override
    public BsonDocument parseLiteral(Object AST) throws CoercingParseLiteralException {
        if (AST instanceof ObjectValue){
            List<ObjectField> fields = ((ObjectValue) AST).getObjectFields();
            BsonDocument parsedValues = new BsonDocument();
            fields.forEach(field ->{
                BsonValue parsedValue = parseObjectField(field.getValue(), Collections.emptyMap());
                parsedValues.put(field.getName(), parsedValue);
            });
            return parsedValues;
        }
        throw new CoercingParseLiteralException(
                "Expected AST type 'Value' but was '" + typeName(AST) + "'."
        );
    }

    public BsonValue parseObjectField(Object input, Map<String, Object> variables) throws CoercingParseLiteralException {
        if(!(input instanceof Value)) {
            throw new CoercingParseLiteralException(
                    "Expected AST type 'Value' but was '" + typeName(input) + "'."
            );
        }
        else if (input instanceof StringValue){
            return new BsonString(((StringValue) input).getValue());
        }
        else if (input instanceof IntValue){
            return new BsonInt32(((IntValue) input).getValue().intValue());
        }
        else if (input instanceof FloatValue){
            return new BsonDouble(((FloatValue) input).getValue().doubleValue());
        }
        else if (input instanceof BooleanValue){
            return new BsonBoolean(((BooleanValue) input).isValue());
        }
        else if (input instanceof NullValue){
            return BsonNull.VALUE;
        }
        else if (input instanceof EnumValue){
            return new BsonString(((EnumValue) input).getName()); // maybe?
        }
        else if (input instanceof VariableReference){
            String varName = ((VariableReference) input).getName();
            return (BsonValue) variables.get(varName);
        }
        else if (input instanceof ArrayValue){
            List<Value> values = ((ArrayValue) input).getValues();
            BsonArray bsonValues = new BsonArray();
            values.forEach(value -> {
                bsonValues.add( parseLiteral(value, variables));
            });
            return bsonValues;
        }
        else if (input instanceof ObjectValue){
            List<ObjectField> fields = ((ObjectValue) input).getObjectFields();
            BsonDocument parsedValues = new BsonDocument();

            fields.forEach(field ->{
                BsonValue parsedValue = parseObjectField(field.getValue(), variables);
                parsedValues.put(field.getName(), parsedValue);
            });
            return parsedValues;
        }
        else {
            return Assert.assertShouldNeverHappen("All types have been covered");
        }
    }
}
