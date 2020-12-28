package org.restheart.graphql.models;

import graphql.Assert;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.GQLQueryDataFetcher;
import org.restheart.utils.JsonUtils;

import java.lang.reflect.Field;


public class QueryMapping extends FieldMapping{

    private static final String[] OPERATORS = {"$arg", "$fk"};


    private String db;
    private String collection;
    private BsonDocument find;
    private BsonDocument sort;
    private BsonDocument limit;
    private BsonDocument skip;


    private QueryMapping(String fieldName, String db, String collection, BsonDocument find, BsonDocument sort, BsonDocument limit, BsonDocument skip) {
        super(fieldName);
        this.db = db;
        this.collection = collection;
        this.find = find;
        this.sort = sort;
        this.limit = limit;
        this.skip = skip;
    }

    public static Builder newBuilder(){
        return new Builder();
    }

    @Override
    public DataFetcher<BsonValue> getDataFetcher() {
        return new GQLQueryDataFetcher(this);
    }

    public String getDb() {
        return db;
    }

    public String getCollection() {
        return collection;
    }

    public BsonDocument getFind() {
        return find;
    }

    public BsonDocument getSort() {
        return sort;
    }

    public BsonDocument getLimit() {
        return limit;
    }

    public BsonDocument getSkip() {
        return skip;
    }


    public BsonDocument interpolateArgs(DataFetchingEnvironment env) throws IllegalAccessException, QueryVariableNotBoundException {

        BsonDocument result = new BsonDocument();

        Field[] fields = (QueryMapping.class).getDeclaredFields();
        for (Field field: fields){
            if(field.getType() == BsonDocument.class){
                BsonDocument fieldValue = (BsonDocument) field.get(this);
                if (fieldValue != null){
                    result.put(field.getName(), searchOperators(fieldValue, env));
                }
            }
        }
        return result;
    }

    private BsonValue searchOperators(BsonDocument docToAnalyze, DataFetchingEnvironment env) throws QueryVariableNotBoundException {

        for (String operator: OPERATORS){
            if (docToAnalyze.containsKey(operator)){
                String valueToInterpolate = docToAnalyze.getString(operator).getValue();

                switch (operator){
                    case "$arg": {
                        BsonDocument arguments = JsonUtils.toBsonDocument(env.getArguments());
                        if (arguments == null || arguments.get(valueToInterpolate) == null) {
                            throw new QueryVariableNotBoundException("variable " + valueToInterpolate + " not bound");
                        }
                        return arguments.get(valueToInterpolate);
                    }
                    case "$fk":{
                        BsonDocument parentDocument = env.getSource();
                        if(parentDocument == null || parentDocument.get(valueToInterpolate) == null){
                            throw new QueryVariableNotBoundException("variable" + valueToInterpolate + "not bound");
                        }
                        return parentDocument.get(valueToInterpolate);
                    }
                    default:
                        return Assert.assertShouldNeverHappen();
                }
            }
        }

        BsonDocument result = new BsonDocument();

        for (String key: docToAnalyze.keySet()){
            if (docToAnalyze.get(key).isDocument()){
                BsonValue value = searchOperators(docToAnalyze.get(key).asDocument(), env);
                result.put(key, value);
            }
            else if (docToAnalyze.get(key).isArray()){
                BsonArray array = new BsonArray();
                for (BsonValue bsonValue: docToAnalyze.get(key).asArray()){
                    if(bsonValue.isDocument()){
                        BsonValue value = searchOperators(bsonValue.asDocument(), env);
                        array.add(value);
                    }
                    else array.add(bsonValue);
                }
                result.put(key, array);
            }
            else result = docToAnalyze.clone();
        }

        return result;
    }






    public static class Builder{

        private String fieldName;
        private String db;
        private String collection;
        private BsonDocument find;
        private BsonDocument sort;
        private BsonDocument limit;
        private BsonDocument skip;

        private Builder(){}

        public Builder fieldName(String fieldName){
            this.fieldName = fieldName;
            return this;
        }

        public Builder db(String db){
            this.db = db;
            return this;
        }

        public Builder collection(String collection){
            this.collection = collection;
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

        public Builder limit(BsonDocument limit){
            this.limit = limit;
            return this;
        }

        public Builder skip(BsonDocument skip){
            this.skip = skip;
            return this;
        }

        public QueryMapping build(){

            if(this.db == null){
                throwIllegalException("db");
            }

            if(this.collection == null){
                throwIllegalException("collection");
            }


            return new QueryMapping(this.fieldName, this.db, this.collection, this.find, this.sort, this.limit, this.skip);
        }

        private static void throwIllegalException(String varName){

            throw  new IllegalStateException(
                varName + "could not be null!"
            );

        }

    }

}
