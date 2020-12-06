package org.restheart.graphql.BSONCoercing;
import graphql.schema.*;
import org.bson.BsonString;
import org.bson.BsonValue;
import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;

public class GraphQLBsonStringCoercing implements Coercing<BsonString, String> {

    private String convertImpl(Object input) {
        if (input instanceof BsonValue){
            BsonValue value = ((BsonValue) input);
            return value.isString() ? value.asString().getValue() : null;
        }
        return null;
    }

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {

        String possibleString = convertImpl(dataFetcherResult);
        if (possibleString == null){
            throw new CoercingSerializeException(
                    "Expected a 'String' but was'" + typeName(dataFetcherResult) + "'."
            );
        }
        return possibleString;
    }

    @Override
    public BsonString parseValue(Object input) throws CoercingParseValueException {
        return new BsonString((String) CoercingUtils.builtInCoercing.get("String").parseValue(input));
    }

    @Override
    public BsonString parseLiteral(Object AST) throws CoercingParseLiteralException {
        return new BsonString((String) CoercingUtils.builtInCoercing.get("String").parseLiteral(AST));
    }
}
