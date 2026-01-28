/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2026 SoftInstigate
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.bson.BsonArray;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.dataloader.BatchLoader;
import org.restheart.graphql.GraphQLQueryTimeoutException;
import org.restheart.security.AggregationPipelineSecurityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Facet;

public class AggregationBatchLoader implements BatchLoader<BsonValue, BsonValue> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AggregationBatchLoader.class);
    private static MongoClient mongoClient;
    private static AggregationPipelineSecurityChecker securityChecker;

    private final String db;
    private final String collection;
    private final long queryTimeLimit;
    private final boolean allowDiskUse;

    public AggregationBatchLoader(String db, String collection, boolean allowDiskUse, long queryTimeLimit) {
        this.db = db;
        this.collection = collection;
        this.allowDiskUse = allowDiskUse;
        this.queryTimeLimit = queryTimeLimit;
    }

    public static void setMongoClient(MongoClient mClient) {
        mongoClient = mClient;
    }
    
    public static void setSecurityChecker(AggregationPipelineSecurityChecker checker) {
        securityChecker = checker;
    }

    @Override
    public CompletionStage<List<BsonValue>> load(List<BsonValue> pipelines) {
        var res = new ArrayList<BsonValue>();

        // Security validation: check all batch pipelines for blacklisted stages and operators
        if (securityChecker != null && securityChecker.isEnabled()) {
            for (BsonValue pipeline : pipelines) {
                if (pipeline.isArray()) {
                    try {
                        securityChecker.validatePipelineOrThrow(pipeline.asArray(), this.db);
                    } catch (SecurityException se) {
                        LOGGER.warn("GraphQL batch aggregation pipeline blocked for security violation: {}", se.getMessage());
                        throw new RuntimeException("GraphQL batch aggregation pipeline security violation: " + se.getMessage());
                    }
                }
            }
        }

        var listOfFacets = pipelines.stream()
                .map(pipeline -> new Facet(String.valueOf(pipeline.hashCode()), toBson(pipeline)))
                .toList();

        try {
            var iterable = mongoClient.getDatabase(this.db)
                    .getCollection(this.collection, BsonValue.class)
                    .aggregate(List.of(Aggregates.facet(listOfFacets)))
                    .allowDiskUse(this.allowDiskUse)
                    .maxTime(this.queryTimeLimit, TimeUnit.MILLISECONDS);

            var aggResult = new BsonArray();

            iterable.into(aggResult);

            var resultDoc = aggResult.get(0).asDocument();

            pipelines.forEach(query -> {
                BsonValue queryResult = resultDoc.get(String.valueOf(query.hashCode()));
                res.add(queryResult);
            });

            return CompletableFuture.completedFuture(res);
        } catch(MongoExecutionTimeoutException toe) {
            throw new GraphQLQueryTimeoutException("Maximum query time limit of " + this.queryTimeLimit + "ms exceeded");
        }

    }

    private List<Bson> toBson(BsonValue pipeline) {
        List<Bson> result = new ArrayList<>();
        if (pipeline.isArray()) {
            pipeline.asArray().forEach(stage -> result.add(stage.asDocument()));
        }
        return result;
    }

}
