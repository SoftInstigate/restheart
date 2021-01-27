package org.restheart.graphql.scalars.bsonCoercing;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonValue;
import org.bson.types.ObjectId;

import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonObjectIdCoercing implements Coercing<ObjectId, ObjectId> {


    private ObjectId convertImpl(Object obj){
        if (obj instanceof String){
            String value = (String) obj;
            return ObjectId.isValid(value) ? new ObjectId(value) : null;
        }
        else if(obj instanceof BsonValue){
            BsonValue value = ((BsonValue) obj);
            return value.isObjectId() ? value.asObjectId().getValue() : null;
        }
        else return null;
    }

    @Override
    public ObjectId serialize(Object dataFetcherResult) throws CoercingSerializeException {
        ObjectId possibleObjID = convertImpl(dataFetcherResult);
        if(possibleObjID == null){
            throw new CoercingSerializeException(
                    "Expected type 'ObjectId' but was '" + typeName(dataFetcherResult) +"'."
            );
        }
        return possibleObjID;
    }

    @Override
    public ObjectId parseValue(Object input) throws CoercingParseValueException {
        ObjectId possibleObjID = convertImpl(input);
        if (possibleObjID == null) {
            throw new CoercingParseValueException(
                    "Expected type 'ObjectId' or a valid 'String' but was '" + typeName(input) + "."
            );
        }
        return possibleObjID;
    }

    @Override
    public ObjectId parseLiteral(Object AST) throws CoercingParseLiteralException {
        if (!(AST instanceof StringValue)){
            throw new CoercingParseLiteralException(
                    "Expected AST type 'StringValue' but was '" + typeName(AST) + "'."
            );
        }
        if(!ObjectId.isValid((((StringValue) AST).getValue()))){
            throw new CoercingParseLiteralException(
                    "Input string is not a valid ObjectId"
            );
        }
        return new ObjectId(((StringValue) AST).getValue());
    }
}
