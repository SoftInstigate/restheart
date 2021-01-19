package org.restheart.graphql.dataloaders;

import com.mongodb.MongoClient;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Facet;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.dataloader.BatchLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class QueryBatchLoader implements BatchLoader<BsonValue, BsonValue> {

    private static MongoClient mongoClient;

    private String db;
    private String collection;

    public static void setMongoClient(MongoClient mClient){
        mongoClient = mClient;
    }

    public QueryBatchLoader(String db, String collection) {

        this.db = db;
        this.collection = collection;

    }

    /**
     *
     * IDEA-1: each pair (db, collection) has its own batchLoader, so all their queries in the same "graph layer"
     * are sent together in one request and their results are cached.
     *
     * PROBLEM: if we merge queries, by $or operator, we lose the correspondence query-result.
     *
     * IDEA-2: to solve the problem above, I used facet aggregation stage; It allows to create sub-pipelines, each one
     * with its stages, and returns a document containing a pair (key, array), where the key is the name of sub-pipeline
     * and the array contains results of the sub-pipeline.
     *
     * So, when in batch there at least 2 queries the aggregation pipeline is given by:
     *
     * - 1st Stage: {$match: {$or: query1, query2, query3, ...}}
     * - 2nd Stage: {$facet: [
     *                  "0": [{$match: query1}, ...],
     *                  "1": [{$match: query2}, ...],
     *                  ...
     *              ]}
     *
     * PERFORMANCE: still to test...
     *
     * @param queries: list of queries to merge by $or operator
     * @return: list of results, one for each query
     */

    @Override
    public CompletionStage<List<BsonValue>> load(List<BsonValue> queries) {

        return CompletableFuture.supplyAsync(() -> {

            List<Bson> stages = new ArrayList<>();

            // if there are at least 2 queries within the batch
            if (queries.size() > 1){

                //merge query by $or operator

                BsonDocument mergedQueries = new BsonDocument("$or", new BsonArray(queries));

                // 1 stage: MATCH with merged conditions
                stages.add(Aggregates.match(mergedQueries));

                // create list of sub-pipelines, one for each query
                List<Facet> listOfFacets = new ArrayList<>();

                queries.forEach(query -> {

                    listOfFacets.add(new Facet(String.valueOf(queries.indexOf(query)), Aggregates.match(query.asDocument())));

                });

                stages.add(Aggregates.facet(listOfFacets));


                // ... otherwise merging is not needed and sub-pipelines neither
            }else {
                stages.add(Aggregates.match(queries.get(0).asDocument()));
            }

            var iterable = mongoClient.getDatabase(this.db).getCollection(this.collection, BsonValue.class).aggregate(stages);

            BsonArray aggResult = new BsonArray();

            iterable.into(aggResult);

            List<BsonValue> res = new ArrayList<>();
            // CASE queries.size() > 1: result is a BsonDocument with format {"0": [<results-of-query0>], "1":[<results-of-query1>, "2":[<results-of-query2>], ...] }
            if (queries.size() > 1){

                aggResult.get(0).asDocument().forEach((key, queryResult) -> {
                        res.add(queryResult);
                    }
                );

            // CASE queries.size() = 1
            }else{
                res.add(aggResult);
            }

            return res;
        });

    }
}
