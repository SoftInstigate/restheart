package org.restheart.graphql.scalars.bsonCoercing;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.bson.BsonDouble;

import static org.restheart.graphql.scalars.bsonCoercing.CoercingUtils.typeName;

public class GraphQLBsonDoubleCoerching implements Coercing<BsonDouble, BsonDouble> {


    @Override
    public BsonDouble serialize(Object dataFetcherResult) throws CoercingSerializeException {

        if (dataFetcherResult instanceof BsonDouble){
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
