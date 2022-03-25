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

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLObjectType;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.dataloader.DataLoader;
import org.restheart.graphql.models.QueryMapping;


public class GQLBatchDataFetcher extends GraphQLDataFetcher{

    public GQLBatchDataFetcher(QueryMapping queryMapping) {
        super(queryMapping);
    }


    @Override
    public Object get(DataFetchingEnvironment dataFetchingEnvironment) throws Exception {

        QueryMapping queryMapping = (QueryMapping) this.fieldMapping;

        DataLoader<BsonValue, BsonValue> dataLoader;

        String key = ((GraphQLObjectType) dataFetchingEnvironment.getParentType()).getName() + "_" + queryMapping.getFieldName();

        dataLoader = dataFetchingEnvironment.getDataLoader(key);

        BsonDocument int_args = queryMapping.interpolateArgs(dataFetchingEnvironment);

        return dataLoader.load(int_args, dataFetchingEnvironment).thenApply(
                results -> {
                    boolean isMultiple = dataFetchingEnvironment.getFieldDefinition().getType() instanceof GraphQLList;
                    if (isMultiple){
                        return results;
                    }
                    else{
                        return results.asArray().size() > 0 ? results.asArray().get(0) : null;
                    }
                }
        );
    }
}
