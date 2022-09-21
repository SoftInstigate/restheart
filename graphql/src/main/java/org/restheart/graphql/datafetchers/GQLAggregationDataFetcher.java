package org.restheart.graphql.datafetchers;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.AggregateIterable;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.Configuration;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.models.AggregationMapping;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetchingEnvironment;

public class GQLAggregationDataFetcher extends GraphQLDataFetcher {

    private final Logger logger = LoggerFactory.getLogger(GQLAggregationDataFetcher.class);

    private static final String AGGREGATION_TIME_LIMIT_KEY = "aggregation-time-limit";
    private long aggregationTimeLimit;

    @Inject("rh-config")
    private Configuration config;

    @OnInit
    public void init() {
        var _config = config.toMap();

        if(_config.containsKey(AGGREGATION_TIME_LIMIT_KEY)) {
            var limit = _config.get(AGGREGATION_TIME_LIMIT_KEY);
            if(limit instanceof Number) {
                this.aggregationTimeLimit = Long.parseLong(_config.get(AGGREGATION_TIME_LIMIT_KEY).toString());  
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
            var aggregation = (AggregationMapping) this.fieldMapping;

            try {
                aggregation.getResolvedStagesAsList(environment);
            } catch (QueryVariableNotBoundException e) {
                logger.info("Something went wrong while trying to resolve stages {}", e.getMessage());
                throw new RuntimeException(e);
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
                    .aggregate(aggregationList)
                    .allowDiskUse(aggregation.getAllowDiskUse().getValue())
                    .maxTime(this.aggregationTimeLimit, TimeUnit.MILLISECONDS);

            } catch (QueryVariableNotBoundException e) {
                logger.error("Aggregation pipeline has failed! {}", e.getMessage());
                e.printStackTrace();
            }

            var stageOutput = new ArrayList<BsonDocument>();

            if(res != null) {
                res.forEach(doc -> stageOutput.add(doc));
            }

            return stageOutput;
        });
    }
}
