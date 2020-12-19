package org.restheart.graphql.models;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

public class Mapping {

    private static final String[] OPERATORS = {"$arg", "$fk"};

    public String getMetaChar(int index) {
        return OPERATORS[index];
    }


    private String alias;
    private String db;
    private String collection;
    private BsonDocument find;
    private BsonDocument sort;
    private BsonDocument skip;
    private BsonDocument limit;
    private Boolean first;

    public Mapping(){}

    public Mapping(String alias, String db, String collection, BsonDocument find, BsonDocument sort,
                   BsonDocument skip, BsonDocument limit, Boolean first) {

        this.alias = alias;
        this.db = db;
        this.collection = collection;
        this.find = find;
        this.sort = sort;
        this.skip = skip;
        this.limit = limit;
        this.first = first;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getDb() {
        return db;
    }

    public void setDb(String db) {
        this.db = db;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
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

    public Boolean getFirst() {
        return first;
    }

    public void setFirst(Boolean first) {
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
                    searchOperators(doc, parentDocument, arguments, result,fieldName);
                }
            }
        }
        return result;
    }


    private void searchOperators(BsonDocument docToAnalyze,
                                 BsonDocument parentDocument,
                                 BsonDocument queryArguments,
                                 BsonDocument result,
                                 String prevKey) throws QueryVariableNotBoundException{
        boolean find = false;
        for (String meta_char: OPERATORS){
            if(docToAnalyze.containsKey(meta_char)){
                find = true;
                break;
            }
        }

        if(!find){
            for (String key: docToAnalyze.keySet()){
                if(docToAnalyze.get(key).getClass() == BsonDocument.class){
                    BsonDocument nextLevelResult = new BsonDocument();
                    searchOperators((BsonDocument) docToAnalyze.get(key), parentDocument, queryArguments, nextLevelResult, key);
                    result.put(prevKey, nextLevelResult);
                }
                else if(docToAnalyze.get(key).isArray()){
                    BsonArray arrayResult = new BsonArray();
                    for (BsonValue bsonValue: docToAnalyze.get(key).asArray()){
                        if (bsonValue.isDocument()){
                            BsonDocument res = new BsonDocument();
                            searchOperators(bsonValue.asDocument(), parentDocument, queryArguments, res, key);
                            arrayResult.add(res.get(key));
                        }
                        else arrayResult.add(bsonValue);
                    }
                    result.put(prevKey, new BsonDocument(key, arrayResult));
                }
                else {
                    result.put(prevKey, docToAnalyze);
                }
            }
        }
        else {
            for (String meta_char: OPERATORS){
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

        private String alias;
        private String db;
        private String collection;
        private BsonDocument find;
        private BsonDocument sort;
        private BsonDocument skip;
        private BsonDocument limit;
        private Boolean first;

        public Builder(){
        }

        public Builder newBuilder(){
            return new Builder();
        }

        public Builder db(String db){
            this.db = db;
            return this;
        }

        public Builder collection(String collection){
            this.collection = collection;
            return this;
        }

        public Builder alias(String alias){
            this.alias = alias;
            return this;
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

        public Builder first(Boolean first){
            this.first = first;
            return this;
        }

        public Mapping build(){
            return new Mapping(this.alias, this.db, this.collection, this.find,
                    this.sort, this.skip, this.limit, this.first);
        }


    }
}
