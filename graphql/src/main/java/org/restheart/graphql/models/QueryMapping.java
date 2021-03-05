/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2021 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.graphql.models;

import graphql.Assert;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.datafetchers.GQLQueryDataFetcher;
import org.restheart.utils.BsonUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.regex.Pattern;


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
                        BsonDocument arguments = BsonUtils.toBsonDocument(env.getArguments());
                        if (arguments == null || arguments.get(valueToInterpolate) == null) {
                            throw new QueryVariableNotBoundException("variable " + valueToInterpolate + " not bound");
                        }
                        return arguments.get(valueToInterpolate);
                    }
                    case "$fk":{
                        BsonDocument parentDocument = env.getSource();
                        return getForeignValue(parentDocument, valueToInterpolate);
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


    private BsonValue getForeignValue(BsonValue bsonValue, String path) throws QueryVariableNotBoundException {

        String[] splitPath = path.split(Pattern.quote("."));
        BsonValue current = bsonValue;

        for (int i = 0; i < splitPath.length; i++){
            if (current.isDocument() && current.asDocument().containsKey(splitPath[i])){
                current = current.asDocument().get(splitPath[i]);
            }
            else if (current.isArray()){
                try{
                    Integer index = Integer.parseInt(splitPath[i]);
                    current = current.asArray().get(index);
                }catch (NumberFormatException nfe){
                    BsonArray array = new BsonArray();
                    for (BsonValue value: current.asArray()){
                        String[] copy = Arrays.copyOfRange(splitPath, i, splitPath.length);
                        array.add(getForeignValue(value, String.join(".", copy)));
                        current = array;
                    }
                    break;
                }catch (IndexOutOfBoundsException ibe){
                    throw new QueryVariableNotBoundException("index out of bounds in " + splitPath[i-1] + " array");
                }

            }
            else throw new QueryVariableNotBoundException ("variable" + splitPath[i] + "not bound");
        }
        return current;
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
