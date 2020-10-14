package org.restheart.graphql;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;

import javax.print.Doc;
import java.util.Collection;


public class Query {


    private String db;
    private String name;
    private String collection;
    private Document filter;
    private Document sort;
    private Document skip;
    private Document limit;
    private Document first;

    public Query(String db, String name, String collection, Document filter, Document sort, Document skip, Document limit, Document first) {
        this.db = db;
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

    public Document getFilter() {
        return filter;
    }

    public void setFilter(Document filter) {
        this.filter = filter;
    }

    public Document getSort() {
        return sort;
    }

    public void setSort(Document sort) {
        this.sort = sort;
    }

    public Document getSkip() {
        return skip;
    }

    public void setSkip(Document skip) {
        this.skip = skip;
    }

    public Document getLimit() {
        return limit;
    }

    public void setLimit(Document limit) {
        this.limit = limit;
    }

    public Document isFirst() {
        return first;
    }

    public void setFirst(Document first) {
        this.first = first;
    }
}
