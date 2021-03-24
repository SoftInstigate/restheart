package org.restheart.graphql.dataloaders;

import com.mongodb.MongoClient;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Facet;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.dataloader.BatchLoader;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.DataLoader;

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
     * - 1st Stage: {$match: {$or: [query1, query2, query3, ...]}}
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

            List<BsonValue> res = new ArrayList<>();

            List<Bson> stages = new ArrayList<>();

            // if there are at least 2 queries within the batch
            if (queries.size() > 1){

                BsonArray mergedCond = new BsonArray();
                List<Facet> listOfFacets = new ArrayList<>();

                // foreach query within the batch...
                queries.forEach(query -> {

                    // add find condition to merged array
                    BsonDocument findClause = query.asDocument().containsKey("find") ? query.asDocument().getDocument("find") : new BsonDocument();
                    mergedCond.add(findClause);

                    // create a new sub-pipeline with query stages
                    listOfFacets.add(new Facet(String.valueOf(query.hashCode()), getQueryStages(query.asDocument())));

                });

                // 1° stage --> $match with conditions merged by $or operator
                stages.add(Aggregates.match(new BsonDocument("$or", mergedCond)));

                // 2° stage --> $facet with one sub-pipeline for each query within the batch
                stages.add(Aggregates.facet(listOfFacets));

                var iterable = mongoClient.getDatabase(this.db).getCollection(this.collection, BsonValue.class).aggregate(stages);

                BsonArray aggResult = new BsonArray();

                iterable.into(aggResult);


                BsonDocument resultDoc = aggResult.get(0).asDocument();
                queries.forEach(query -> {
                    BsonValue queryResult = resultDoc.get(String.valueOf(query.hashCode()));
                    res.add(queryResult);
                });


                // ... otherwise merging is not needed and sub-pipelines neither
            }else {

                BsonDocument query = queries.get(0).asDocument();

                stages = getQueryStages(query);

                var iterable = mongoClient.getDatabase(this.db).getCollection(this.collection, BsonValue.class).aggregate(stages);

                BsonArray aggResult = new BsonArray();

                iterable.into(aggResult);

                res.add(aggResult);

            }

            return res;
        });

    }


    private List<Bson> getQueryStages(BsonDocument queryDoc){

        List<Bson> stages = new ArrayList<>();

        if(queryDoc.containsKey("find")) stages.add(Aggregates.match(queryDoc.getDocument("find")));

        if(queryDoc.containsKey("sort")) stages.add(Aggregates.sort(queryDoc.getDocument("sort")));

        if(queryDoc.containsKey("skip")) {
            Integer skip = queryDoc.getInt32("skip").getValue();
            if (skip > 0) stages.add(Aggregates.skip(skip));
        }

        if(queryDoc.containsKey("limit")) {
            Integer limit = queryDoc.getInt32("limit").getValue();
            if (limit > 0) {
                stages.add(Aggregates.limit(limit));
            }
        }





        return stages;

    }
}
