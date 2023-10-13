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

import com.mongodb.client.MongoClient;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.restheart.graphql.models.FieldMapping;

public abstract class GraphQLDataFetcher implements DataFetcher<Object> {
    protected static MongoClient mongoClient;
    protected FieldMapping fieldMapping;

    public static void setMongoClient(MongoClient mClient){
        mongoClient = mClient;
    }

    public GraphQLDataFetcher(FieldMapping fieldMapping){
        this.fieldMapping = fieldMapping;
    }

    /**
     * utility method to store the root document in the context
     * if the execution path level is 2 (when the root = source)
     * @param env
     */
    protected void storeRootDoc(DataFetchingEnvironment env) {
        var ctx = env.getGraphQlContext();
        // at path level 2 the parent is the root
        if (ctx.getOrEmpty("rootDoc").isEmpty() && env.getExecutionStepInfo().getPath().getLevel() == 2) {
            ctx.put("rootDoc", env.getSource());
        }
    }
}
