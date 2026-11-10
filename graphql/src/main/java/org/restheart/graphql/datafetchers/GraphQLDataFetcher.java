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
package org.restheart.graphql.datafetchers;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.graphql.models.FieldMapping;
import org.restheart.security.AggregationPipelineSecurityChecker;

import com.mongodb.client.MongoClient;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLType;

public abstract class GraphQLDataFetcher implements DataFetcher<Object> {
    protected static MongoClient mongoClient;
    protected FieldMapping fieldMapping;
    protected static AggregationPipelineSecurityChecker securityChecker;

    public static void setMongoClient(MongoClient mClient) {
        mongoClient = mClient;
    }

    public static void setSecurityChecker(AggregationPipelineSecurityChecker checker) {
        securityChecker = checker;
    }

    public GraphQLDataFetcher(FieldMapping fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    /**
     * utility method to store the root document in the context
     * if the execution path level is 2 (when the root = source)
     * @param env
     */
    protected void storeRootDoc(DataFetchingEnvironment env) {
        BsonDocument ctx = env.getLocalContext();
        // at path level 2 the parent is the root
        if (env.getExecutionStepInfo().getPath().getLevel() == 2) {
            ctx.put("rootDoc", env.getSource());
        }
    }

    /**
     * check if type is a list also when the actual type is wrapped in GraphQLNonNull
     * @param type
     * @return true if the field type is a list
     */
    protected boolean isList(GraphQLType type) {
        if (type instanceof GraphQLList) {
            return true;
        } else if (type instanceof GraphQLNonNull nnt) {
            return isList(nnt.getWrappedType());
        }

        return false;
    }

    protected long maxTimeTotal(DataFetchingEnvironment env) {
        BsonDocument ctx = env.getLocalContext();
        return ctx.containsKey("query-time-limit")
                ? ctx.get("query-time-limit").asInt64().getValue()
                : 0l;
    }

    protected long maxTimeLeft(DataFetchingEnvironment env) {
        BsonDocument ctx = env.getLocalContext();
        return ctx.containsKey("query-time-limit-left") ?
                ctx.get("query-time-limit-left").asInt64().getValue()
                : maxTimeTotal(env);
    }

    /**
     * Checks the find and sort of a query mapping, with its arguments already bound, against the
     * aggregationSecurity blacklists that guard the aggregation mappings, so that an operator such as
     * $where reaches MongoDB from neither kind of mapping. It matters most with check-operators off,
     * when a BsonDocument argument may carry operators on purpose.
     *
     * @param checker the aggregationSecurity checker; null or disabled checks nothing
     * @param intArgs the query mapping with its arguments bound, as QueryMapping.interpolateArgs returns it
     * @param db the database of the mapping
     * @throws RuntimeException if the find or the sort uses a blacklisted operator
     */
    public static void checkQuery(AggregationPipelineSecurityChecker checker, BsonDocument intArgs, String db) {
        if (checker == null || !checker.isEnabled() || intArgs == null) {
            return;
        }

        // the find and the sort as the $match and $sort stages they are equivalent to
        var stages = new BsonArray();
        var find = intArgs.get("find");
        if (find != null && find.isDocument()) {
            stages.add(new BsonDocument("$match", find));
        }
        var sort = intArgs.get("sort");
        if (sort != null && sort.isDocument()) {
            stages.add(new BsonDocument("$sort", sort));
        }

        try {
            checker.validatePipelineOrThrow(stages, db);
        } catch (SecurityException se) {
            throw new RuntimeException("GraphQL query security violation: " + se.getMessage());
        }
    }
}
