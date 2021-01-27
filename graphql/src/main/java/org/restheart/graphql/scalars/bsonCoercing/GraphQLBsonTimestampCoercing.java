package org.restheart.graphql.scalars.bsonCoercing;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonTimestamp;
import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonTimestampCoercing implements Coercing<BsonTimestamp, BsonTimestamp> {
    
    @Override
    public BsonTimestamp serialize(Object dataFetcherResult) throws CoercingSerializeException {

        if(dataFetcherResult instanceof BsonTimestamp){
            return (BsonTimestamp) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonTimestamp' but was '" + typeName(dataFetcherResult) +"'."
        );
    }

    @Override
    public BsonTimestamp parseValue(Object input) throws CoercingParseValueException {
        Long timestamp =  (Long) CoercingUtils.builtInCoercing.get("Long").parseValue(input);
        return new BsonTimestamp(timestamp);
    }

    @Override
    public BsonTimestamp parseLiteral(Object AST) throws CoercingParseLiteralException {
        Long timestamp =  (Long) CoercingUtils.builtInCoercing.get("Long").parseLiteral(AST);
        return new BsonTimestamp(timestamp);
    }
}
