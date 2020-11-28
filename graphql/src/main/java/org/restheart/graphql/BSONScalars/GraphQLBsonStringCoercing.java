package org.restheart.graphql.BSONScalars;
import graphql.schema.*;
import org.bson.BsonString;
import org.bson.BsonValue;

import static graphql.Scalars.GraphQLString;
import static org.restheart.graphql.BSONScalars.CoercingUtils.typeName;

public class GraphQLBsonStringCoercing implements Coercing<BsonString, String> {

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
      if (dataFetcherResult instanceof BsonValue){
          BsonValue value = (BsonValue) dataFetcherResult;
          if(value.isString()){
              return value.asString().getValue();
          }
          throw new CoercingSerializeException(
                  "Expected type 'String' but was'" + typeName(dataFetcherResult) + "'."
          );
      }
        throw new CoercingSerializeException(
                "Expected a 'BsonValue' but was'" + typeName(dataFetcherResult) + "'."
        );
    }

    @Override
    public BsonString parseValue(Object input) throws CoercingParseValueException {
        String possibleString = (String) GraphQLString.getCoercing().parseValue(input);
        return new BsonString(possibleString);
    }

    @Override
    public BsonString parseLiteral(Object AST) throws CoercingParseLiteralException {
        String possibleString = (String) GraphQLString.getCoercing().parseLiteral(AST);
        return new BsonString(possibleString);
    }
}
