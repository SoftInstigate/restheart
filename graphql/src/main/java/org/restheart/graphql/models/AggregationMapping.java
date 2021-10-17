package org.restheart.graphql.models;

import java.util.ArrayList;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.conversions.Bson;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.datafetchers.GQLAggregationDataFetcher;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;

import graphql.schema.DataFetchingEnvironment;

public class AggregationMapping extends FieldMapping {
    
    private BsonArray stages;
    private BsonString db;
    private BsonString collection;
    private BsonBoolean allowDiskUse = new BsonBoolean(false);
    

    public AggregationMapping(String fieldName, BsonString db, BsonString collection, BsonArray stages,
            BsonBoolean allowDiskUse) {

        super(fieldName);
        this.stages = stages;
        this.db = db;
        this.collection = collection;
        this.allowDiskUse = allowDiskUse;
    }

    public AggregationMapping(String fieldName, BsonString db, BsonString collection, BsonArray stages) {

        super(fieldName);
        this.stages = stages;
        this.db = db;
        this.collection = collection;
    }

    @Override
    public GraphQLDataFetcher getDataFetcher() {

        return new GQLAggregationDataFetcher(this);
    }

    public List<? extends Bson> getResolvedStagesAsList(DataFetchingEnvironment env)
            throws QueryVariableNotBoundException {

        List<BsonDocument> resultList = new ArrayList<>();

        for (BsonValue stage : this.stages) {

            if (stage.isDocument()) {

                resultList.add(searchOperators(stage.asDocument(), env).asDocument());
            }
        }

        return resultList;
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

}
