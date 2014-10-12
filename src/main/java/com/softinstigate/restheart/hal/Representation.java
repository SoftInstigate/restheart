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
package com.softinstigate.restheart.hal;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.BSONObject;

/**
 *
 * @author uji
 */
public class Representation
{
    public static final String HAL_JSON_MEDIA_TYPE = "application/hal+json";
    
    private final BasicDBObject dbObject;
    
    public Representation(String href)
    {
        dbObject = new BasicDBObject();
        
        Link self = new Link("self", href);
        
        dbObject.put("_links", self.getDBObject());
    }
        
    BasicDBObject getDBObject()
    {
        return dbObject;
    }
    
    public void addLink(Link link)
    {
        if (dbObject.get("_links") == null)
            dbObject.put("_links", new BasicDBObject());
        
        BasicDBObject _links = (BasicDBObject) dbObject.get("_links");
        
        _links.putAll((BSONObject)((Link)link).getDBObject());
    }
    
    public void addProperty(String key, Object value)
    {
        dbObject.append(key, value);
    }
    
    public void addProperties(DBObject props)
    {
        if (props == null)
            return;
        
        dbObject.putAll(props);
    }
    
    public void addRepresentation(String rel, Representation rep)
    {
        if (dbObject.get("_embedded") == null)
            dbObject.put("_embedded", new BasicDBObject());
        
        BasicDBObject _embedded = (BasicDBObject) dbObject.get("_embedded");
        
        if (_embedded.get(rel) == null)
            _embedded.put(rel, new BasicDBObject());
        
        BasicDBObject _rel = (BasicDBObject) _embedded.get(rel);
        
        _rel.putAll((BSONObject)((Representation)rep).getDBObject());
    }
    
    @Override
    public String toString()
    {
        return dbObject.toString();
    }
}
