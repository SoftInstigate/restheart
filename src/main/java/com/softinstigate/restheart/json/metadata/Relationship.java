/*
 * Copyright SoftInstigate srl. All Rights Reserved.
 *
 *
 * The copyright to the computer program(s) herein is the property of
 * SoftInstigate srl, Italy. The program(s) may be used and/or copied only
 * with the written permission of SoftInstigate srl or in accordance with the
 * terms and conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied. This copyright notice must not be removed.
 */
package com.softinstigate.restheart.json.metadata;

import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author uji
 */
public class Relationship
{
    public enum TYPE { ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY };
    public enum ROLE { OWNING, INVERSE };
    
    public static final String RELATIONSHIPS_ELEMENT_NAME = "rels";
    public static final String REL_ELEMENT_NAME = "rel";
    public static final String TYPE_ELEMENT_NAME = "type";
    public static final String ROLE_ELEMENT_NAME = "role";
    public static final String TARGET_DB_ELEMENT_NAME = "target-db";
    public static final String TARGET_COLLECTION_ELEMENT_NAME = "target-coll";
    public static final String REF_ELEMENT_NAME = "ref-field";
    
    
    private final String rel;
    private final TYPE type;
    private final ROLE role;
    private final String targetDb;
    private final String targetCollection;
    private final String referenceField;
    
    public Relationship(String rel, TYPE type, ROLE role, String targetDb, String targetCollection, String referenceField)
    {
        this.rel = rel;
        this.type = type;
        this.role = role;
        this.targetDb = targetDb;
        this.targetCollection = targetCollection;
        this.referenceField = referenceField;
    }
    
    public Relationship(String rel, String type, String role, String targetDb, String targetCollection, String referenceField) throws InvalidMetadataException
    {
        this.rel = rel;
        
        try
        {
            this.type = TYPE.valueOf(type);
        }
        catch(IllegalArgumentException iae)
        {
            throw new InvalidMetadataException("invalid type value: " + type + ". valid values are " + Arrays.toString(TYPE.values()), iae);
        }
        
        try
        {
            this.role = ROLE.valueOf(role);
        }
        catch(IllegalArgumentException iae)
        {
            throw new InvalidMetadataException("invalid role value " + role + ". valid values are " + Arrays.toString(ROLE.values()), iae);
        }
        
        this.targetDb = targetDb;
        this.targetCollection = targetCollection;
        this.referenceField = referenceField;
    }
    
    public static List<Relationship> getFromJson(DBObject collProps) throws InvalidMetadataException
    {
        if (collProps == null)
            return null;
        
        ArrayList<Relationship> ret = new ArrayList<>();
        
        Object _rels = collProps.get(RELATIONSHIPS_ELEMENT_NAME);
        
        if (_rels == null || ! (_rels instanceof BasicDBList))
            throw new InvalidMetadataException("element 'relationships' is not an array list." + _rels);
        
        BasicDBList rels = (BasicDBList) _rels;
        
        for (Object _rel: rels.toArray())
        {
            if (!(_rel instanceof DBObject))
                throw new InvalidMetadataException("element 'relationships' is not valid." + _rel);
                
            DBObject rel = (DBObject) _rel;
            ret.add(getRelFromJson(rel));
        }
        
        return ret;
    }
    
    private static Relationship getRelFromJson(DBObject content) throws InvalidMetadataException
    {
        Object _rel = content.get(REL_ELEMENT_NAME);
        Object _type = content.get(TYPE_ELEMENT_NAME);
        Object _role = content.get(ROLE_ELEMENT_NAME);
        Object _targetDb = content.get(TARGET_DB_ELEMENT_NAME);
        Object _targetCollection = content.get(TARGET_COLLECTION_ELEMENT_NAME);
        Object _referenceField = content.get(REF_ELEMENT_NAME);
        
        if (_rel == null || !(_rel instanceof String))
        {
            throw new InvalidMetadataException((_rel == null ? "missing " : "invalid ") + REL_ELEMENT_NAME + " element.");
        }
        
        if (_type == null || !(_type instanceof String))
        {
            throw new InvalidMetadataException((_type == null ? "missing " : "invalid ") + TYPE_ELEMENT_NAME + " element.");
        }
        
        if (_role == null || !(_role instanceof String))
        {
            throw new InvalidMetadataException((_role == null ? "missing " : "invalid ") + ROLE_ELEMENT_NAME + " element.");
        }
        
        if (_targetDb != null && !(_type instanceof String))
        {
            throw new InvalidMetadataException("invalid " + TARGET_DB_ELEMENT_NAME + " field.");
        }
        
        if (_targetCollection == null || !(_targetCollection instanceof String))
        {
            throw new InvalidMetadataException((_targetCollection == null ? "missing " : "invalid ") + TARGET_COLLECTION_ELEMENT_NAME + " element.");
        }
        
        if (_referenceField == null || !(_referenceField instanceof String))
        {
            throw new InvalidMetadataException((_referenceField == null ? "missing " : "invalid ") + REF_ELEMENT_NAME + " element.");
        }
        
        String rel = (String) _rel;
        String type = (String) _type;
        String role = (String) _role;
        String targetDb = (String) _targetDb;
        String targetCollection = (String) _targetCollection;
        String referenceField = (String) _referenceField;
        
        return new Relationship(rel, type, role, targetDb, targetCollection, referenceField);
    }
    
    public URI getRelationshipLink(String baseUrl, DBObject data) throws URISyntaxException
    {
        if (role == ROLE.OWNING)
        {
            Object _referenceValue = data.get(referenceField);
            
            
            
            return new URI("http://127.0.0.1" + "/" + _referenceValue);
        }
        
        return new URI("http://127.0.0.1");
    }
    
    /**
     * @return the rel
     */
    public String getRel()
    {
        return rel;
    }

    /**
     * @return the type
     */
    public TYPE getType()
    {
        return type;
    }

    /**
     * @return the role
     */
    public ROLE getRole()
    {
        return role;
    }

    /**
     * @return the targetDb
     */
    public String getTargetDb()
    {
        return targetDb;
    }

    /**
     * @return the targetCollection
     */
    public String getTargetCollection()
    {
        return targetCollection;
    }

    /**
     * @return the referenceField
     */
    public String getReferenceField()
    {
        return referenceField;
    }
}
