package org.restheart.graphql.models;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.datafetchers.GQLAggregationDataFetcher;
import org.restheart.graphql.datafetchers.GQLBatchAggregationDataFetcher;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.graphql.dataloaders.AggregationBatchLoader;

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
        this.db = db;
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

            if (this.dataLoaderSettings.getMax_batch_size() > 0) {
                options.setMaxBatchSize(this.dataLoaderSettings.getMax_batch_size());
            }

            options.setBatchingEnabled(this.dataLoaderSettings.getBatching());
            options.setCachingEnabled(this.dataLoaderSettings.getCaching());

            return new DataLoader<BsonValue, BsonValue>(new AggregationBatchLoader(this.db.getValue(), this.collection.getValue()), options);
        }

        return null;
    }

    public List<BsonDocument> getResolvedStagesAsList(DataFetchingEnvironment env) throws QueryVariableNotBoundException {
        var resultList = new ArrayList<BsonDocument>();

        for (var stage : this.stages) {
            if (stage.isDocument()) {
                resultList.add(searchOperators(stage.asDocument(), env).asDocument());
            }
        }

        return resultList;
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
                this.dataLoaderSettings = DataLoaderSettings.newBuilder().build();
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
