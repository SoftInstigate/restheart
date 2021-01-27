package org.restheart.graphql.datafetchers;

import graphql.schema.DataFetchingEnvironment;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.graphql.models.FieldRenaming;

import java.util.Arrays;
import java.util.regex.Pattern;

public class GQLRenamingDataFetcher extends GraphQLDataFetcher {

    public GQLRenamingDataFetcher(FieldRenaming fieldRenaming) {
        super(fieldRenaming);
    }

    @Override
    public BsonValue get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {

        String alias = ((FieldRenaming) this.fieldMapping).getAlias();

        BsonDocument parentDocument = dataFetchingEnvironment.getSource();
        return getValues(parentDocument, alias);
    }


    private BsonValue getValues(BsonValue bsonValue, String path) {

        String[] splitPath = path.split(Pattern.quote("."));
        BsonValue current = bsonValue;

        for (int i = 0; i < splitPath.length; i++) {
            if (current.isDocument() && current.asDocument().containsKey(splitPath[i])) {
                current = current.asDocument().get(splitPath[i]);
            } else if (current.isArray()) {
                try {
                    Integer index = Integer.parseInt(splitPath[i]);
                    current = current.asArray().get(index);
                } catch (NumberFormatException nfe) {
                    BsonArray array = new BsonArray();
                    for (BsonValue value : current.asArray()) {
                        String[] copy = Arrays.copyOfRange(splitPath, i, splitPath.length);
                        array.add(getValues(value, String.join(".", copy)));
                        current = array;
                    }
                    break;
                } catch (IndexOutOfBoundsException ibe) {
                    return null;
                }

            } else{
                return null;
            }
        }
        return current;

    }
}
