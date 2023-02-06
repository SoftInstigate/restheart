/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2023 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.graphql.dataloaders;

import com.mongodb.client.MongoClient;
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
     * - 1st Stage: {$match: {$or: [query1, query2, query3, ...]}}
     * - 2nd Stage: {$facet: [
     *                  "0": [{$match: query1}, ...],
     *                  "1": [{$match: query2}, ...],
     *                  ...
     *              ]}
     *
     * @param queries: list of queries to merge by $or operator
     * @return: list of results, one for each query
     */

    @Override
    public CompletionStage<List<BsonValue>> load(List<BsonValue> queries) {
        return CompletableFuture.supplyAsync(() -> {
            var res = new ArrayList<BsonValue>();
            List<Bson> stages = new ArrayList<>();

            // if there are at least 2 queries within the batch
            if (queries.size() > 1){
                var mergedCond = new BsonArray();
                var listOfFacets = new ArrayList<Facet>();

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

                var resultDoc = aggResult.get(0).asDocument();
                queries.forEach(query -> {
                    BsonValue queryResult = resultDoc.get(String.valueOf(query.hashCode()));
                    res.add(queryResult);
                });
                // ... otherwise merging is not needed and sub-pipelines neither
            } else {
                var query = queries.get(0).asDocument();
                stages = getQueryStages(query);
                var iterable = mongoClient.getDatabase(this.db).getCollection(this.collection, BsonValue.class).aggregate(stages);
                var aggResult = new BsonArray();

                iterable.into(aggResult);

                res.add(aggResult);
            }

            return res;
        });

    }

    private List<Bson> getQueryStages(BsonDocument queryDoc){
        var stages = new ArrayList<Bson>();

        if (queryDoc.containsKey("find")) {
            stages.add(Aggregates.match(queryDoc.getDocument("find")));
        }

        if (queryDoc.containsKey("sort")) {
            stages.add(Aggregates.sort(queryDoc.getDocument("sort")));
        }

        if (queryDoc.containsKey("skip")) {
            var skip = queryDoc.getInt32("skip").getValue();
            if (skip > 0) stages.add(Aggregates.skip(skip));
        }

        if (queryDoc.containsKey("limit")) {
            var limit = queryDoc.getInt32("limit").getValue();
            if (limit > 0) {
                stages.add(Aggregates.limit(limit));
            }
        }

        return stages;
    }
}
