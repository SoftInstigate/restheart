package org.restheart.graphql.scalars.bsonCoercing;

import graphql.schema.Coercing;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonBoolean;
import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonBooleanCoercing implements Coercing<BsonBoolean, BsonBoolean> {

    @Override
    public BsonBoolean serialize(Object dataFetcherResult) throws CoercingSerializeException {

        if(dataFetcherResult instanceof BsonBoolean){
            return (BsonBoolean) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonBoolean' but was '" + typeName(dataFetcherResult) + "'."
        );
    }

    @Override
    public BsonBoolean parseValue(Object input) {
        return new BsonBoolean((Boolean) CoercingUtils.builtInCoercing.get("Boolean").parseValue(input));
    }

    @Override
    public BsonBoolean parseLiteral(Object AST) {
        return new BsonBoolean((Boolean) CoercingUtils.builtInCoercing.get("Boolean").parseLiteral(AST));

    }
}
