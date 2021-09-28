package org.restheart.graphql.datafetchers;

import java.util.concurrent.CompletableFuture;

import com.mongodb.client.AggregateIterable;
import org.bson.BsonDocument;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.models.AggregationMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetchingEnvironment;

public class GQLAggregationDataFetcher extends GraphQLDataFetcher {

    private final Logger logger = LoggerFactory.getLogger(GQLAggregationDataFetcher.class);

    public GQLAggregationDataFetcher(AggregationMapping aggregationMapping) {
        super(aggregationMapping);
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        // db = 'sample_mflix',
        // coll = 'movies',
        // stages = [  
        //    { "$match": { "name": { "$var": "title" } } },  
        // ]
        
        // la variabile "title" sara' settata come parametro sul campo che ha associato un aggregation mapping
        //  e non come query param (?avars...)
        // Es: 
        // query {
        //   MoviesByTomatoesRateRange(min: 3.8, max: 4.5, limit: 3, skip: 20, sort: -1) {
        //      tomatoesRate(title: "Titanic") <--
        //   }
        // }
        
        return CompletableFuture.supplyAsync(() -> {

            AggregationMapping aggregation = ((AggregationMapping) this.fieldMapping);

            try {
                aggregation.getResolvedStagesAsList(environment);
            } catch (QueryVariableNotBoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            AggregateIterable<BsonDocument> res = null;
            try {
                res = mongoClient
                        .getDatabase(aggregation.getDb().getValue())
                        .getCollection(aggregation.getCollection().getValue())
                        .withDocumentClass(BsonDocument.class)
                        .aggregate(
                            aggregation.getResolvedStagesAsList(environment)
                        )
                        .allowDiskUse(aggregation.getAllowDiskUse().getValue());
                        // .maxTime(MongoServiceConfiguration.get()
                        // .getAggregationTimeLimit(), TimeUnit.MILLISECONDS);
            } catch (QueryVariableNotBoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            if(res.iterator().hasNext()) {
                this.logger.info("first {}", res.first());
            }
            
            return res.iterator().hasNext() ? res.first() : null;
        });
    }
    
}
