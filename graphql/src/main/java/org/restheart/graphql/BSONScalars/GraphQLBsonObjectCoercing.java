package org.restheart.graphql.BSONScalars;
import graphql.Assert;
import graphql.language.*;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.*;
import org.bson.json.JsonParseException;
import org.restheart.utils.JsonUtils;

import java.util.*;

import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

public class GraphQLBsonObjectCoercing implements Coercing<BsonValue, Object> {


    @Override
    public Object serialize(Object dataFetcherResult) throws CoercingSerializeException {

        if (dataFetcherResult instanceof BsonValue){
            BsonValue value = (BsonValue) dataFetcherResult;
            try{
                Coercing coercing = CoercingUtils.getTypeCoercing(value.getBsonType());
                return coercing.serialize(value);
            } catch (IllegalStateException e){
                throw new CoercingSerializeException(
                        "Error serializing: " + e.getMessage() + "."
                );
            }
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonValue' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonValue parseValue(Object input) throws CoercingParseValueException {
        try {
            return JsonUtils.parse(input.toString());
        } catch (JsonParseException e){
            throw new CoercingParseValueException(
                    "Error parsing value: " + e.getMessage() + "."
            );
        }
    }

    @Override
    public BsonValue parseLiteral(Object AST) throws CoercingParseLiteralException {
        return parseLiteral(AST, Collections.emptyMap());
    }

    @Override
    public BsonValue parseLiteral(Object input, Map<String, Object> variables) throws CoercingParseLiteralException {
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
                bsonValues.add((BsonValue) parseLiteral(value, variables));
            });
            return bsonValues;
        }
        else if (input instanceof ObjectValue){
            List<ObjectField> fields = ((ObjectValue) input).getObjectFields();
            BsonDocument parsedValues = new BsonDocument();

            fields.forEach(field ->{
                BsonValue parsedValue = (BsonValue) parseLiteral(field, variables);
                parsedValues.put(field.getName(), parsedValue);
            });
            return parsedValues;
        }
        else {
            return Assert.assertShouldNeverHappen("All types have been covered");
        }
    }
}
