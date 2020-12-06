package org.restheart.graphql.BSONCoercing;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonTimestamp;
import org.bson.BsonValue;
import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;

public class GraphQLBsonTimestampCoercing implements Coercing<BsonTimestamp, Long> {

    private Long convertImpl(Object obj){
        if (obj instanceof Long){
            return (Long) obj;
        }
        else if(obj instanceof String){
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e){
                return null;
            }
        }
        else if(obj instanceof BsonValue){
            BsonValue value = (BsonValue) obj;
            return value.isTimestamp() ? value.asTimestamp().getValue() : null;
        }
        return null;
    }

    @Override
    public Long serialize(Object dataFetcherResult) throws CoercingSerializeException {
        Long possibleTimeStamp = convertImpl(dataFetcherResult);
        if(possibleTimeStamp == null){
            throw new CoercingSerializeException(
                    "Expected type 'Long (Timestamp)' but was '" + typeName(dataFetcherResult) +"'."
            );
        }
        return possibleTimeStamp;
    }

    @Override
    public BsonTimestamp parseValue(Object input) throws CoercingParseValueException {
        Long possibleTimeStamp = convertImpl(input);
        if(possibleTimeStamp == null){
            throw new CoercingParseValueException(
                    "Expected type 'Long (Timestamp)' but was '" + typeName(input) +"'."
            );
        }
        return new BsonTimestamp(possibleTimeStamp);
    }

    @Override
    public BsonTimestamp parseLiteral(Object AST) throws CoercingParseLiteralException {
        Long timestamp =  (Long) CoercingUtils.builtInCoercing.get("Long").parseLiteral(AST);
        return new BsonTimestamp(timestamp);
    }
}
