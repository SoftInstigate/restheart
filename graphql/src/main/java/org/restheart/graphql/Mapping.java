package org.restheart.graphql;

public abstract class Mapping {
    String type;
    String name;
    String target_db;
    String target_collection;


    public Mapping(String type, String name, String target_db, String target_collection) {
        this.type = type;
        this.name = name;
        this.target_db = target_db;
        this.target_collection = target_collection;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTarget_db() {
        return target_db;
    }

    public void setTarget_db(String target_db) {
        this.target_db = target_db;
    }

    public String getTarget_collection() {
        return target_collection;
    }

    public void setTarget_collection(String target_collection) {
        this.target_collection = target_collection;
    }
}
