package org.restheart.graphql;

public class AssociationMapping extends Mapping{

    private String type;
    private String role;
    private String key;
    private String ref_field;

    public AssociationMapping(String name, String target_db, String target_collection, String type,
                              String role, String key, String ref_field) {
        super(name, target_db, target_collection);
        this.type = type;
        this.role = role;
        this.key = key;
        this.ref_field = ref_field;
    }



    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getRef_field() {
        return ref_field;
    }

    public void setRef_field(String ref_field) {
        this.ref_field = ref_field;
    }
}
