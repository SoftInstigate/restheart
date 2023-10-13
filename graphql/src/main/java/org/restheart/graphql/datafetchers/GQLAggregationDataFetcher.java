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
import java.util.concurrent.TimeUnit;
import com.mongodb.client.AggregateIterable;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.configuration.Configuration;
import org.restheart.graphql.models.AggregationMapping;
import org.restheart.plugins.Inject;
import org.restheart.plugins.OnInit;
import graphql.schema.DataFetchingEnvironment;

public class GQLAggregationDataFetcher extends GraphQLDataFetcher {
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
    public Object get(DataFetchingEnvironment env) throws Exception {
        // store the root object in the context
        // this happens when the execution level is
        storeRootDoc(env);

        var aggregation = (AggregationMapping) this.fieldMapping;

        AggregateIterable<BsonDocument> res = null;

        var interpolatedAggregation = aggregation.interpolateArgs(env);

        // If user does not pass any stage return an empty array
        if(interpolatedAggregation.size() == 0) {
            return new BsonArray();
        }

        res = mongoClient
            .getDatabase(aggregation.getDb().getValue())
            .getCollection(aggregation.getCollection().getValue())
            .withDocumentClass(BsonDocument.class)
            .aggregate(interpolatedAggregation)
            .allowDiskUse(aggregation.getAllowDiskUse().getValue())
            .maxTime(this.aggregationTimeLimit, TimeUnit.MILLISECONDS);

        var stageOutput = new ArrayList<BsonDocument>();

        if(res != null) {
            res.into(stageOutput);
            // res.forEach(doc -> stageOutput.add(doc));
        }

        return stageOutput;
    }
}
