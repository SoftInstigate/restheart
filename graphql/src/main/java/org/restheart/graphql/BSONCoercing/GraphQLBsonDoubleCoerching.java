package org.restheart.graphql.BSONCoercing;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonDouble;
import org.bson.BsonInt64;
import static org.restheart.graphql.BSONCoercing.CoercingUtils.typeName;

public class GraphQLBsonDoubleCoerching implements Coercing<BsonDouble, BsonDouble> {


    @Override
    public BsonDouble serialize(Object dataFetcherResult) throws CoercingSerializeException {

        if (dataFetcherResult instanceof BsonInt64){
            return (BsonDouble) dataFetcherResult;
        }
        throw new CoercingSerializeException(
                "Expected type 'BsonDouble' but was '" + typeName(dataFetcherResult) + "'."
        );
    }

    @Override
    public BsonDouble parseValue(Object input) throws CoercingParseValueException {
        return new BsonDouble((Double) CoercingUtils.builtInCoercing.get("Float").parseValue(input));
    }

    @Override
    public BsonDouble parseLiteral(Object AST) throws CoercingParseLiteralException {
        return new BsonDouble((Double) CoercingUtils.builtInCoercing.get("Float").parseLiteral(AST));
    }
}
