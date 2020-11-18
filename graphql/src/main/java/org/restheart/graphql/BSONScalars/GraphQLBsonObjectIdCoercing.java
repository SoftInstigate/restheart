package org.restheart.graphql.BSONScalars;

import graphql.GraphQL;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonValue;
import org.bson.types.ObjectId;

import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

public class GraphQLBsonObjectIdCoercing implements Coercing<ObjectId, ObjectId> {

    private ObjectId convertImpl(Object input){
        if(input instanceof String){
            return (ObjectId.isValid((String) input)) ? new ObjectId((String) input) : null;
        }
        else if(input instanceof ObjectId){
            return ((ObjectId) input);
        }
        else if(input instanceof BsonValue){
            return ((BsonValue) input).isObjectId() ? ((BsonValue) input).asObjectId().getValue() : null;
        }
        else {
            return null;
        }
    }


    /**
     * called to serialize the result to send it back to client
     * */
    @Override
    public ObjectId serialize(Object dataFetcherResult) throws CoercingSerializeException {
        ObjectId possibleOID = convertImpl(dataFetcherResult);
        if (possibleOID == null){
            throw new CoercingSerializeException(
                    "Expected type 'ObjectId but was '" + typeName(dataFetcherResult) +"."
            );
        }
        return possibleOID;
    }

    /**
     * called to parse client input that was passed through variables
     */

    @Override
    public ObjectId parseValue(Object input) throws CoercingParseValueException {
        ObjectId possibileOID = convertImpl(input);
        if (possibileOID == null){
            throw new CoercingParseValueException(
                    "Expected type 'ObjectId but was '" + typeName(input) +"."
            );
        }
        return possibileOID;
    }

    /**
     * called to parse client input that was passed inline in the query
     */

    @Override
    public ObjectId parseLiteral(Object input) throws CoercingParseLiteralException {
        if (!(input instanceof StringValue)){
            throw new CoercingParseLiteralException(
                    "Expected AST type 'StringValue' but was '" + typeName(input) + "'."
            );
        }
        ObjectId possibleOID = convertImpl(((StringValue) input).getValue());
        if(possibleOID == null){
            throw new CoercingParseLiteralException(
                    "Value is not a ObjectId : '" + String.valueOf(input) + "'"
            );
        }
        return possibleOID;

    }
}
