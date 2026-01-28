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

package org.restheart.graphql.models;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.GraphQLIllegalAppDefinitionException;
import org.restheart.graphql.datafetchers.GQLAggregationDataFetcher;
import org.restheart.graphql.datafetchers.GQLBatchAggregationDataFetcher;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.graphql.dataloaders.AggregationBatchLoader;
import org.restheart.mongodb.utils.StagesInterpolator;
import org.restheart.mongodb.utils.StagesInterpolator.STAGE_OPERATOR;
import org.restheart.mongodb.utils.VarsInterpolator.VAR_OPERATOR;
import org.restheart.utils.BsonUtils;

import graphql.schema.DataFetchingEnvironment;

public class AggregationMapping extends FieldMapping implements Batchable {
    private BsonArray stages;
    private BsonString db;
    private BsonString collection;
    private BsonBoolean allowDiskUse = new BsonBoolean(false);
    private DataLoaderSettings dataLoaderSettings;

    public AggregationMapping(String fieldName, BsonString db, BsonString collection, BsonArray stages, BsonBoolean allowDiskUse, DataLoaderSettings settings) {
        super(fieldName);
        this.stages = stages;
        this.db = db; // DB!!!
        this.collection = collection;
        this.allowDiskUse = allowDiskUse;
        this.dataLoaderSettings = settings;
    }

    @Override
    public GraphQLDataFetcher getDataFetcher() {
        return this.dataLoaderSettings.getBatching()
            ? new GQLBatchAggregationDataFetcher(this)
            : new GQLAggregationDataFetcher(this);
    }

    @Override
    public DataLoader<BsonValue, BsonValue> getDataloader() {
        if (this.dataLoaderSettings.getCaching() || this.dataLoaderSettings.getBatching()) {
            var options = new DataLoaderOptions().setCacheKeyFunction(bsonVal -> String.valueOf(bsonVal.hashCode()));

            if (this.dataLoaderSettings.getMaxBatchSize() > 0) {
                options.setMaxBatchSize(this.dataLoaderSettings.getMaxBatchSize());
            }

            options.setBatchingEnabled(this.dataLoaderSettings.getBatching());
            options.setCachingEnabled(this.dataLoaderSettings.getCaching());

            return DataLoaderFactory.newDataLoader(new AggregationBatchLoader(this.db.getValue(), this.collection.getValue(), this.allowDiskUse.getValue(), this.dataLoaderSettings.getQueryTimeLimit()), options);
        }

        return null;
    }

    public List<BsonDocument> interpolateArgs(DataFetchingEnvironment env) throws QueryVariableNotBoundException, GraphQLIllegalAppDefinitionException {
        var values = BsonUtils.toBsonDocument(env.getArguments());
        // add the rootDoc arg see https://restheart.org/docs/mongodb-graphql/#the-rootdoc-argument
        BsonDocument locaLContext = env.getLocalContext();
        BsonValue rootDoc = locaLContext.get("rootDoc");
        if (rootDoc != null) { // rootDoc is only available at path level >= 2
            values.put("rootDoc", rootDoc);
        }

        // add the @user args
        var user = locaLContext.getDocument("@user");
        values.put("@user", user.isEmpty() ? BsonNull.VALUE : user);
        user.entrySet().stream().forEach(e -> values.put("@user.".concat(e.getKey()), e.getValue()));

        try {
            var argInterpolated = StagesInterpolator.interpolate(VAR_OPERATOR.$arg, STAGE_OPERATOR.$ifarg, stages, values);
            var argAndFkInterpolated = new ArrayList<BsonDocument>();

            for (var s: argInterpolated) {
                var is = interpolateFkOperator(s, env);

                if (is.isDocument()) {
                    argAndFkInterpolated.add(is.asDocument());
                } else {
                    throw new GraphQLIllegalAppDefinitionException("invalid stage: " + BsonUtils.toJson(s));
                }
            }

            return argAndFkInterpolated;
        } catch(InvalidMetadataException ime) {
            throw new GraphQLIllegalAppDefinitionException("invalid app definition", ime);
        }
    }

    public DataLoaderSettings getDataLoaderSettings() {
        return dataLoaderSettings;
    }

    public void setDataLoaderSettings(DataLoaderSettings dataLoaderSettings) {
        this.dataLoaderSettings = dataLoaderSettings;
    }

    public BsonArray getStages() {
        return this.stages;
    }

    public void setStages(BsonArray stages) {
        this.stages = stages;
    }

    public BsonString getDb() {
        return db;
    }

    public void setDb(BsonString db) {
        this.db = db;
    }

    public BsonString getCollection() {
        return collection;
    }

    public void setCollection(BsonString collection) {
        this.collection = collection;
    }

    public BsonBoolean getAllowDiskUse() {
        return allowDiskUse;
    }

    public void setAllowDiskUse(BsonBoolean allowDiskUse) {
        this.allowDiskUse = allowDiskUse;
    }

    public static class Builder {
        private String fieldName;
        private BsonArray stages;
        private BsonString db;
        private BsonString collection;
        private BsonBoolean allowDiskUse = new BsonBoolean(false);
        private DataLoaderSettings dataLoaderSettings;

        public Builder() {
        }

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder stages(BsonArray stages) {
            this.stages = stages;
            return this;
        }

        public Builder db(BsonString db) {
            this.db = db;
            return this;
        }

        public Builder collection(BsonString collection) {
            this.collection = collection;
            return this;
        }

        public Builder allowDiskUse(BsonBoolean allowDiskUse) {
            this.allowDiskUse = allowDiskUse;
            return this;
        }

        public Builder dataLoaderSettings(DataLoaderSettings dataLoaderSettings) {
            this.dataLoaderSettings = dataLoaderSettings;
            return this;
        }

        public AggregationMapping build() {
            if (this.dataLoaderSettings == null) {
                this.dataLoaderSettings = DataLoaderSettings.builder().build();
            }
            return new AggregationMapping(
                    this.fieldName,
                    this.db,
                    this.collection,
                    this.stages,
                    this.allowDiskUse,
                    this.dataLoaderSettings);
        }

    }

}
