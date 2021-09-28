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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetchingEnvironment;

public class AggregationMapping extends FieldMapping {
    private final Logger log = LoggerFactory.getLogger(AggregationMapping.class);

    private BsonArray stages;
    private BsonString db;
    private BsonString collection;
    private BsonBoolean allowDiskUse = new BsonBoolean(false);
    // getResolvedStagesAsList method

    public AggregationMapping(String fieldName, BsonString db, BsonString collection, BsonArray stages, BsonBoolean allowDiskUse) {

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

    // in each stage object I could have different params
    /*
        {
            "$match": {
                "name" : {"$arg" : "movieTitle"}
            }
        }
        getMovieByTitle(movieTitle: 'Titanic')

        I should get from environment the movieTitle, if present,
        and replace {"$arg": "movieTitle"} with movieTitle value passed to the query
        EX:
        {
            "$match": {
                "name" : "Titanic"
            }
        }
    */
    public List<? extends Bson> getResolvedStagesAsList(DataFetchingEnvironment env) throws QueryVariableNotBoundException {
        // Ho un array di stage, per ognuno devo interpolare i dati e aggiungerlo nuovamente nell'array da restituire
        List<BsonDocument> ret = new ArrayList<>();
       
        for(BsonValue stage: this.stages) {
            if(stage.isDocument()) {

                ret.add(QueryMapping.searchOperators(stage.asDocument(), env).asDocument());
            }
        }

        return ret;
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
