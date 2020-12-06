package org.restheart.graphql.BSONCoercing;

import graphql.schema.Coercing;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonBoolean;
import org.bson.BsonValue;
import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;

public class GraphQLBsonBooleanCoercing implements Coercing<BsonBoolean, Boolean> {

    private Boolean convertImpl(Object input) {
        if (input instanceof BsonValue){
            BsonValue value = ((BsonValue) input);
            return value.isBoolean() ? value.asBoolean().getValue() : null;
        }
        return null;
    }

    @Override
    public Boolean serialize(Object dataFetcherResult) throws CoercingSerializeException {
        Boolean possibleBoolean = convertImpl(dataFetcherResult);
        if(possibleBoolean == null) {
            throw new CoercingSerializeException(
                    "Expected type 'Boolean' but was '" + typeName(dataFetcherResult) + "'."
            );
        }
        return possibleBoolean;
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
