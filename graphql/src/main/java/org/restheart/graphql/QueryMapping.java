package org.restheart.graphql;
import org.bson.BsonDocument;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class QueryMapping extends Mapping {

    private static final String[] META_CHARS = {"$arg", "$fk"};

    public String getMetaChar(int index) {
        return META_CHARS[index];
    }

    private BsonDocument find;
    private BsonDocument sort;
    private BsonDocument skip;
    private BsonDocument limit;
    private BsonDocument first;

    public QueryMapping(String type, String name, String target_db, String target_collection,
                        BsonDocument find, BsonDocument sort, BsonDocument skip, BsonDocument limit, BsonDocument first) {
        super(type, name, target_db, target_collection);
        this.find = find;
        this.sort = sort;
        this.skip = skip;
        this.limit = limit;
        this.first = first;
    }



    public BsonDocument getFind() {
        return find;
    }

    public void setFind(BsonDocument find) {
        this.find = find;
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
                    searchMetaChars(doc, parentDocument, arguments, result,fieldName);
                }
            }
        }
        return result;
    }


    private void searchMetaChars(BsonDocument docToAnalyze,
                                 BsonDocument parentDocument,
                                 BsonDocument queryArguments,
                                 BsonDocument result,
                                 String prevKey) throws QueryVariableNotBoundException{
        boolean find = false;
        for (String meta_char: META_CHARS){
            if(docToAnalyze.containsKey(meta_char)){
                find = true;
                break;
            }
        }

        if(!find){
            BsonDocument nextLevelResult = new BsonDocument();
            for (String key: docToAnalyze.keySet()){
                if(docToAnalyze.get(key).getClass() == BsonDocument.class){
                    searchMetaChars((BsonDocument) docToAnalyze.get(key), parentDocument, queryArguments, nextLevelResult, key);
                }
            }
            result.put(prevKey, nextLevelResult);
        }
        else {
            for (String meta_char: META_CHARS){
                if(docToAnalyze.containsKey(meta_char)){
                    String valueName = docToAnalyze.get(meta_char).asString().getValue();
                    switch (meta_char){
                        case "$arg": {
                            if (queryArguments == null || queryArguments.get(valueName) == null) {
                                throw new QueryVariableNotBoundException("variable " + valueName + " not bound");
                            }
                            result.put(prevKey, queryArguments.get(valueName));
                            break;
                        }
                        case "$fk":{
                            if(parentDocument == null || parentDocument.get(valueName) == null){
                                throw new QueryVariableNotBoundException("variable" + valueName + "not bound");
                            }
                            result.put(prevKey, parentDocument.get(valueName));
                        }
                    }
                }
            }
        }
    }


    public static class Builder{
        String type;
        String name;
        String target_db;
        String target_collection;
        private BsonDocument find;
        private BsonDocument sort;
        private BsonDocument skip;
        private BsonDocument limit;
        private BsonDocument first;

        public Builder(String type, String name, String db, String collection, boolean multiple){
            this.type = type;
            this.name = name;
            this.target_db = db;
            this.target_collection = collection;
        }

        public Builder newBuilder(String type, String db, String name, String collection, boolean multiple){
            return new Builder(type, db, name, collection, multiple);
        }

        public Builder find(BsonDocument find){
            this.find = find;
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
            return new QueryMapping(this.type, this.name, this.target_db, this.target_collection, this.find,
                    this.sort, this.skip, this.limit, this.first);
        }


    }
}
