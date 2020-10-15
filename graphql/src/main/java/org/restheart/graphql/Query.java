package org.restheart.graphql;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.restheart.exchange.InvalidMetadataException;
import org.restheart.exchange.QueryVariableNotBoundException;

import javax.print.Doc;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;


public class Query {


    private String db;
    private String name;
    private String collection;
    private boolean multiple;
    private Document filter;
    private Document sort;
    private Document skip;
    private Document limit;
    private Document first;

    public Query(String db, String name, String collection, boolean multiple, Document filter, Document sort, Document skip, Document limit, Document first) {
        this.db = db;
        this.name = name;
        this.collection = collection;
        this.multiple = multiple;
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


    public Map<String, Document> interpolate(Map<String, Object> arguments) throws InvalidMetadataException, QueryVariableNotBoundException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {

        Map<String, Document> result = new HashMap<>();
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
                    searchArg(doc, arguments, result, fieldName);
                }
            }
        }
        return result;
    }

    private void searchArg(Document doc, Map<String, Object> arguments, Map<String, Document> result, String fieldName) throws QueryVariableNotBoundException {

        if (!doc.containsKey("$arg")) {
            for (String key : doc.keySet()) {
                if (doc.get(key).getClass() == Document.class) {
                    searchArg((Document) doc.get(key), arguments, result, fieldName);
                }
            }
        } else {
            String argName = (String) doc.get("$arg");
            //... if arguments don't contain such variable name, launch an exception
            if (arguments == null || arguments.get(argName) == null) {
                throw new QueryVariableNotBoundException("variable " + argName + " not bound");
            }
            Document value = new Document();
            //create a document of type {"argName" : "value"}
            value.put(argName, arguments.get(argName));
            //put the document inside a Map
            result.put(fieldName, value);
        }
    }
}

