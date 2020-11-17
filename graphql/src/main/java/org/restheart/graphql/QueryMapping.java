package org.restheart.graphql;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.utils.JsonUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class QueryMapping extends Mapping {

    private boolean multiple;
    private BsonDocument filter;
    private BsonDocument sort;
    private BsonDocument skip;
    private BsonDocument limit;
    private BsonDocument first;

    public QueryMapping(String type, String name, String target_db, String target_collection, boolean multiple,
                        BsonDocument filter, BsonDocument sort, BsonDocument skip, BsonDocument limit, BsonDocument first) {
        super(type, name, target_db, target_collection);
        this.multiple = multiple;
        this.filter = filter;
        this.sort = sort;
        this.skip = skip;
        this.limit = limit;
        this.first = first;
    }

    public boolean isMultiple() {
        return multiple;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
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

    public BsonDocument getSkip() {
        return skip;
    }

    public void setSkip(BsonDocument skip) {
        this.skip = skip;
    }

    public BsonDocument getLimit() {
        return limit;
    }

    public void setLimit(BsonDocument limit) {
        this.limit = limit;
    }

    public BsonDocument getFirst() {
        return first;
    }

    public void setFirst(BsonDocument first) {
        this.first = first;
    }


    public BsonDocument interpolate(BsonDocument arguments, BsonDocument parentDocument) throws InvalidMetadataException,
            QueryVariableNotBoundException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {

        BsonDocument result = new BsonDocument();
        Class cls = this.getClass();
        //get all the declared fields of query object
        Field[] fields = cls.getDeclaredFields();
        for (Field field: fields) {
            //if the current field is a Document, we have to interpolate
            if (field.getType() == BsonDocument.class){
                //find the name of the argument to search in the arguments of the GraphQL query...
                String fieldName = field.getName();
                String fieldNameUpper = fieldName.substring(0,1).toUpperCase()+fieldName.substring(1);
                Object obj = (cls.getDeclaredMethod("get"+fieldNameUpper)).invoke(this);
                if (obj != null){
                    BsonDocument doc = (BsonDocument) obj;
                    BsonDocument fieldResult = searchMetaChars(new String[]{"$arg", "$fk"}, doc, parentDocument, arguments, fieldName);
                    result.put(fieldName, fieldResult.get(fieldName));
                }
            }
        }
        return result;
    } 

    private BsonDocument searchMetaChars(String[] metaChars, BsonDocument doc, BsonDocument parentDocument, BsonDocument arguments
            , String prevKey) throws QueryVariableNotBoundException {
        boolean find = false;
        //searching metacharacters..
        for (String key: metaChars){
            //if at least one is present, go out from the loop
            if(doc.containsKey(key)){
                find = true;
                break;
            }
        }
        //if no metacharacters are present...
        if (!find) {
            for (String key : doc.keySet()) {
                //if there are sub-documents...
                if (doc.get(key).getClass() == BsonDocument.class) {
                    //recall recursively the method...
                    return new BsonDocument(prevKey, searchMetaChars(metaChars, (BsonDocument) doc.get(key), parentDocument, arguments, key));
                }
            }
        } else {
            //if at least one metacharacter is present...
            for (String key: metaChars){
                if(doc.containsKey(key)){
                    String valueName = doc.get(key).asString().getValue();
                    BsonDocument value = new BsonDocument();
                    if(key == "$arg") {
                        if (arguments == null || arguments.get(valueName) == null) {
                            throw new QueryVariableNotBoundException("variable " + valueName + " not bound");
                        }
                        //create a document of type {"argName" : "value"}
                        value.put(prevKey, arguments.get(valueName));
                    }
                    else{
                        if(parentDocument == null || parentDocument.get(valueName) == null){
                            throw new QueryVariableNotBoundException("variable" + valueName + "not bound");
                        }
                        value.put(prevKey, parentDocument.get(valueName));
                    }
                    return value;
                }
            }
        }
        return null;
    }


    public static class Builder{
        String type;
        String name;
        String target_db;
        String target_collection;
        private boolean multiple;
        private BsonDocument filter;
        private BsonDocument sort;
        private BsonDocument skip;
        private BsonDocument limit;
        private BsonDocument first;

        public Builder(String type, String name, String db, String collection, boolean multiple){
            this.type = type;
            this.name = name;
            this.target_db = db;
            this.target_collection = collection;
            this.multiple = multiple;
        }

        public Builder newBuilder(String type, String db, String name, String collection, boolean multiple){
            return new Builder(type, db, name, collection, multiple);
        }

        public Builder filter(BsonDocument filter){
            this.filter = filter;
            return this;
        }

        public Builder sort(BsonDocument sort){
            this.sort = sort;
            return this;
        }

        public Builder skip(BsonDocument skip){
            this.skip = skip;
            return this;
        }

        public Builder limit(BsonDocument limit){
            this.limit = limit;
            return this;
        }

        public Builder first(BsonDocument first){
            this.first = first;
            return this;
        }

        public QueryMapping build(){
            return new QueryMapping(this.type, this.name, this.target_db, this.target_collection, this.multiple, this.filter,
                    this.sort, this.skip, this.limit, this.first);
        }


    }
}
