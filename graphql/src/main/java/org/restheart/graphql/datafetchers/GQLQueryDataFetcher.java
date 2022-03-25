/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2022 SoftInstigate
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

import com.mongodb.client.FindIterable;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;
import org.bson.*;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.models.QueryMapping;

import java.util.concurrent.CompletableFuture;

public class GQLQueryDataFetcher extends GraphQLDataFetcher {

    private static final String SORT_FIELD = "sort";
    private static final String FIND_FIELD = "find";
    private static final String LIMIT_FIELD = "limit";
    private static final String SKIP_FIELD = "skip";

    public GQLQueryDataFetcher(QueryMapping queryMapping) {
        super(queryMapping);
    }

    @Override
    public Object get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {

        return CompletableFuture.supplyAsync(() ->{

            QueryMapping queryMapping = (QueryMapping) this.fieldMapping;

            BsonDocument int_args = null;
            try {
                int_args = queryMapping.interpolateArgs(dataFetchingEnvironment);
            } catch (IllegalAccessException | QueryVariableNotBoundException e) {
                e.printStackTrace();
            }

            FindIterable<BsonValue> query = mongoClient.getDatabase(queryMapping.getDb())
                    .getCollection(queryMapping.getCollection(), BsonValue.class)
                    .find(int_args.containsKey(FIND_FIELD) ? int_args.get(FIND_FIELD).asDocument(): new BsonDocument());

            if (int_args.containsKey(SORT_FIELD) && int_args.get(SORT_FIELD) != null){
                query = query.sort(int_args.get(SORT_FIELD).asDocument());
            }

            if (int_args.containsKey(SKIP_FIELD) && int_args.get(SKIP_FIELD) != null){
                query = query.skip(int_args.get(SKIP_FIELD).asInt32().getValue());
            }

            if (int_args.containsKey(LIMIT_FIELD) && int_args.get(LIMIT_FIELD) != null){
                query = query.limit(int_args.get(LIMIT_FIELD).asInt32().getValue());
            }

            boolean isMultiple = dataFetchingEnvironment.getFieldDefinition().getType() instanceof GraphQLList;

            BsonValue queryResult;
            if (isMultiple) {
                BsonArray results = new BsonArray();
                query.into(results);
                queryResult = results;
            } else {
                queryResult = query.first();
            }

            return queryResult;

        });

    }
}
