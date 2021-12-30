package org.restheart.graphql.datafetchers;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.AggregateIterable;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.models.AggregationMapping;
import org.restheart.plugins.ConfigurationScope;
import org.restheart.plugins.InjectConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetchingEnvironment;

public class GQLAggregationDataFetcher extends GraphQLDataFetcher {

    private final Logger logger = LoggerFactory.getLogger(GQLAggregationDataFetcher.class);

    private static final String AGGREGATION_TIME_LIMIT_KEY = "aggregation-time-limit";
    private long aggregationTimeLimit;

    @InjectConfiguration(scope = ConfigurationScope.ALL)
    public void initConfig(Map<String, Object> config) {

        if(config.containsKey(AGGREGATION_TIME_LIMIT_KEY)) {
            Object limit = config.get(AGGREGATION_TIME_LIMIT_KEY);
            if(limit instanceof Number) {
                this.aggregationTimeLimit = Long.parseLong(config.get(AGGREGATION_TIME_LIMIT_KEY).toString());  
            } else {
                this.aggregationTimeLimit = 0;
            }
        }
    }


    public GQLAggregationDataFetcher(AggregationMapping aggregationMapping) {
        super(aggregationMapping);
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        
        return CompletableFuture.supplyAsync(() -> {

            AggregationMapping aggregation = ((AggregationMapping) this.fieldMapping);

            try {
                aggregation.getResolvedStagesAsList(environment);
            } catch (QueryVariableNotBoundException e) {
                logger.info("Something went wrong while trying to resolve stages {}", e.getMessage());
                e.printStackTrace();
            }

            AggregateIterable<BsonDocument> res = null;
            try {
                var aggregationList = aggregation.getResolvedStagesAsList(environment);
                
                // If user does not pass any stage return an empty array
                if(aggregationList.size() == 0 ) {
                    return new BsonArray();
                }
                
                res = mongoClient
                        .getDatabase(aggregation.getDb().getValue())
                        .getCollection(aggregation.getCollection().getValue())
                        .withDocumentClass(BsonDocument.class)
                        .aggregate(
                            aggregationList
                        )
                        .allowDiskUse(aggregation.getAllowDiskUse().getValue())
                        .maxTime(this.aggregationTimeLimit, TimeUnit.MILLISECONDS);

            } catch (QueryVariableNotBoundException e) {
                logger.error("Aggregation pipeline has failed! {}", e.getMessage());
                e.printStackTrace();
            }

            var stageOutput = new BsonArray();
            
            if(res != null) {
                res.forEach(doc -> stageOutput.add(doc));
            }

            return stageOutput;
        });
    }
    
}
