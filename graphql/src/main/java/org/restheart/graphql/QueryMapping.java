package org.restheart.graphql;

import org.bson.Document;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class QueryMapping extends Mapping {

    private boolean multiple;
    private Document filter;
    private Document sort;
    private Document skip;
    private Document limit;
    private Document first;

    public QueryMapping(String type, String name, String target_db, String target_collection, boolean multiple,
                        Document filter, Document sort, Document skip, Document limit, Document first) {
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

    public Document getFirst() {
        return first;
    }

    public void setFirst(Document first) {
        this.first = first;
    }


    public Document interpolate(Map<String, Object> arguments, Document parentDocument) throws InvalidMetadataException,
            QueryVariableNotBoundException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {

        Document result = new Document();
        Class cls = this.getClass();
        //get all the declared fields of query object
        Field[] fields = cls.getDeclaredFields();
        for (Field field: fields) {
            //if the current field is a Document, we have to interpolate
            if (field.getType() == Document.class){
                //find the name of the argument to search in the arguments of the GraphQL query...
                String fieldName = field.getName();
                String fieldNameUpper = fieldName.substring(0,1).toUpperCase()+fieldName.substring(1);
                Object obj = (cls.getDeclaredMethod("get"+fieldNameUpper)).invoke(this);
                if (obj != null){
                    Document doc = (Document) obj;
                    Document fieldResult = searchMetaChars(new String[]{"$arg", "$fk"}, doc, parentDocument, arguments, fieldName);
                    result.put(fieldName, fieldResult.get(fieldName));
                }
            }
        }
        return result;
    } 

    private Document searchMetaChars(String[] metaChars, Document doc, Document parentDocument, Map<String, Object> arguments
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
                if (doc.get(key).getClass() == Document.class) {
                    //recall recursively the method...
                    return new Document(prevKey, searchMetaChars(metaChars, (Document) doc.get(key), parentDocument, arguments, key));
                }
            }
        } else {
            //if at least one metacharacter is present...
            for (String key: metaChars){
                if(doc.containsKey(key)){
                    String valueName = (String) doc.get(key);
                    Document value = new Document();
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
        private Document filter;
        private Document sort;
        private Document skip;
        private Document limit;
        private Document first;

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

        public Builder filter(Document filter){
            this.filter = filter;
            return this;
        }

        public Builder sort(Document sort){
            this.sort = sort;
            return this;
        }

        public Builder skip(Document skip){
            this.skip = skip;
            return this;
        }

        public Builder limit(Document limit){
            this.limit = limit;
            return this;
        }

        public Builder first(Document first){
            this.first = first;
            return this;
        }

        public QueryMapping build(){
            return new QueryMapping(this.type, this.name, this.target_db, this.target_collection, this.multiple, this.filter,
                    this.sort, this.skip, this.limit, this.first);
        }


    }
}
