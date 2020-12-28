package org.restheart.graphql;

import graphql.schema.DataFetchingEnvironment;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.graphql.models.FieldRenaming;
import java.util.regex.Pattern;

public class GQLRenamingDataFetcher extends GraphQLDataFetcher {

    public GQLRenamingDataFetcher(FieldRenaming fieldRenaming) {
        super(fieldRenaming);
    }

    @Override
    public BsonValue get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {

        String alias = ((FieldRenaming) this.fieldMapping).getAlias();
        String[] path = alias.split(Pattern.quote("."));

        BsonDocument parentDocument = dataFetchingEnvironment.getSource();
        BsonValue result = null;
        for (String step : path){
            if (parentDocument.containsKey(step)){
                result = parentDocument.get(step);
                if (result.isDocument()){
                    parentDocument = result.asDocument();
                }
            }
            else {
                result = null;
                break;
            }
        }
        return result;
    }
}
