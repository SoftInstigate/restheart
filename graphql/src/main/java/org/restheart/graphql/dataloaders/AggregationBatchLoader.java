package org.restheart.graphql.dataloaders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Facet;

import org.bson.BsonArray;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.dataloader.BatchLoader;

public class AggregationBatchLoader implements BatchLoader<BsonValue, BsonValue> {

    private static MongoClient mongoClient;

    private String db;
    private String collection;

    public AggregationBatchLoader(String db, String collection) {
        this.db = db;
        this.collection = collection;
    }

    public static void setMongoClient(MongoClient mClient) {
        mongoClient = mClient;
    }

    @Override
    public CompletionStage<List<BsonValue>> load(List<BsonValue> pipelines) {
        return CompletableFuture.supplyAsync(() -> {
            var res = new ArrayList<BsonValue>();

            var listOfFacets = pipelines.stream()
                    .map(pipeline -> new Facet(String.valueOf(pipeline.hashCode()), toBson(pipeline)))
                    .toList();

            var iterable = mongoClient.getDatabase(this.db)
                    .getCollection(this.collection, BsonValue.class)
                    .aggregate(List.of(Aggregates.facet(listOfFacets)));

            var aggResult = new BsonArray();

            iterable.into(aggResult);

            var resultDoc = aggResult.get(0).asDocument();

            pipelines.forEach(query -> {
                BsonValue queryResult = resultDoc.get(String.valueOf(query.hashCode()));
                res.add(queryResult);
            });

            return res;
        });

    }

    private List<Bson> toBson(BsonValue pipeline) {
        List<Bson> result = new ArrayList<>();
        if (pipeline.isArray()) {
            pipeline.asArray().forEach(stage -> result.add(stage.asDocument()));
        }
        return result;
    }

}
