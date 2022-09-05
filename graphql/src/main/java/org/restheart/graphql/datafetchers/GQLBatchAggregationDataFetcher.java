package org.restheart.graphql.datafetchers;

import org.bson.BsonArray;
import org.restheart.graphql.models.AggregationMapping;
import graphql.schema.GraphQLObjectType;

import graphql.schema.DataFetchingEnvironment;

public class GQLBatchAggregationDataFetcher extends GraphQLDataFetcher {

    public GQLBatchAggregationDataFetcher(AggregationMapping aggregationMapping) {
        super(aggregationMapping);
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        var aggregationMapping = (AggregationMapping) this.fieldMapping;

        var key = ((GraphQLObjectType) environment.getParentType()).getName() + "_" + aggregationMapping.getFieldName();

        var dataLoader = environment.getDataLoader(key);

        var aggregationList = aggregationMapping.getResolvedStagesAsList(environment);

        var bsonArray = new BsonArray();
        bsonArray.addAll(aggregationList);

        return dataLoader.load(bsonArray, environment);

    }
}
