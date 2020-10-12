package org.restheart.graphql;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.conversions.Bson;

import java.util.Collection;


public class Query {


    private String db;
    private String name;
    private String collection;
    private BsonDocument filter;
    private BsonDocument sort;
    private Integer skip;
    private Integer limit;
    private boolean first;

    public Query(String db, String name, String collection, BsonDocument filter, BsonDocument sort, Integer skip, Integer limit, boolean first) {
        this.name = db;
        this.name = name;
        this.collection = collection;
        this.filter = filter;
        this.sort = sort;
        this.skip = skip;
        this.limit = limit;
        this.first = first;
    }


    public String getDb() {
        return db;
    }

    public void setDb(String db) {
        this.db = db;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public BsonDocument getFilter() {
        return filter;
    }

    public void setFilter(BsonDocument filter) {
        this.filter = filter;
    }

    public BsonDocument getSort() {
        return sort;
    }

    public void setSort(BsonDocument sort) {
        this.sort = sort;
    }

    public Integer getSkip() {
        return skip;
    }

    public void setSkip(Integer skip) {
        this.skip = skip;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public boolean isFirst() {
        return first;
    }

    public void setFirst(boolean first) {
        this.first = first;
    }
}
