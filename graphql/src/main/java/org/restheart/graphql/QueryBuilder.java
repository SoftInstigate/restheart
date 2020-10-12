package org.restheart.graphql;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;

public final class QueryBuilder {

    private String db;
    private String name;
    private String collection;
    private BsonDocument filter;
    private BsonDocument sort = null;
    private Integer skip = null;
    private Integer limit = null;
    private boolean first = false;

    private QueryBuilder(String _db, String _name, String _collection){
        this.db = _db;
        this.name = _name;
        this.collection = _collection;
    }

    public static QueryBuilder newBuilder(String _db, String _name, String _collection){
        return new QueryBuilder(_db, _name, _collection);
    }

    public QueryBuilder filter(BsonDocument _filter){
        this.filter = _filter;
        return this;
    }

    public QueryBuilder sort(BsonDocument _sort){
        this.sort = _sort;
        return this;
    }

    public QueryBuilder skip(Integer _skip){
        this.skip = _skip;
        return this;
    }

    public QueryBuilder limit(Integer _limit){
        this.limit = _limit;
        return this;
    }

    public QueryBuilder first(boolean _first){
        this.first = _first;
        return this;
    }

    public Query build(){
        // here we can implement centralized values control
        return new Query(db, name, collection, filter, sort, skip, limit, first);
    }


}
