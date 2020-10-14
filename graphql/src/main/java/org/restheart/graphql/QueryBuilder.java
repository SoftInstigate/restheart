package org.restheart.graphql;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;

public final class QueryBuilder {

    private String db;
    private String name;
    private String collection;
    private Document filter;
    private Document sort;
    private Document skip;
    private Document limit;
    private Document first;

    private QueryBuilder(String _db, String _name, String _collection){
        this.db = _db;
        this.name = _name;
        this.collection = _collection;
    }

    public static QueryBuilder newBuilder(String _db, String _name, String _collection){
        return new QueryBuilder(_db, _name, _collection);
    }

    public QueryBuilder filter(Document _filter){
        this.filter = _filter;
        return this;
    }

    public QueryBuilder sort(Document _sort){
        this.sort = _sort;
        return this;
    }

    public QueryBuilder skip(Document _skip){
        this.skip = _skip;
        return this;
    }

    public QueryBuilder limit(Document _limit){
        this.limit = _limit;
        return this;
    }

    public QueryBuilder first(Document _first){
        this.first = _first;
        return this;
    }

    public Query build(){
        // here we can implement centralized values control
        return new Query(db, name, collection, filter, sort, skip, limit, first);
    }


}
