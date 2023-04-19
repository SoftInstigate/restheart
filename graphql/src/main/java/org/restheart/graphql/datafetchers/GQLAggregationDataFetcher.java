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

package org.restheart.graphql.datafetchers;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.mongodb.client.AggregateIterable;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.configuration.Configuration;
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
