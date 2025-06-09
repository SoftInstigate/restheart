/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2025 SoftInstigate
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

import java.util.concurrent.TimeUnit;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.graphql.GraphQLQueryTimeoutException;
import org.restheart.graphql.models.QueryMapping;
import org.restheart.utils.BsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoExecutionTimeoutException;

import graphql.schema.DataFetchingEnvironment;

public class GQLQueryDataFetcher extends GraphQLDataFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(GQLQueryDataFetcher.class);

    private static final String SORT_FIELD = "sort";
    private static final String FIND_FIELD = "find";
    private static final String LIMIT_FIELD = "limit";
    private static final String SKIP_FIELD = "skip";

    public GQLQueryDataFetcher(QueryMapping queryMapping) {
        super(queryMapping);
    }

    @Override
    public Object get(DataFetchingEnvironment env) throws Exception {
        // store the root object in the context
        // this happens when the execution level is 2
        storeRootDoc(env);

        var queryMapping = (QueryMapping) this.fieldMapping;

        BsonDocument int_args;

        int_args = queryMapping.interpolateArgs(env);

        var _find = int_args.containsKey(FIND_FIELD) ? int_args.get(FIND_FIELD).asDocument(): new BsonDocument();
        var _sort = int_args.containsKey(SORT_FIELD) && int_args.get(SORT_FIELD) != null ? int_args.get(SORT_FIELD).asDocument() : null;
        var _skip = int_args.containsKey(SKIP_FIELD) && int_args.get(SKIP_FIELD) != null ? int_args.get(SKIP_FIELD).asInt32().getValue() : null;
        var _limit = int_args.containsKey(LIMIT_FIELD) && int_args.get(LIMIT_FIELD) != null ? int_args.get(LIMIT_FIELD).asInt32().getValue() : null;

        LOGGER.debug("Executing query for field {}: {}.{}.find {}, sort {}, skip {}, limit {}, context vars {}", env.getField().getName(), queryMapping.getDb(), queryMapping.getCollection(),  _find, _sort, _skip, _limit, BsonUtils.toJson(env.getLocalContext()));

        try {
            var query = mongoClient
                .getDatabase(queryMapping.getDb()).getCollection(queryMapping.getCollection(), BsonValue.class)
                .find(_find)
                .maxTime(maxTimeLeft(env), TimeUnit.MILLISECONDS);

            if (_sort != null) {
                query = query.sort(_sort);
            }

            if (_skip != null) {
                query = query.skip(_skip);
            }

            if (_limit != null) {
                query = query.limit(_limit);
            }

            if (isList(env.getFieldDefinition().getType())) {
                var queryResult = new BsonArray();
                query.into(queryResult.asArray());
                return queryResult;
            } else {
                return query.first();
            }
        } catch(MongoExecutionTimeoutException toe) {
            throw new GraphQLQueryTimeoutException("Maximum query time limit of " + maxTimeTotal(env) + "ms exceeded");
        }
    }
}
