package org.restheart.graphql.datafetchers;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.restheart.graphql.models.QueryMapping;


public class GQLBatchDataFetcher extends GraphQLDataFetcher{

    public GQLBatchDataFetcher(QueryMapping queryMapping) {
        super(queryMapping);
    }


    @Override
    public Object get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {

        QueryMapping queryMapping = (QueryMapping) this.fieldMapping;

        DataLoaderRegistry dataLoaderRegistry = dataFetchingEnvironment.getDataLoaderRegistry();

        DataLoader<BsonValue, BsonValue> dataLoader;

        String key = ((GraphQLObjectType) dataFetchingEnvironment.getParentType()).getName() + "_" + queryMapping.getFieldName();

        dataLoader = dataFetchingEnvironment.getDataLoader(key);

        BsonDocument int_args = queryMapping.interpolateArgs(dataFetchingEnvironment);

        return dataLoader.load(int_args, dataFetchingEnvironment).thenApply(
                results -> {
                    boolean isMultiple = dataFetchingEnvironment.getFieldDefinition().getType() instanceof GraphQLList;
                    if (isMultiple){
                        return results;
                    }
                    else{
                        return results.asArray().size() > 0 ? results.asArray().get(0) : null;
                    }
                }
        );
    }
}
