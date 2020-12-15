package org.restheart.graphql.BSONCoercing;
import graphql.schema.*;
import org.bson.BsonString;
import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;

public class GraphQLBsonStringCoercing implements Coercing<BsonString, BsonString> {

    @Override
    public BsonString serialize(Object dataFetcherResult) throws CoercingSerializeException {

        if(dataFetcherResult instanceof BsonString) {
            return (BsonString) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonString' but was '" + typeName(dataFetcherResult) +"'."
        );
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
