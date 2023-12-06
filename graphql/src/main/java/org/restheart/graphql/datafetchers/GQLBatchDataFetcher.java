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

import org.bson.BsonValue;
import org.dataloader.DataLoader;
import org.restheart.graphql.models.QueryMapping;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;


public class GQLBatchDataFetcher extends GraphQLDataFetcher{

    public GQLBatchDataFetcher(QueryMapping queryMapping) {
        super(queryMapping);
    }


    @Override
    public Object get(DataFetchingEnvironment env) throws Exception {
        // store the root object in the context
        // this happens when the execution level is 2
        storeRootDoc(env);

        var queryMapping = (QueryMapping) this.fieldMapping;

        DataLoader<BsonValue, BsonValue> dataLoader;

        String key = ((GraphQLObjectType) env.getParentType()).getName() + "_" + queryMapping.getFieldName();

        dataLoader = env.getDataLoader(key);

        var int_args = queryMapping.interpolateArgs(env);

        return dataLoader.load(int_args, env).thenApply(
            results -> {
                if (isList(env.getFieldDefinition().getType())) {
                    return results;
                } else {
                    return !results.asArray().isEmpty() ? results.asArray().get(0) : null;
                }
            }
        );
    }
}
